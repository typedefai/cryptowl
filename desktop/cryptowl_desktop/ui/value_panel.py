from __future__ import annotations

"""Value panel (bottom dock, DBeaver-style): inspect one cell.

Formats: Auto / Text / Hex / Base32 / JSON / Image. A CWO1 blob shows its
header summary and, when the row is a t_file row, a decrypt-with-FEK preview.
"""

from PyQt6.QtCore import Qt
from PyQt6.QtGui import QFont, QPixmap
from PyQt6.QtWidgets import (QHBoxLayout, QLabel, QMessageBox, QPushButton,
                             QScrollArea, QStackedWidget, QTextBrowser,
                             QVBoxLayout, QWidget)

from ..vault import files as filesv
from ..vault import values as V

AUTO, TEXT, HEXDUMP, BASE32, JSON, IMAGE = range(6)

_MONO = QFont("Menlo")
_MONO.setStyleHint(QFont.StyleHint.Monospace)
_MONO_B = QFont("Menlo")
_MONO_B.setStyleHint(QFont.StyleHint.Monospace)
_MONO_B.setPointSize(10)


class ValuePanel(QWidget):

    def __init__(self, vault, parent=None):
        super().__init__(parent)
        self.vault = vault
        self._cell = None  # (table, column, row dict)

        self.header = QLabel("Select a cell in a data grid to inspect it.")
        self.header.setObjectName("SectionHint")
        self.header.setWordWrap(True)

        self.buttons = {}
        bar = QHBoxLayout()
        for fid, label in ((AUTO, "Auto"), (TEXT, "Text"), (HEXDUMP, "Hex"),
                           (BASE32, "Base32"), (JSON, "JSON"), (IMAGE, "Image")):
            button = QPushButton(label)
            button.setCheckable(True)
            button.clicked.connect(lambda _=False, f=fid: self.render(f))
            self.buttons[fid] = button
            bar.addWidget(button)
        self.decrypt_btn = QPushButton("Decrypt with FEK")
        self.decrypt_btn.setToolTip("C-tier attachments: decrypt the file (AAD = row id)")
        self.decrypt_btn.clicked.connect(self._decrypt_preview)
        self.export_btn = QPushButton("Export decrypted…")
        self.export_btn.clicked.connect(self._export)
        bar.addStretch(1)
        bar.addWidget(self.decrypt_btn)
        bar.addWidget(self.export_btn)

        self.view = QTextBrowser()
        self.view.setFont(_MONO_B)
        self.view.setOpenExternalLinks(False)
        self.image = QLabel()
        self.image.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.image_scroll = QScrollArea()
        self.image_scroll.setWidget(self.image)
        self.image_scroll.setWidgetResizable(True)
        self.stack = QStackedWidget()
        self.stack.addWidget(self.view)
        self.stack.addWidget(self.image_scroll)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(4, 2, 4, 2)
        layout.addLayout(bar)
        layout.addWidget(self.header)
        layout.addWidget(self.stack, 1)
        self.set_cell(None)

    # ------------------------------------------------------------------ api

    def set_cell(self, cell):
        """cell = (table, column, row dict) or None."""
        self._cell = cell
        for fid, button in self.buttons.items():
            button.setChecked(fid == AUTO)
        if cell is None:
            self.header.setText("Select a cell in a data grid to inspect it.")
            self.view.setPlainText("")
            self.stack.setCurrentWidget(self.view)
            return
        table, column, row = cell
        self.header.setText(f"{table}.{column}   ·   {len(str(row))}")
        self.decrypt_btn.setVisible(table == "t_file")
        self.export_btn.setVisible(table == "t_file")
        self.render(AUTO)

    # ---------------------------------------------------------------- render

    def _raw(self):
        if not self._cell:
            return None
        _table, column, row = self._cell
        return row.get(column)

    def _blob(self, value):
        if isinstance(value, (bytes, bytearray, memoryview)):
            return bytes(value)
        return None

    def render(self, fmt):
        if not self._cell:
            return
        table, column, row = self._cell
        raw = self._raw()
        text = ""
        show_image = False
        blob = self._blob(raw)

        if fmt == IMAGE:
            show_image = blob is not None and self._set_image(blob)
            if not show_image:
                text = "(not an image)"
        elif fmt == HEXDUMP:
            text = V.hex_dump(blob) if blob is not None else f"(not a blob: {raw!r})"
        elif fmt == BASE32:
            text = V.base32(blob) if blob is not None else f"(not a blob: {raw!r})"
        elif fmt == JSON:
            pretty = V.pretty_json_if(str(raw)) if blob is None else None
            text = pretty or "(not JSON)"
        elif fmt == TEXT:
            text = V.describe(raw)
        else:  # AUTO
            if blob is not None:
                summary = V.cwo1_summary(blob)
                if summary:
                    kind = "chunked" if summary["chunked"] else "whole-file"
                    chunks = f", {summary['chunk_count']} chunks" if summary["chunked"] else ""
                    text = (f"CWO1 v{summary['version']} {kind}{chunks}\n"
                            f"iv/nonce = {summary['iv_or_nonce']}\n"
                            f"total = {len(blob)} B\n\n(see Hex for the bytes)")
                else:
                    text = f"<blob {len(blob)} B>\n\n{V.hex_dump(blob[:512])}"
            else:
                pretty = V.pretty_json_if(str(raw))
                text = pretty if pretty else ("" if raw is None else str(raw))
            show_image = blob is not None and self._set_image(blob)

        self.view.setPlainText(text)
        self.stack.setCurrentWidget(self.image_scroll if show_image else self.view)
        if not show_image:
            self.image.clear()

    def _set_image(self, blob: bytes) -> bool:
        pixmap = QPixmap()
        if not pixmap.loadFromData(blob):
            return False
        scaled = pixmap.scaled(
            self.image_scroll.viewport().size(),
            Qt.AspectRatioMode.KeepAspectRatio,
            Qt.TransformationMode.SmoothTransformation)
        self.image.setPixmap(scaled)
        return True

    # ------------------------------------------------------- decrypt / export

    def _attachment(self):
        if not self._cell:
            return None
        table, _column, row = self._cell
        if table != "t_file":
            return None
        file_id = row.get("id")
        storage = row.get("storage_name")
        if not file_id or not storage:
            return None
        path = filesv.subdir_path(self.vault, "attachments") + "/" + storage
        if not filesv.cwo1_info(path):
            return None
        return file_id, path

    def _decrypt_preview(self):
        attachment = self._attachment()
        if attachment is None:
            return
        file_id, path = attachment
        plain = filesv.decrypt_bytes(self.vault, file_id, path)
        if plain is None:
            QMessageBox.warning(self, "Decrypt",
                                "Decryption failed (not a whole-file CWO1 blob, or a "
                                "non-C tier attachment).")
            return
        if plain[:4] == b"CWO1":
            QMessageBox.information(self, "Decrypt",
                                    "This file is chunked; use Export decrypted… for it.")
            return
        self.buttons[IMAGE].setChecked(True)
        self.render(IMAGE)
        self.header.setText(f"{self._cell[0]}.{self._cell[1]}  ·  decrypted "
                            f"{len(plain)} B")

    def _export(self):
        attachment = self._attachment()
        if attachment is None:
            return
        from PyQt6.QtWidgets import QFileDialog
        file_id, path = attachment
        target, _ = QFileDialog.getSaveFileName(self, "Export decrypted file", file_id)
        if not target:
            return
        try:
            size = filesv.decrypt_to(self.vault, file_id, path, target)
        except Exception as exc:  # noqa: BLE001
            QMessageBox.critical(self, "Export", str(exc))
            return
        self.header.setText(f"exported {size} B -> {target}")
