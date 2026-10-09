from __future__ import annotations

"""Separate popup window hosting the item editor (no tabs)."""

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import QMainWindow

from ..vault import ItemRepository, Vault
from .items import ItemPage
from .registry import ItemType


class ItemWindow(QMainWindow):
    changed = Signal()   # save/delete happened (browser should refresh)

    def __init__(self, vault: Vault, repo: ItemRepository, item_type: ItemType,
                 item_id: str | None = None, parent_id: str | None = None,
                 parent=None):
        super().__init__(parent)
        self.setAttribute(Qt.WidgetAttribute.WA_DeleteOnClose, True)
        self.resize(600, 500)

        self.page = ItemPage(vault, item_type, repo=repo, parent_id=parent_id)
        self.setCentralWidget(self.page)
        self.page.item_selected.connect(self._update_title)
        self.page.items_changed.connect(self._on_changed)

        if item_id is not None:
            self.page.select(item_id)
        else:
            self.page.new_item()
        self._update_title(self.page.selected_item())

    def show_history(self) -> None:
        self.page.show_history()

    def _update_title(self, item) -> None:
        name = item.title if item else \
            f"New {self.page.item_type.display.lower()}"
        self.setWindowTitle(f"{name or '(untitled)'} — "
                            f"{self.page.item_type.display}")

    def _on_changed(self) -> None:
        self.changed.emit()
        if self.page.selected_item() is None:
            self.close()

    def closeEvent(self, event) -> None:
        if not self.page.confirm_close():
            event.ignore()
            return
        super().closeEvent(event)
