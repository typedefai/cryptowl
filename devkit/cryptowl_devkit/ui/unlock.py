from __future__ import annotations

"""Login-style unlock panel: current vault, master password, switch menu."""

import os

from PySide6.QtCore import QPoint, Qt, Signal
from PySide6.QtGui import QFontMetrics
from PySide6.QtWidgets import (QFileDialog, QHBoxLayout, QLabel, QLineEdit,
                               QMenu, QMessageBox, QPushButton, QVBoxLayout,
                               QWidget)

from ..vault import inspect_folder
from . import app_icon

WARN_COLOR = "#9a6700"
ERROR_COLOR = "#cf222e"
HINT_COLOR = "#57606a"
PATH_ELIDE_WIDTH = 380


class UnlockPage(QWidget):
    """Login-style panel: current vault, master password, switch-vault menu."""

    unlock_requested = Signal(str, str)
    create_requested = Signal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self._entries = []

        icon = QLabel()
        icon.setPixmap(app_icon().pixmap(72, 72))
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

        self.create_link = QLabel('<a href="create">Create vault…</a>')
        self.switch_link = QLabel('<a href="switch">Switch vault…</a>')
        separator = QLabel("·")
        separator.setStyleSheet(f"color: {HINT_COLOR};")
        links = QHBoxLayout()
        links.addStretch(1)
        links.addWidget(self.create_link)
        links.addWidget(separator)
        links.addWidget(self.switch_link)
        links.addStretch(1)
        for link in (self.create_link, self.switch_link):
            link.setTextInteractionFlags(
                Qt.TextInteractionFlag.LinksAccessibleByMouse)
        self.create_link.linkActivated.connect(
            lambda _: self.create_requested.emit())
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
        layout.addLayout(links)
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

    def browse(self) -> None:
        start = self.current_path() or os.path.expanduser("~")
        chosen = QFileDialog.getExistingDirectory(self, "Select vault folder",
                                                  start)
        if chosen:
            self.set_path(chosen, focus=True)

    def _show_switch_menu(self) -> None:
        if not self._entries:
            self.browse()
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
        menu.addAction("Choose folder…", self.browse)
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
