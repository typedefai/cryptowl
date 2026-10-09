from __future__ import annotations

"""Explorer-style navigation tree: This vault > pinned shortcuts / type
filters / folder tree, all with icons."""

from PySide6.QtCore import QSize, Qt, Signal
from PySide6.QtWidgets import QTreeWidget, QTreeWidgetItem

from ..vault import FOLDER_TYPE, FolderInfo, ItemRepository
from . import icons
from .registry import REGISTRY

K_ROOT = "root"
K_FOLDER = "folder"
K_TYPE = "type"


class Sidebar(QTreeWidget):
    folder_selected = Signal(object)   # folder id (str) or None for the root
    type_selected = Signal(str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setHeaderHidden(True)
        self.setUniformRowHeights(True)
        self.setIconSize(QSize(16, 16))
        self._loading = False
        self.currentItemChanged.connect(self._on_current_changed)

    def set_repo(self, repo: ItemRepository) -> None:
        self._loading = True
        self.clear()
        folders = repo.folders()
        counts = repo.counts()

        root = QTreeWidgetItem(["This vault"])
        root.setIcon(0, icons.icon("desktop", "#4A5568"))
        root.setData(0, Qt.ItemDataRole.UserRole, (K_ROOT, None))
        font = root.font(0)
        font.setBold(True)
        root.setFont(0, font)
        self.addTopLevelItem(root)

        for folder in folders:
            if folder.pinned:
                item = self._folder_item(folder)
                item.setToolTip(0, "Pinned folder")
                root.addChild(item)

        for item_type in _registry_types():
            node = QTreeWidgetItem(
                [f"{item_type.display}  ({counts.get(item_type.type_id, 0)})"])
            node.setIcon(0, item_type.icon())
            node.setData(0, Qt.ItemDataRole.UserRole,
                         (K_TYPE, item_type.type_id))
            node.setToolTip(0, f"Show all {item_type.display.lower()} items")
            root.addChild(node)

        self._add_folder_children(root, folders, None)
        self.expandAll()
        self._loading = False

    def select_folder(self, folder_id: str | None) -> None:
        matched = self._find(K_FOLDER, folder_id) or self._find(K_ROOT, None)
        self._set_current(matched)

    def select_type(self, type_id: str) -> None:
        self._set_current(self._find(K_TYPE, type_id))

    # -- internals ----------------------------------------------------------

    def _folder_item(self, folder: FolderInfo) -> QTreeWidgetItem:
        item = QTreeWidgetItem([folder.title or "(untitled)"])
        item.setIcon(0, icons.folder_icon())
        item.setData(0, Qt.ItemDataRole.UserRole, (K_FOLDER, folder.id))
        return item

    def _add_folder_children(self, parent_item: QTreeWidgetItem,
                             folders: list[FolderInfo],
                             parent_id: str | None) -> None:
        for folder in folders:
            if folder.parent_id != parent_id:
                continue
            item = self._folder_item(folder)
            parent_item.addChild(item)
            self._add_folder_children(item, folders, folder.id)
            item.setExpanded(True)

    def _find(self, kind: str, key) -> QTreeWidgetItem | None:
        stack = [self.topLevelItem(i)
                 for i in range(self.topLevelItemCount())]
        while stack:
            item = stack.pop()
            if item.data(0, Qt.ItemDataRole.UserRole) == (kind, key):
                return item
            stack.extend(item.child(i) for i in range(item.childCount()))
        return None

    def _set_current(self, item: QTreeWidgetItem | None) -> None:
        previous = self._loading
        self._loading = True
        self.setCurrentItem(item)
        self._loading = previous

    def _on_current_changed(self, current: QTreeWidgetItem | None,
                            _previous) -> None:
        if self._loading or current is None:
            return
        data = current.data(0, Qt.ItemDataRole.UserRole)
        if not data:
            return
        kind, key = data
        if kind == K_TYPE:
            self.type_selected.emit(key)
        else:
            self.folder_selected.emit(key)


def _registry_types():
    return [t for t in REGISTRY if t.type_id != FOLDER_TYPE]
