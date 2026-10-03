from __future__ import annotations

"""Unlock / create / change-password dialogs."""

import os

from PyQt6.QtWidgets import (QDialog, QDialogButtonBox, QFileDialog,
                             QFormLayout, QHBoxLayout, QLabel, QLineEdit,
                             QMessageBox, QPushButton, QVBoxLayout)


class OpenVaultDialog(QDialog):
    """Pick a vault directory and enter the master password."""

    def __init__(self, parent=None, start_dir: str | None = None):
        super().__init__(parent)
        self.setWindowTitle("Open vault")
        self.setMinimumWidth(520)
        self._result_path = None
        self._result_password = None

        self.path_edit = QLineEdit(start_dir or "")
        browse = QPushButton("Browse…")
        browse.clicked.connect(self._browse)
        path_row = QHBoxLayout()
        path_row.addWidget(self.path_edit)
        path_row.addWidget(browse)

        self.password_edit = QLineEdit()
        self.password_edit.setEchoMode(QLineEdit.EchoMode.Password)

        form = QFormLayout()
        form.addRow("Vault folder", path_row)
        form.addRow("Master password", self.password_edit)

        hint = QLabel(
            "The folder must contain vault.meta, vault.db, config.json, config.sig\n"
            "and a device_secret file (vaults bound to an Android Keystore cannot\n"
            "be opened on the desktop).")
        hint.setWordWrap(True)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        buttons.accepted.connect(self._accept)
        buttons.rejected.connect(self.reject)

        layout = QVBoxLayout(self)
        layout.addLayout(form)
        layout.addWidget(hint)
        layout.addWidget(buttons)

    def _browse(self):
        chosen = QFileDialog.getExistingDirectory(
            self, "Select vault folder", self.path_edit.text() or os.path.expanduser("~"))
        if chosen:
            self.path_edit.setText(chosen)

    def _accept(self):
        path = self.path_edit.text().strip()
        password = self.password_edit.text()
        if not os.path.isfile(os.path.join(path, "vault.meta")):
            QMessageBox.warning(self, "Open vault",
                                "That folder does not contain a vault.meta file.")
            return
        if not password:
            QMessageBox.warning(self, "Open vault", "Enter the master password.")
            return
        self._result_path = path
        self._result_password = password
        self.accept()

    def values(self):
        return self._result_path, self._result_password


class CreateVaultDialog(QDialog):
    """Create a fresh desktop-bound vault (device_secret + full schema)."""

    def __init__(self, parent=None, start_dir: str | None = None):
        super().__init__(parent)
        self.setWindowTitle("Create vault")
        self.setMinimumWidth(520)
        self._result = None

        self.parent_edit = QLineEdit(start_dir or os.path.expanduser("~"))
        browse = QPushButton("Browse…")
        browse.clicked.connect(self._browse)
        parent_row = QHBoxLayout()
        parent_row.addWidget(self.parent_edit)
        parent_row.addWidget(browse)

        self.id_edit = QLineEdit("personal")
        self.name_edit = QLineEdit("Personal")
        self.password_edit = QLineEdit()
        self.password_edit.setEchoMode(QLineEdit.EchoMode.Password)
        self.confirm_edit = QLineEdit()
        self.confirm_edit.setEchoMode(QLineEdit.EchoMode.Password)

        form = QFormLayout()
        form.addRow("Parent folder", parent_row)
        form.addRow("Vault id (folder name)", self.id_edit)
        form.addRow("Display name", self.name_edit)
        form.addRow("Master password", self.password_edit)
        form.addRow("Confirm password", self.confirm_edit)

        hint = QLabel(
            "The desktop creates vaults with a device_secret file, so they can be\n"
            "copied to Android (the app re-binds them on first open). Vaults created\n"
            "on Android cannot be opened here afterwards.")
        hint.setWordWrap(True)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        buttons.accepted.connect(self._accept)
        buttons.rejected.connect(self.reject)

        layout = QVBoxLayout(self)
        layout.addLayout(form)
        layout.addWidget(hint)
        layout.addWidget(buttons)

    def _browse(self):
        chosen = QFileDialog.getExistingDirectory(
            self, "Select parent folder", self.parent_edit.text())
        if chosen:
            self.parent_edit.setText(chosen)

    def _accept(self):
        parent = self.parent_edit.text().strip()
        vault_id = self.id_edit.text().strip()
        password = self.password_edit.text()
        if not parent or not os.path.isdir(parent):
            QMessageBox.warning(self, "Create vault", "Choose an existing parent folder.")
            return
        if not vault_id or "/" in vault_id or os.sep in vault_id:
            QMessageBox.warning(self, "Create vault", "Vault id must be a simple name.")
            return
        if len(password) < 8:
            QMessageBox.warning(self, "Create vault",
                                "Use at least 8 characters for the master password.")
            return
        if password != self.confirm_edit.text():
            QMessageBox.warning(self, "Create vault", "Passwords do not match.")
            return
        path = os.path.join(parent, vault_id)
        if os.path.exists(os.path.join(path, "vault.db")):
            QMessageBox.warning(self, "Create vault",
                                f"{path} already contains a vault.db.")
            return
        self._result = (path, vault_id, self.name_edit.text().strip() or vault_id, password)
        self.accept()

    def values(self):
        return self._result


class ChangePasswordDialog(QDialog):
    """Fast master-password change (rewrap + re-sign, O(1))."""

    def __init__(self, vault, parent=None):
        super().__init__(parent)
        self.vault = vault
        self.setWindowTitle("Change master password")
        self.setMinimumWidth(460)

        self.current_edit = QLineEdit()
        self.current_edit.setEchoMode(QLineEdit.EchoMode.Password)
        self.new_edit = QLineEdit()
        self.new_edit.setEchoMode(QLineEdit.EchoMode.Password)
        self.confirm_edit = QLineEdit()
        self.confirm_edit.setEchoMode(QLineEdit.EchoMode.Password)

        form = QFormLayout()
        form.addRow("Current password", self.current_edit)
        form.addRow("New password", self.new_edit)
        form.addRow("Confirm new password", self.confirm_edit)

        note = QLabel(
            "Only vault_key:smk is rewrapped and both integrity signatures are\n"
            "re-signed — the database and files are not re-encrypted.")
        note.setWordWrap(True)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        buttons.accepted.connect(self._accept)
        buttons.rejected.connect(self.reject)

        layout = QVBoxLayout(self)
        layout.addLayout(form)
        layout.addWidget(note)
        layout.addWidget(buttons)

    def _accept(self):
        new_password = self.new_edit.text()
        if len(new_password) < 8:
            QMessageBox.warning(self, self.windowTitle(),
                                "Use at least 8 characters for the new password.")
            return
        if new_password != self.confirm_edit.text():
            QMessageBox.warning(self, self.windowTitle(), "New passwords do not match.")
            return
        try:
            self.vault.change_master_password(
                self.current_edit.text().encode("utf-8"),
                new_password.encode("utf-8"))
        except Exception as exc:  # noqa: BLE001 - shown to the user
            QMessageBox.critical(self, self.windowTitle(), str(exc))
            return
        QMessageBox.information(
            self, self.windowTitle(),
            "Master password changed. Use the new password on the next open.")
        self.accept()
