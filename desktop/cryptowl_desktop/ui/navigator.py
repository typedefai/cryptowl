from __future__ import annotations

"""Vault Navigator (left tree, DBeaver-style).

Nodes carry (kind, key); a click asks the main window to open the matching
editor tab. Built from the live vault: schema tables with row counts, files
with sizes, wrapped keys.
"""

from PyQt6.QtCore import Qt
from PyQt6.QtGui import QBrush, QColor
from PyQt6.QtWidgets import (QMenu, QStyle, QTreeWidget, QTreeWidgetItem)

from ..vault import catalog, files as filesv

# node kinds understood by the editor manager in main_window
OVERVIEW = "overview"
META = "meta"
TABLE = "table"
KEYS = "keys"
FILES = "files"
FEATURE = "feature"
SQL = "sql"
LAB = "lab"

_FEATURES = [("notes", "Notes"), ("media", "Media"),
             ("moments", "Moments"), ("passwords", "Passwords")]

_ACCENT = QColor("#007ACC")
_COUNT = QColor("#5A5A5A")


class NavigatorTree(QTreeWidget):

    def __init__(self, vault, on_open, parent=None):
        super().__init__(parent)
        self.vault = vault
        self._on_open = on_open
        self.setHeaderHidden(True)
        self.setRootIsDecorated(True)
        self.itemClicked.connect(self._activated)
        self.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.customContextMenuRequested.connect(self._context_menu)
        self.build()

    # ------------------------------------------------------------------ build

    def build(self):
        self.clear()
        if self.vault is None:
            placeholder = QTreeWidgetItem(["No vault open"])
            placeholder.setFlags(placeholder.flags() & ~Qt.ItemFlag.ItemIsEnabled)
            self.addTopLevelItem(placeholder)
            return
        style = self.style()
        sp = QStyle.StandardPixmap

        root = QTreeWidgetItem([f"{self.vault.vault_id}  ({self.vault.config.get('name') or 'vault'})"])
        root.setIcon(0, style.standardIcon(sp.SP_DirOpenIcon))
        self.addTopLevelItem(root)
        root.setExpanded(True)

        def child(parent, label, kind, key=None, icon=None, count=None):
            item = QTreeWidgetItem([label])
            item.setData(0, Qt.ItemDataRole.UserRole, (kind, key))
            if icon is not None:
                item.setIcon(0, style.standardIcon(icon))
            if count is not None:
                item.setText(0, f"{label}   {count}")
                item.setForeground(0, QBrush(_COUNT))
            if kind == OVERVIEW:
                item.setForeground(0, QBrush(_ACCENT))
            parent.addChild(item)
            return item

        child(root, "Overview", OVERVIEW, icon=sp.SP_ComputerIcon)
        meta = QTreeWidgetItem(["Metadata"])
        meta.setIcon(0, style.standardIcon(sp.SP_FileDialogDetailedView))
        root.addChild(meta)
        meta.setExpanded(True)
        for key, label in (("vault.meta", "vault.meta"),
                           ("config", "config.json + config.sig"),
                           ("device_secret", "device_secret")):
            child(meta, label, META, key, icon=sp.SP_FileIcon)

        version = catalog.user_version(self.vault.conn)
        schema_item = QTreeWidgetItem([f"Schema  v{version}"])
        schema_item.setIcon(0, style.standardIcon(sp.SP_FileDialogListView))
        root.addChild(schema_item)
        schema_item.setExpanded(True)
        for table in catalog.tables(self.vault.conn):
            count = table["row_count"]
            label = f"{table['name']}   ({count if count is not None else '?'})"
            child(schema_item, table["name"], TABLE, table["name"],
                  icon=sp.SP_FileDialogListView)

        keys = QTreeWidgetItem(["Security"])
        keys.setIcon(0, style.standardIcon(sp.SP_MessageBoxWarning))
        root.addChild(keys)
        keys.setExpanded(True)
        child(keys, "Keys & chain", KEYS, icon=sp.SP_FileDialogInfoView)

        files_root = QTreeWidgetItem(["Files on disk"])
        files_root.setIcon(0, style.standardIcon(sp.SP_DirIcon))
        root.addChild(files_root)
        for subdir in ("attachments", "thumbnails"):
            entries = filesv.list_files(self.vault, subdir)
            child(files_root, subdir, FILES, subdir,
                  icon=sp.SP_DirIcon, count=len(entries))

        features = QTreeWidgetItem(["Features (friendly editors)"])
        features.setIcon(0, style.standardIcon(sp.SP_FileDialogDetailedView))
        root.addChild(features)
        for key, label in _FEATURES:
            child(features, label, FEATURE, key, icon=sp.SP_FileIcon)

        tools = QTreeWidgetItem(["Tools"])
        tools.setIcon(0, style.standardIcon(sp.SP_FileDialogContentsView))
        root.addChild(tools)
        child(tools, "SQL console", SQL, icon=sp.SP_FileDialogContentsView)
        child(tools, "Crypto lab (dev)", LAB, icon=sp.SP_MessageBoxWarning)
        self.resizeColumnToContents(0)

    # ---------------------------------------------------------------- actions

    def _activated(self, item, _column=0):
        data = item.data(0, Qt.ItemDataRole.UserRole)
        if data:
            kind, key = data
            self._on_open(kind, key)

    def _context_menu(self, pos):
        item = self.itemAt(pos)
        if item is None or self.vault is None:
            return
        data = item.data(0, Qt.ItemDataRole.UserRole)
        menu = QMenu(self)
        if data:
            kind, key = data
            menu.addAction("Open", lambda: self._on_open(kind, key))
            if kind == TABLE:
                menu.addAction("Open with empty filter",
                               lambda: self._on_open(kind, key, {"filter": ""}))
        else:
            menu.addAction("Refresh", self.refresh_counts)
        menu.exec(self.mapToGlobal(pos))

    def refresh_counts(self):
        """Rebuilds the tree (row counts / file listings are rebuilt too)."""
        if self.vault is None:
            return
        self.build()
