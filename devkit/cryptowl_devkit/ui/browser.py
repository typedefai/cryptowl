from __future__ import annotations

"""Content pane: items of the current selection in icons / list / details."""

import time

from PySide6.QtCore import QSize, Qt, Signal
from PySide6.QtWidgets import (QAbstractItemView, QHeaderView,
                               QListWidget, QListWidgetItem, QStackedWidget,
                               QTreeWidget, QTreeWidgetItem, QWidget)

from ..vault import FOLDER_TYPE, ItemSummary
from . import icons
from .registry import get_type

ICONS = "icons"
LIST = "list"
DETAILS = "details"


def _fmt_time(ms: int) -> str:
    try:
        return time.strftime("%Y-%m-%d %H:%M", time.localtime(ms / 1000))
    except (TypeError, ValueError, OSError):
        return str(ms)


def _type_label(summary: ItemSummary) -> str:
    if summary.type == FOLDER_TYPE:
        return "Folder"
    item_type = get_type(summary.type)
    return item_type.display if item_type else summary.type


def _data_id(item) -> str | None:
    """Item id from a QListWidgetItem (role only) or QTreeWidgetItem (column)."""
    if isinstance(item, QTreeWidgetItem):
        return item.data(0, Qt.ItemDataRole.UserRole)
    return item.data(Qt.ItemDataRole.UserRole)


class ContentView(QWidget):
    open_requested = Signal(str)          # item/folder id (activated)
    selection_changed = Signal(object)    # ItemSummary | None

    def __init__(self, parent=None):
        super().__init__(parent)
        self._summaries: dict[str, ItemSummary] = {}
        self._order: list[str] = []

        self.icons = QListWidget()
        self.icons.setViewMode(QListWidget.ViewMode.IconMode)
        self.icons.setIconSize(QSize(48, 48))
        self.icons.setGridSize(QSize(96, 84))
        self.icons.setResizeMode(QListWidget.ResizeMode.Adjust)
        self.icons.setMovement(QListWidget.Movement.Static)
        self.icons.setWordWrap(True)
        self.icons.setSpacing(6)

        self.list = QListWidget()
        self.list.setViewMode(QListWidget.ViewMode.ListMode)
        self.list.setIconSize(QSize(16, 16))

        self.details = QTreeWidget()
        self.details.setColumnCount(4)
        self.details.setHeaderLabels(
            ["Name", "Type", "Date modified", "Size"])
        self.details.setRootIsDecorated(False)
        self.details.setAlternatingRowColors(True)
        self.details.header().setStretchLastSection(False)
        self.details.header().setSectionResizeMode(
            0, QHeaderView.ResizeMode.Stretch)
        for column in (1, 2, 3):
            self.details.header().setSectionResizeMode(
                column, QHeaderView.ResizeMode.ResizeToContents)

        for widget in (self.icons, self.list, self.details):
            widget.setEditTriggers(
                QAbstractItemView.EditTrigger.NoEditTriggers)
            widget.setSelectionMode(
                QAbstractItemView.SelectionMode.SingleSelection)
            widget.itemSelectionChanged.connect(self._on_selection)
            widget.itemActivated.connect(self._on_activated)

        self.stack = QStackedWidget()
        for widget in (self.icons, self.list, self.details):
            self.stack.addWidget(widget)

        from PySide6.QtWidgets import QVBoxLayout
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.stack)

    # -- data ---------------------------------------------------------------

    def set_items(self, summaries: list[ItemSummary],
                  select_id: str | None = None) -> None:
        self._summaries = {s.id: s for s in summaries}
        self._order = [s.id for s in summaries]
        self.icons.clear()
        self.list.clear()
        self.details.clear()

        for summary in summaries:
            icon = self._icon(summary)
            label = ("Pinned · " if summary.pinned else "") + \
                (summary.title or "(untitled)")

            for widget in (self.icons, self.list):
                item = QListWidgetItem(icon, label)
                item.setData(Qt.ItemDataRole.UserRole, summary.id)
                item.setToolTip(f"{_type_label(summary)} · "
                                f"updated {_fmt_time(summary.updated_at)}")
                widget.addItem(item)

            row = QTreeWidgetItem([label, _type_label(summary),
                                   _fmt_time(summary.updated_at), "—"])
            row.setIcon(0, icon)
            row.setData(0, Qt.ItemDataRole.UserRole, summary.id)
            self.details.addTopLevelItem(row)

        if select_id and select_id in self._summaries:
            self.select_id(select_id)

    def set_filter(self, text: str) -> None:
        """Hide entries whose name doesn't contain `text` (current path only)."""
        needle = (text or "").strip().lower()
        for widget in (self.icons, self.list):
            for row in range(widget.count()):
                item = widget.item(row)
                item.setHidden(bool(needle) and needle not in item.text().lower())
        for row in range(self.details.topLevelItemCount()):
            item = self.details.topLevelItem(row)
            item.setHidden(bool(needle) and needle not in item.text(0).lower())

    def set_view_mode(self, mode: str) -> None:
        index = {ICONS: 0, LIST: 1, DETAILS: 2}.get(mode, 0)
        current = self.current_id()
        self.stack.setCurrentIndex(index)
        if current:
            self.select_id(current)

    def current_id(self) -> str | None:
        return self._widget_id(self.stack.currentWidget())

    def select_id(self, item_id: str) -> None:
        for widget in (self.icons, self.list, self.details):
            blocked = widget.blockSignals(True)
            try:
                self._select_in(widget, item_id)
            finally:
                widget.blockSignals(blocked)
        self.selection_changed.emit(self._summaries.get(item_id))

    def _select_in(self, widget, item_id: str) -> None:
        if widget is self.details:
            for row in range(widget.topLevelItemCount()):
                item = widget.topLevelItem(row)
                if _data_id(item) == item_id:
                    widget.setCurrentItem(item)
                    widget.scrollToItem(item)
                    return
            return
        for row in range(widget.count()):
            item = widget.item(row)
            if _data_id(item) == item_id:
                widget.setCurrentItem(item)
                widget.scrollToItem(item)
                return

    def _widget_id(self, widget) -> str | None:
        item = widget.currentItem()
        return _data_id(item) if item is not None else None

    def clear(self) -> None:
        self.set_items([])

    # -- internals ----------------------------------------------------------

    def _icon(self, summary: ItemSummary):
        if summary.type == FOLDER_TYPE:
            return icons.folder_icon()
        item_type = get_type(summary.type)
        return item_type.icon() if item_type else icons.icon("document")

    def _on_selection(self) -> None:
        widget = self.sender() or self.stack.currentWidget()
        item_id = self._widget_id(widget)
        self.selection_changed.emit(
            self._summaries.get(item_id) if item_id else None)

    def _on_activated(self, item) -> None:
        item_id = _data_id(item)
        if item_id:
            self.open_requested.emit(item_id)
