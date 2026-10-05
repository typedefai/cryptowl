from __future__ import annotations

"""Minimal PySide6 window — login-style unlock for a single vault, with a
switch menu for recent vaults or another folder. No vault contents yet.
"""

import logging
import os

from PySide6.QtCore import QPoint, Qt, Signal
from PySide6.QtGui import QAction, QFontMetrics
from PySide6.QtWidgets import (QFileDialog, QFormLayout, QHBoxLayout, QLabel,
                               QLineEdit, QMainWindow, QMenu, QMessageBox,
                               QPushButton, QStackedWidget, QStyle,
                               QVBoxLayout, QWidget)

from .. import __version__
from ..recent import RecentVaults
from ..vault import Vault, VaultError, inspect_folder

logger = logging.getLogger("devkit.ui")

WARN_COLOR = "#9a6700"
ERROR_COLOR = "#cf222e"
HINT_COLOR = "#57606a"
WINDOW_WIDTH = 460
PATH_ELIDE_WIDTH = 380


class UnlockPage(QWidget):
    """Login-style panel: current vault, master password, switch-vault menu."""

    unlock_requested = Signal(str, str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._entries = []

        icon = QLabel()
        icon.setPixmap(self.style()
                       .standardIcon(QStyle.StandardPixmap.SP_DirIcon)
                       .pixmap(64, 64))
        icon.setAlignment(Qt.AlignmentFlag.AlignCenter)

        self.name_label = QLabel("No vault selected")
        self.name_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.name_label.setStyleSheet("font-size: 17px; font-weight: 600;")

        self.path_label = QLabel()
        self.path_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.path_label.setStyleSheet(f"color: {HINT_COLOR};")

        self.warning_label = QLabel()
        self.warning_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.warning_label.setWordWrap(True)

        self.password_edit = QLineEdit()
        self.password_edit.setEchoMode(QLineEdit.EchoMode.Password)
        self.password_edit.setPlaceholderText("Master password")
        self.password_edit.setMinimumWidth(220)
        self.password_edit.returnPressed.connect(self._request_unlock)
        self.unlock_btn = QPushButton("Unlock")
        self.unlock_btn.setDefault(True)
        self.unlock_btn.clicked.connect(self._request_unlock)
        password_row = QHBoxLayout()
        password_row.addStretch(1)
        password_row.addWidget(self.password_edit)
        password_row.addWidget(self.unlock_btn)
        password_row.addStretch(1)

        self.switch_link = QLabel('<a href="switch">Switch vault…</a>')
        self.switch_link.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.switch_link.setTextInteractionFlags(
            Qt.TextInteractionFlag.LinksAccessibleByMouse)
        self.switch_link.linkActivated.connect(lambda _: self._show_switch_menu())

        layout = QVBoxLayout(self)
        layout.addStretch(3)
        layout.addWidget(icon)
        layout.addWidget(self.name_label)
        layout.addWidget(self.path_label)
        layout.addSpacing(4)
        layout.addWidget(self.warning_label)
        layout.addSpacing(14)
        layout.addLayout(password_row)
        layout.addSpacing(10)
        layout.addWidget(self.switch_link)
        layout.addStretch(4)
        layout.setContentsMargins(28, 20, 28, 20)

        self.path_edit = QLineEdit(self)
        self.path_edit.hide()
        self.path_edit.textChanged.connect(self.refresh_detection)
        self.refresh_detection()

    # -- state --------------------------------------------------------------

    def set_recents(self, entries) -> None:
        self._entries = list(entries)
        self.refresh_detection()

    def set_path(self, path: str, focus: bool = False) -> None:
        self.path_edit.setText(path)
        if focus:
            self.password_edit.setFocus()

    def current_path(self) -> str:
        return self.path_edit.text().strip()

    def refresh_detection(self) -> None:
        folder = self.path_edit.text().strip()
        self.password_edit.clear()
        if not folder:
            self.name_label.setText("No vault selected")
            self.path_label.clear()
            self.path_label.setToolTip("")
            self.warning_label.clear()
            self.switch_link.setText('<a href="switch">Open vault…</a>')
            self.password_edit.setEnabled(False)
            self.unlock_btn.setEnabled(False)
            return

        status = inspect_folder(folder)
        self.name_label.setText(
            status.vault_id or os.path.basename(os.path.normpath(status.path)))
        self.path_label.setText(QFontMetrics(self.path_label.font()).elidedText(
            status.path, Qt.TextElideMode.ElideMiddle, PATH_ELIDE_WIDTH))
        self.path_label.setToolTip(status.path)

        if status.ready:
            self.warning_label.clear()
        else:
            color = WARN_COLOR if status.android_bound else ERROR_COLOR
            self.warning_label.setStyleSheet(f"color: {color};")
            self.warning_label.setText(status.summary)

        self.switch_link.setText('<a href="switch">Switch vault…</a>')
        self.password_edit.setEnabled(status.ready)
        self.unlock_btn.setEnabled(status.ready)

    # -- actions ------------------------------------------------------------

    def _browse(self) -> None:
        start = self.current_path() or os.path.expanduser("~")
        chosen = QFileDialog.getExistingDirectory(self, "Select vault folder",
                                                  start)
        if chosen:
            self.set_path(chosen, focus=True)

    def _show_switch_menu(self) -> None:
        if not self._entries:
            self._browse()
            return
        current = os.path.abspath(os.path.expanduser(self.current_path()))
        menu = QMenu(self)
        for entry in self._entries:
            action = menu.addAction(entry["name"])
            action.setToolTip(entry["path"])
            action.setCheckable(True)
            action.setChecked(
                os.path.abspath(os.path.expanduser(entry["path"])) == current)
            action.triggered.connect(
                lambda _checked=False, p=entry["path"]: self.set_path(p, focus=True))
        menu.addSeparator()
        menu.addAction("Choose folder…", self._browse)
        menu.exec(self.switch_link.mapToGlobal(
            QPoint(0, self.switch_link.height())))

    def _request_unlock(self) -> None:
        path = self.current_path()
        password = self.password_edit.text()
        if not path:
            QMessageBox.warning(self, "Unlock", "Choose a vault folder first.")
            return
        if not password:
            QMessageBox.warning(self, "Unlock", "Enter the master password.")
            return
        self.unlock_requested.emit(path, password)


class VaultPage(QWidget):
    """Post-unlock summary — proof the DB opened; contents come in later steps."""

    lock_requested = Signal()

    def __init__(self, parent=None):
        super().__init__(parent)

        title = QLabel("Vault unlocked")
        title.setStyleSheet("font-size: 22px; font-weight: 600;")

        self.form = QFormLayout()
        self._labels = {}
        for key in ("Name", "Vault id", "Path", "Database", "VaultKey", "FEK"):
            label = QLabel()
            label.setTextInteractionFlags(
                Qt.TextInteractionFlag.TextSelectableByMouse)
            label.setWordWrap(True)
            self._labels[key] = label
            self.form.addRow(key, label)

        lock = QPushButton("Lock")
        lock.clicked.connect(self.lock_requested)
        buttons = QHBoxLayout()
        buttons.addStretch(1)
        buttons.addWidget(lock)

        layout = QVBoxLayout(self)
        layout.addWidget(title)
        layout.addLayout(self.form)
        layout.addStretch(1)
        layout.addLayout(buttons)
        layout.setContentsMargins(24, 20, 24, 20)

    def set_vault(self, vault: Vault) -> None:
        tables = vault.tables
        self._labels["Name"].setText(vault.name)
        self._labels["Vault id"].setText(vault.vault_id)
        self._labels["Path"].setText(vault.path)
        self._labels["Database"].setText(
            f"vault.db opened (read-only) — schema v{vault.schema_version}, "
            f"{len(tables)} tables")
        self._labels["VaultKey"].setText(
            f"{vault.vault_key_fingerprint} (fingerprint)")
        self._labels["FEK"].setText(f"{vault.fek_fingerprint} (fingerprint)")

    def clear(self) -> None:
        for label in self._labels.values():
            label.clear()


class MainWindow(QMainWindow):
    def __init__(self, start_dir: str | None = None):
        super().__init__()
        self.setWindowTitle(f"CryptOwl DevKit {__version__}")
        self.setFixedSize(WINDOW_WIDTH, 500)
        self.vault: Vault | None = None
        self.recent = RecentVaults()

        self.unlock_page = UnlockPage()
        self.vault_page = VaultPage()
        self.stack = QStackedWidget()
        self.stack.addWidget(self.unlock_page)
        self.stack.addWidget(self.vault_page)
        self.setCentralWidget(self.stack)

        self.unlock_page.unlock_requested.connect(self._unlock)
        self.vault_page.lock_requested.connect(self.lock)

        self._build_menu()
        self._refresh_recents()

        prefill = start_dir or self.recent.latest_path() or ""
        if prefill:
            self.unlock_page.set_path(prefill)
        self.statusBar().showMessage("Ready")

    # -- menu ---------------------------------------------------------------

    def _build_menu(self) -> None:
        file_menu = self.menuBar().addMenu("&File")
        open_action = QAction("&Open vault…", self)
        open_action.triggered.connect(self._open_from_menu)
        self.lock_action = QAction("&Lock", self)
        self.lock_action.triggered.connect(self.lock)
        exit_action = QAction("E&xit", self)
        exit_action.triggered.connect(self.close)
        file_menu.addAction(open_action)
        file_menu.addAction(self.lock_action)
        file_menu.addSeparator()
        file_menu.addAction(exit_action)

        help_menu = self.menuBar().addMenu("&Help")
        about_action = QAction("&About", self)
        about_action.triggered.connect(self._about)
        help_menu.addAction(about_action)

        self._set_locked_ui(True)

    def _open_from_menu(self) -> None:
        if self.stack.currentWidget() is self.vault_page:
            return
        self.unlock_page._browse()

    def _about(self) -> None:
        QMessageBox.about(
            self, "About CryptOwl DevKit",
            f"<b>CryptOwl DevKit {__version__}</b><br><br>"
            "Step 1: open a desktop-bound vault (SQLCipher raw key), remember "
            "vault folders, and unlock with the master password.<br><br>"
            "Byte-compatible with the Android app; vault contents and media "
            "come in later steps.")

    # -- unlock / lock ------------------------------------------------------

    def _unlock(self, path: str, password: str) -> None:
        try:
            vault = Vault.open(path, password.encode("utf-8"))
        except VaultError as exc:
            logger.warning("open failed for %s: %s", path, exc)
            QMessageBox.warning(self, "Unlock", str(exc))
            return
        except Exception as exc:  # noqa: BLE001 - native errors can be Error
            logger.exception("unexpected open failure for %s", path)
            QMessageBox.critical(self, "Unlock", f"Could not open the vault:\n{exc}")
            return

        self.vault = vault
        self.vault_page.set_vault(vault)
        self.unlock_page.password_edit.clear()
        self.recent.add(vault.path, vault.name)
        self._refresh_recents()
        self.stack.setCurrentWidget(self.vault_page)
        self._set_locked_ui(False)
        self.statusBar().showMessage(f"Unlocked {vault.path}")

    def lock(self) -> None:
        path = self.vault.path if self.vault is not None else None
        if self.vault is not None:
            self.vault.close()
            self.vault = None
        self.vault_page.clear()
        if path:
            self.unlock_page.set_path(path)
        self.stack.setCurrentWidget(self.unlock_page)
        self.unlock_page.refresh_detection()
        self.unlock_page.password_edit.setFocus()
        self._set_locked_ui(True)
        self.statusBar().showMessage("Locked")

    def _set_locked_ui(self, locked: bool) -> None:
        self.lock_action.setEnabled(not locked)

    def _refresh_recents(self) -> None:
        self.unlock_page.set_recents(self.recent.entries())

    def closeEvent(self, event) -> None:
        if self.vault is not None:
            self.vault.close()
            self.vault = None
        super().closeEvent(event)
