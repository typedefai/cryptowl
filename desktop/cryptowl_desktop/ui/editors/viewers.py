from __future__ import annotations

"""Metadata viewers (vault.meta / config / device_secret) + Files browser."""

import datetime as dt
import json
import os

from PyQt6.QtGui import QPixmap
from PyQt6.QtCore import Qt
from PyQt6.QtWidgets import (QCheckBox, QDialog, QFileDialog, QHBoxLayout,
                             QLabel, QMessageBox, QPushButton, QScrollArea,
                             QTableWidget, QTableWidgetItem, QTextBrowser,
                             QVBoxLayout, QWidget)

from ...vault import files as filesv
from ...vault.values import fmt_size


def _mtime(ms) -> str:
    try:
        return dt.datetime.fromtimestamp(int(ms) / 1000).strftime("%Y-%m-%d %H:%M")
    except (OverflowError, OSError, ValueError, TypeError):
        return str(ms)


class MetaViewer(QWidget):
    """vault.meta / config.json+sig / device_secret — developer viewers."""

    def __init__(self, vault, key: str, parent=None):
        super().__init__(parent)
        self.vault = vault
        self.key = key
        self._reveal = False

        self.status = QLabel("")
        self.status.setObjectName("SectionHint")
        self.status.setWordWrap(True)
        self.view = QTextBrowser()
        self.view.setObjectName("MonoView")

        layout = QVBoxLayout(self)
        layout.setContentsMargins(6, 6, 6, 6)
        layout.addWidget(self.status)
        if key == "device_secret":
            toggle = QCheckBox("Reveal raw hex (dev only)")
            toggle.toggled.connect(self._set_reveal)
            layout.addWidget(toggle)
        layout.addWidget(self.view, 1)
        self.refresh()

    def _set_reveal(self, checked: bool):
        self._reveal = checked
        self.refresh()

    def refresh(self):
        vault = self.vault
        if self.key == "vault.meta":
            self.status.setText(
                "vault.meta — MAC verified at open (HMAC-SHA256, MAC key = SMK[32:64]);"
                " binary fields are Crockford Base32.")
            self.view.setPlainText(json.dumps(vault.meta, indent=2, sort_keys=True))
        elif self.key == "config":
            config_path = os.path.join(vault.path, "config.json")
            sig_path = os.path.join(vault.path, "config.sig")
            config_bytes = open(config_path, "rb").read()
            sig = open(sig_path, encoding="ascii").read().strip() if os.path.isfile(sig_path) else "?"
            self.status.setText(
                f"config.json — {len(config_bytes)} B · config.sig = {sig} (verified at open)")
            self.view.setPlainText(config_bytes.decode("utf-8", "replace"))
        else:  # device_secret
            path = os.path.join(vault.path, "device_secret")
            if not os.path.isfile(path):
                self.status.setText(
                    "device_secret — absent: the vault is bound to an Android Keystore "
                    "Device Secret and cannot be opened on the desktop anymore.")
                self.view.setPlainText("")
                return
            mode = oct(os.stat(path).st_mode & 0o777)
            self.status.setText(
                f"device_secret — 32 B · mode {mode} · fingerprint "
                f"{vault.device_secret_fingerprint()}. Raw hex hidden by default.")
            self.view.setPlainText(
                open(path, "r", encoding="utf-8").read() if self._reveal
                else "<hidden — check the box above to reveal>")

    def properties(self):
        return [("Editor", f"Metadata: {self.key}"), ("Vault", self.vault.vault_id)]


class FilesEditor(QWidget):
    """attachments/ or thumbnails/ as a file grid with CWO1 info + preview/export."""

    def __init__(self, vault, subdir: str, parent=None):
        super().__init__(parent)
        self.vault = vault
        self.subdir = subdir
        self._rows_cache = []

        self.table = QTableWidget(0, 5)
        self.table.setHorizontalHeaderLabels(
            ["File", "Size", "CWO1", "AAD (row id)", "Modified"])
        self.table.horizontalHeader().setStretchLastSection(True)
        self.table.verticalHeader().setVisible(False)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.doubleClicked.connect(self._preview_selected)

        preview_btn = QPushButton("Preview image")
        export_btn = QPushButton("Export decrypted…")
        refresh_btn = QPushButton("Refresh")
        preview_btn.clicked.connect(self._preview_selected)
        export_btn.clicked.connect(self._export_selected)
        refresh_btn.clicked.connect(self.refresh)

        toolbar = QHBoxLayout()
        for button in (preview_btn, export_btn, refresh_btn):
            toolbar.addWidget(button)
        toolbar.addStretch(1)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(6, 6, 6, 6)
        layout.addLayout(toolbar)
        layout.addWidget(self.table, 1)
        self.refresh()

    def refresh(self):
        self._rows_cache = filesv.list_files(self.vault, self.subdir)
        self.table.setRowCount(len(self._rows_cache))
        for i, entry in enumerate(self._rows_cache):
            cwo = entry["cwo1"]
            if cwo:
                kind = (f"chunked x{cwo['chunk_count']}" if cwo["chunked"]
                        else f"whole ({cwo['iv_or_nonce'][:8]}…)")
                version = f"v{cwo['version']}"
            else:
                kind, version = "not CWO1", "?"
            for col, text in enumerate([
                    entry["name"], fmt_size(entry["size"]),
                    f"{version} {kind}", entry["aad"], _mtime(entry["mtime"])]):
                item = QTableWidgetItem(str(text))
                item.setToolTip(entry["path"])
                self.table.setItem(i, col, item)

    def _selected(self):
        row = self.table.currentRow()
        if 0 <= row < len(self._rows_cache):
            return self._rows_cache[row]
        return None

    def _preview_selected(self, *_):
        entry = self._selected()
        if entry is None:
            return
        if not entry["cwo1"] or entry["cwo1"]["chunked"]:
            QMessageBox.information(
                self, "Preview",
                "Only whole-file CWO1 images can be previewed here (videos are "
                "chunked; use Export).")
            return
        plain = filesv.decrypt_bytes(self.vault, entry["aad"], entry["path"])
        if plain is None:
            QMessageBox.warning(self, "Preview", "Decryption failed.")
            return
        pixmap = QPixmap()
        if not pixmap.loadFromData(plain):
            QMessageBox.warning(self, "Preview", "Not an image (use the value panel's Hex).")
            return
        dialog = QDialog(self)
        dialog.setWindowTitle(entry["name"])
        label = QLabel()
        label.setPixmap(pixmap.scaled(900, 640, Qt.AspectRatioMode.KeepAspectRatio,
                                      Qt.TransformationMode.SmoothTransformation))
        scroll = QScrollArea()
        scroll.setWidget(label)
        layout = QVBoxLayout(dialog)
        layout.addWidget(scroll)
        dialog.resize(920, 660)
        dialog.exec()

    def _export_selected(self, *_):
        entry = self._selected()
        if entry is None:
            return
        target, _ = QFileDialog.getSaveFileName(
            self, "Export decrypted file",
            entry["name"][:-4] if entry["name"].endswith(".cwo") else entry["name"])
        if not target:
            return
        try:
            size = filesv.decrypt_to(self.vault, entry["aad"], entry["path"], target)
        except Exception as exc:  # noqa: BLE001
            QMessageBox.critical(self, "Export", str(exc))
            return
        QMessageBox.information(self, "Export", f"Exported {size} B -> {target}")

    def properties(self):
        total = sum(r["size"] for r in self._rows_cache)
        return [("Editor", f"Files: {self.subdir}"),
                ("Files", str(len(self._rows_cache))),
                ("Total", fmt_size(total))]
