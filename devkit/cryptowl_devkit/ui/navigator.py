from __future__ import annotations

"""Vault Navigator tree (left dock): sections, metadata files, DB tables."""

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import QTreeWidget, QTreeWidgetItem

from ..vault import Vault

OVERVIEW = "overview"
KEYS = "keys"
SQLCIPHER = "sqlcipher"
META = "meta"
CONFIG = "config"
DEVICE_SECRET = "device_secret"
TABLE = "table"


class NavigatorTree(QTreeWidget):
    open_requested = Signal(str, str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setHeaderHidden(True)
        self.setUniformRowHeights(True)
        self.vault: Vault | None = None
        self.itemActivated.connect(self._activated)

    def set_vault(self, vault: Vault) -> None:
        self.vault = vault
        super().clear()
        root = QTreeWidgetItem([f"{vault.name}  ({vault.vault_id})"])
        font = root.font(0)
        font.setBold(True)
        root.setFont(0, font)
        self.addTopLevelItem(root)

        for label, kind in (("Overview", OVERVIEW), ("Key chain", KEYS)):
            root.addChild(self._item(label, kind, ""))

        metadata = QTreeWidgetItem(["Metadata"])
        for label, kind in (("vault.meta", META), ("config.json", CONFIG),
                            ("device_secret", DEVICE_SECRET)):
            metadata.addChild(self._item(label, kind, ""))
        root.addChild(metadata)

        database = QTreeWidgetItem([f"Database (schema v{vault.schema_version})"])
        for table in vault.tables:
            database.addChild(
                self._item(f"{table}  ({vault.db.row_count(table)})", TABLE, table))
        root.addChild(database)

        root.setExpanded(True)
        metadata.setExpanded(True)
        database.setExpanded(True)

    def clear_vault(self) -> None:
        self.vault = None
        super().clear()

    def _item(self, label: str, kind: str, key: str) -> QTreeWidgetItem:
        item = QTreeWidgetItem([label])
        item.setData(0, Qt.ItemDataRole.UserRole, (kind, key))
        return item

    def _activated(self, item: QTreeWidgetItem, _column: int = 0) -> None:
        data = item.data(0, Qt.ItemDataRole.UserRole)
        if data:
            self.open_requested.emit(data[0], data[1])
