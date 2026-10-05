from __future__ import annotations

"""Create-vault dialog."""

import os

from PySide6.QtWidgets import (QDialog, QDialogButtonBox, QFileDialog,
                               QFormLayout, QHBoxLayout, QLabel, QLineEdit,
                               QMessageBox, QPushButton, QVBoxLayout)


class CreateVaultDialog(QDialog):
    """Create a fresh desktop-bound vault (device_secret + SQLCipher DB)."""

    def __init__(self, parent=None, start_dir: str | None = None):
        super().__init__(parent)
        self.setWindowTitle("Create vault")
        self.setMinimumWidth(480)
        self._result = None

        self.parent_edit = QLineEdit(start_dir or os.path.expanduser("~"))
        browse = QPushButton("Browse…")
        browse.clicked.connect(self._browse)
        parent_row = QHBoxLayout()
        parent_row.addWidget(self.parent_edit, 1)
        parent_row.addWidget(browse)

        self.id_edit = QLineEdit("personal")
        self.name_edit = QLineEdit()
        self.name_edit.setPlaceholderText("Defaults to the vault id")
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
            "The vault is bound to this computer by a device_secret file, so it "
            "can be reopened here or copied to Android (the app re-binds it on "
            "first open).")
        hint.setWordWrap(True)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok
            | QDialogButtonBox.StandardButton.Cancel)
        buttons.accepted.connect(self._accept)
        buttons.rejected.connect(self.reject)

        layout = QVBoxLayout(self)
        layout.addLayout(form)
        layout.addWidget(hint)
        layout.addWidget(buttons)

    def _browse(self) -> None:
        chosen = QFileDialog.getExistingDirectory(
            self, "Select parent folder", self.parent_edit.text())
        if chosen:
            self.parent_edit.setText(chosen)

    def _accept(self) -> None:
        parent = os.path.abspath(
            os.path.expanduser(self.parent_edit.text().strip()))
        vault_id = self.id_edit.text().strip()
        password = self.password_edit.text()
        if not os.path.isdir(parent):
            QMessageBox.warning(self, "Create vault",
                                "Choose an existing parent folder.")
            return
        if (not vault_id or vault_id in (".", "..")
                or os.sep in vault_id or "/" in vault_id):
            QMessageBox.warning(self, "Create vault",
                                "Vault id must be a simple folder name.")
            return
        if len(password) < 8:
            QMessageBox.warning(
                self, "Create vault",
                "Use at least 8 characters for the master password.")
            return
        if password != self.confirm_edit.text():
            QMessageBox.warning(self, "Create vault", "Passwords do not match.")
            return
        target = os.path.join(parent, vault_id)
        if (os.path.exists(os.path.join(target, "vault.meta"))
                or os.path.exists(os.path.join(target, "vault.db"))):
            QMessageBox.warning(self, "Create vault",
                                f"{target} already contains a vault.")
            return
        self._result = (target, vault_id,
                        self.name_edit.text().strip() or vault_id, password)
        self.accept()

    def values(self):
        return self._result
