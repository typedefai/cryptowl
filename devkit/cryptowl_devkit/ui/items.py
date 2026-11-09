from __future__ import annotations

"""Generic item detail page: title + classification + plugin body editor +
optional commit message + actions. Hosted in a popup window; lists live in the
browser. S/T tiers gate on the KEK (dev: SMK-wrapped) / secondary password."""

import time

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (QComboBox, QDialog, QDialogButtonBox,
                               QHBoxLayout, QInputDialog, QLabel, QLineEdit,
                               QMessageBox, QPushButton, QTableWidget,
                               QTableWidgetItem, QVBoxLayout, QWidget)

from ..vault import (BadSecondaryPassword, Item, ItemDraft, ItemRepository,
                     SecondaryPasswordNotSet, Vault)
from .registry import ItemType

CLASSIFICATION_LABELS = (
    ("C", "Confidential"),
    ("S", "Secret"),
    ("T", "Top Secret"),
)


def _fmt_time(ms: int) -> str:
    try:
        return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(ms / 1000))
    except (TypeError, ValueError, OSError):
        return str(ms)


class ItemPage(QWidget):
    """Edits one item of a given type; Save is explicit, with discard guards."""

    item_selected = Signal(object)   # Item | None
    dirty_changed = Signal(bool)
    items_changed = Signal()

    def __init__(self, vault: Vault, item_type: ItemType,
                 repo: ItemRepository | None = None,
                 parent_id: str | None = None, parent=None):
        super().__init__(parent)
        self.vault = vault
        self.item_type = item_type
        self.repo = repo or ItemRepository(vault)
        self.parent_id = parent_id
        self._current: Item | None = None
        self._locked = False
        self._dirty = False
        self._loading = False

        self.new_btn = QPushButton("New")
        self.save_btn = QPushButton("Save")
        self.delete_btn = QPushButton("Delete")
        self.pin_btn = QPushButton("Pin")
        self.history_btn = QPushButton("History…")
        self.unlock_btn = QPushButton("Unlock")
        self.unlock_btn.setVisible(False)
        self.new_btn.clicked.connect(self.new_item)
        self.save_btn.clicked.connect(self.save)
        self.delete_btn.clicked.connect(self.delete)
        self.pin_btn.clicked.connect(self.toggle_pin)
        self.history_btn.clicked.connect(self.show_history)
        self.unlock_btn.clicked.connect(self._unlock_current)
        actions = QHBoxLayout()
        for button in (self.new_btn, self.save_btn, self.delete_btn,
                       self.pin_btn, self.history_btn, self.unlock_btn):
            actions.addWidget(button)
        actions.addStretch(1)

        self.title_edit = QLineEdit()
        self.title_edit.setPlaceholderText("Title")
        self.class_combo = QComboBox()
        for code, label in CLASSIFICATION_LABELS:
            self.class_combo.addItem(f"{label} ({code})", code)
        title_row = QHBoxLayout()
        title_row.addWidget(self.title_edit, 1)
        title_row.addWidget(QLabel("Level"))
        title_row.addWidget(self.class_combo)

        self.body = item_type.make_editor()
        self.message_edit = QLineEdit()
        self.message_edit.setPlaceholderText(
            "Describe the change (optional commit message)")
        self.title_edit.textChanged.connect(self._mark_dirty)
        self.class_combo.currentIndexChanged.connect(self._mark_dirty)
        if hasattr(self.body, "textChanged"):
            self.body.textChanged.connect(self._mark_dirty)

        layout = QVBoxLayout(self)
        layout.addLayout(actions)
        layout.addWidget(QLabel("Title"))
        layout.addLayout(title_row)
        layout.addWidget(QLabel(f"{item_type.display} content"))
        layout.addWidget(self.body, 1)
        layout.addWidget(self.message_edit)
        layout.setContentsMargins(12, 12, 12, 12)

        self._sync_buttons()

    # -- state --------------------------------------------------------------

    def selected_item(self) -> Item | None:
        return self._current

    def confirm_close(self) -> bool:
        return self._confirm_discard()

    def select(self, item_id: str) -> None:
        if self._current is not None and self._current.id == item_id:
            return
        if not self._confirm_discard():
            return
        self._load(item_id)

    def refresh(self, select_id: str | None = None) -> None:
        item_id = select_id or (self._current.id if self._current else None)
        if item_id:
            self._load(item_id)
        else:
            self._clear_editor()

    def new_item(self) -> None:
        if not self._confirm_discard():
            return
        self._clear_editor()
        self.title_edit.setFocus()

    # -- save / delete / pin -------------------------------------------------

    def save(self) -> None:
        classification = self.class_combo.currentData()
        draft = ItemDraft(
            type=self.item_type.type_id,
            title=self.title_edit.text(),
            content=self.body.get_text(),
            classification=classification,
            parent_id=self._current.parent_id if self._current
            else self.parent_id,
            meta=self._current.meta if self._current else None,
            pinned=self._current.pinned if self._current else False,
            message=self.message_edit.text().strip())

        def action(ts_password):
            if self._current is None:
                return self.repo.create(draft, ts_password=ts_password)
            self.repo.update(self._current.id, draft,
                             ts_password=ts_password)
            return self._current.id

        item_id = self._with_secondary_password(classification, action)
        if item_id is None:
            return
        self.message_edit.clear()
        self._load(item_id)
        self.items_changed.emit()

    def delete(self) -> None:
        if self._current is None:
            return
        choice = QMessageBox.question(
            self, self.item_type.display,
            f"Delete '{self._current.title or '(untitled)'}'?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.Cancel)
        if choice != QMessageBox.StandardButton.Yes:
            return
        self.repo.soft_delete(self._current.id)
        self._clear_editor()
        self.items_changed.emit()

    def toggle_pin(self) -> None:
        if self._current is None:
            return
        self.repo.set_pinned(self._current.id, not self._current.pinned)
        if self._locked:
            self.items_changed.emit()  # avoid re-prompting for the password
            return
        self._load(self._current.id)
        self.items_changed.emit()

    def show_history(self) -> None:
        if self._current is None:
            return
        dialog = HistoryDialog(self.repo, self._current.id, self)
        if dialog.exec() == QDialog.DialogCode.Accepted:
            self._load(self._current.id)
            self.items_changed.emit()

    # -- secondary password --------------------------------------------------

    def _with_secondary_password(self, classification: str, action):
        """Run `action(ts_password)`, prompting for the secondary password."""
        ts_password = None
        if classification == "T":
            ts_password = self._ask_secondary_password(
                setup=self.vault.secondary_salt is None)
            if ts_password is None:
                return None
        while True:
            try:
                return action(ts_password)
            except SecondaryPasswordNotSet:
                ts_password = self._ask_secondary_password(setup=True)
                if ts_password is None:
                    return None
            except BadSecondaryPassword as exc:
                QMessageBox.warning(self, "Secondary password", str(exc))
                return None

    def ask_secondary_password(self, setup: bool = False):
        return self._ask_secondary_password(setup)

    def _ask_secondary_password(self, setup: bool):
        if setup:
            while True:
                password, ok = QInputDialog.getText(
                    self, "Secondary password",
                    "Set a secondary password for Top Secret items:",
                    QLineEdit.EchoMode.Password)
                if not ok:
                    return None
                if not password:
                    QMessageBox.warning(self, "Secondary password",
                                        "The password must not be empty.")
                    continue
                confirm, ok = QInputDialog.getText(
                    self, "Secondary password", "Confirm the password:",
                    QLineEdit.EchoMode.Password)
                if not ok:
                    return None
                if password != confirm:
                    QMessageBox.warning(self, "Secondary password",
                                        "Passwords do not match.")
                    continue
                try:
                    self.repo.tiers.set_secondary_password(
                        password.encode("utf-8"))
                except Exception as exc:  # noqa: BLE001
                    QMessageBox.warning(self, "Secondary password", str(exc))
                    return None
                return password.encode("utf-8")
        password, ok = QInputDialog.getText(
            self, "Secondary password",
            "Secondary password (Top Secret):",
            QLineEdit.EchoMode.Password)
        return password.encode("utf-8") if ok and password else None

    # -- internals ----------------------------------------------------------

    def _load(self, item_id: str) -> None:
        item = self.repo.get(item_id)
        if item is not None and item.locked:
            password = self._ask_secondary_password(setup=False)
            if password is not None:
                try:
                    item = self.repo.get(item_id, ts_password=password)
                except BadSecondaryPassword as exc:
                    QMessageBox.warning(self, "Secondary password", str(exc))
        self._current = item
        self._locked = bool(item is not None and item.locked)
        self._loading = True
        self.title_edit.setText(item.title if item else "")
        self.body.set_text("" if self._locked else (item.content if item else ""))
        index = self.class_combo.findData(item.classification if item else "C")
        self.class_combo.setCurrentIndex(max(index, 0))
        self.message_edit.clear()
        self._loading = False
        self._set_dirty(False)
        self._set_locked_ui()
        self.item_selected.emit(item)
        self._sync_buttons()

    def _set_locked_ui(self) -> None:
        self.body.setEnabled(not self._locked)
        self.title_edit.setEnabled(not self._locked)
        self.class_combo.setEnabled(not self._locked)
        self.unlock_btn.setVisible(self._locked)
        if self._locked:
            self.body.set_text("")
            self.body.setPlaceholderText(
                "Top Secret — unlock with the secondary password")

    def _unlock_current(self) -> None:
        if self._current is not None:
            self._load(self._current.id)

    def _clear_editor(self) -> None:
        self._current = None
        self._locked = False
        self._loading = True
        self.title_edit.clear()
        self.body.set_text("")
        self.class_combo.setCurrentIndex(0)
        self.message_edit.clear()
        self._loading = False
        self._set_dirty(False)
        self._set_locked_ui()
        self.item_selected.emit(None)
        self._sync_buttons()

    def _mark_dirty(self) -> None:
        if not self._loading:
            self._set_dirty(True)

    def _set_dirty(self, dirty: bool) -> None:
        if dirty != self._dirty:
            self._dirty = dirty
            self.dirty_changed.emit(dirty)
        self._sync_buttons()

    def _confirm_discard(self) -> bool:
        if not self._dirty:
            return True
        choice = QMessageBox.question(
            self, self.item_type.display, "Discard unsaved changes?",
            QMessageBox.StandardButton.Discard
            | QMessageBox.StandardButton.Cancel)
        return choice == QMessageBox.StandardButton.Discard

    def _sync_buttons(self) -> None:
        has_item = self._current is not None
        self.save_btn.setEnabled(
            not self._locked and (self._dirty or not has_item))
        self.delete_btn.setEnabled(has_item)
        self.pin_btn.setEnabled(has_item)
        self.history_btn.setEnabled(has_item)
        self.pin_btn.setText("Unpin" if has_item and self._current.pinned
                             else "Pin")


class HistoryDialog(QDialog):
    """Version list (seq | level | when | message) with restore."""

    def __init__(self, repo: ItemRepository, item_id: str, page: ItemPage):
        super().__init__(page)
        self.setWindowTitle("History")
        self.resize(620, 360)
        self.repo = repo
        self.item_id = item_id
        self.page = page

        versions = repo.versions(item_id)
        self.table = QTableWidget(len(versions), 4)
        self.table.setHorizontalHeaderLabels(
            ["Seq", "Level", "When", "Message"])
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.setSelectionBehavior(
            QTableWidget.SelectionBehavior.SelectRows)
        self.table.setSelectionMode(
            QTableWidget.SelectionMode.SingleSelection)
        self.table.verticalHeader().setVisible(False)
        for row, version in enumerate(versions):
            cells = (str(version.seq), version.classification,
                     _fmt_time(version.created_at), version.message)
            for col, text in enumerate(cells):
                cell = QTableWidgetItem(text)
                cell.setFlags(Qt.ItemFlag.ItemIsEnabled
                              | Qt.ItemFlag.ItemIsSelectable)
                if col == 3:
                    cell.setToolTip(text)
                self.table.setItem(row, col, cell)
        self.table.resizeColumnsToContents()
        self.table.setColumnWidth(3, 260)

        self.restore_btn = QPushButton("Restore selected")
        self.restore_btn.setEnabled(False)
        self.restore_btn.clicked.connect(self._restore)
        self.table.itemSelectionChanged.connect(
            lambda: self.restore_btn.setEnabled(
                self.table.currentRow() >= 0))

        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Close)
        buttons.rejected.connect(self.reject)
        buttons.accepted.connect(self.reject)
        bottom = QHBoxLayout()
        bottom.addWidget(self.restore_btn)
        bottom.addStretch(1)
        bottom.addWidget(buttons)

        layout = QVBoxLayout(self)
        layout.addWidget(self.table, 1)
        layout.addLayout(bottom)

    def _restore(self) -> None:
        row = self.table.currentRow()
        if row < 0:
            return
        versions = self.repo.versions(self.item_id)
        version = versions[row]
        password = None
        if version.classification == "T":
            password = self.page.ask_secondary_password(setup=False)
            if password is None:
                return
        try:
            self.repo.restore(version.id, ts_password=password)
        except BadSecondaryPassword as exc:
            QMessageBox.warning(self, "Secondary password", str(exc))
            return
        self.accept()
