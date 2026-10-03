from __future__ import annotations

"""DBeaver-style table editor: Data (read-only grid) | DDL | Statistics."""

from PyQt6.QtCore import QAbstractTableModel, QModelIndex, Qt
from PyQt6.QtGui import QBrush, QColor, QFont
from PyQt6.QtWidgets import (QDialog, QHBoxLayout, QLabel, QLineEdit, QMenu,
                             QPlainTextEdit, QPushButton, QSplitter,
                             QTableView, QTabWidget, QTableWidget,
                             QTableWidgetItem, QVBoxLayout, QWidget)

from ...vault import catalog
from ...vault.grid import Grid
from ...vault.values import base32, describe, hex_dump

# roles
RAW = Qt.ItemDataRole.UserRole + 1
COLUMN = Qt.ItemDataRole.UserRole + 2

_BADGES = {"C": QColor("#2E7D32"), "S": QColor("#B26A00"), "T": QColor("#C62828")}
_NULL = QColor("#909090")
_BOLD = QFont()
_BOLD.setBold(True)


class TableModel(QAbstractTableModel):
    """Rows of a page; blobs/NULLs rendered via values.describe."""

    def __init__(self, columns, rows, parent=None):
        super().__init__(parent)
        self._columns = columns
        self._rows = rows

    def set_rows(self, columns, rows):
        self.beginResetModel()
        self._columns, self._rows = columns, rows
        self.endResetModel()

    def rowCount(self, parent=QModelIndex()):
        return 0 if parent.isValid() else len(self._rows)

    def columnCount(self, parent=QModelIndex()):
        return 0 if parent.isValid() else len(self._columns)

    def headerData(self, section, orientation, role=Qt.ItemDataRole.DisplayRole):
        if role != Qt.ItemDataRole.DisplayRole:
            return None
        if orientation == Qt.Orientation.Horizontal:
            return self._columns[section] if section < len(self._columns) else ""
        return str(section + 1)

    def data(self, index, role=Qt.ItemDataRole.DisplayRole):
        if not index.isValid():
            return None
        row = self._rows[index.row()]
        column = self._columns[index.column()]
        raw = row[index.column()]
        if role == RAW:
            return raw
        if role == COLUMN:
            return column
        if role == Qt.ItemDataRole.DisplayRole:
            return describe(raw)
        if role == Qt.ItemDataRole.ToolTipRole:
            if raw is None:
                return "NULL"
            if isinstance(raw, (bytes, bytearray, memoryview)):
                return f"blob, {len(raw)} bytes"
            if isinstance(raw, int) and abs(raw) > 10 ** 12:
                return f"{raw} (epoch ms)"
            return str(raw)[:400]
        if role == Qt.ItemDataRole.ForegroundRole:
            if raw is None:
                return QBrush(_NULL)
            if column == "classification" and str(raw) in _BADGES:
                return QBrush(_BADGES[str(raw)])
        if role == Qt.ItemDataRole.FontRole:
            if raw is None or (column == "classification" and str(raw) in _BADGES):
                return _BOLD
        return None


class TableEditor(QWidget):
    """Data grid (read-only) + DDL + statistics for one table."""

    def __init__(self, vault, table: str, on_value_selected=None,
                 on_fk_open=None, on_open_files=None, parent=None):
        super().__init__(parent)
        self.vault = vault
        self.table = table
        self.on_value_selected = on_value_selected
        self.on_fk_open = on_fk_open
        self.on_open_files = on_open_files
        self.grid = Grid(vault.conn, table)
        self.offset = 0
        self.total = 0
        self.order_by = None
        self.order_desc = False
        self.exact = None

        # ---- data tab
        self.filter_edit = QLineEdit()
        self.filter_edit.setPlaceholderText(
            'Filter: plain text = LIKE, or column = value (e.g. classification = S)')
        self.filter_edit.returnPressed.connect(self._apply_filter)
        apply_btn = QPushButton("Apply")
        clear_btn = QPushButton("Clear")
        apply_btn.clicked.connect(self._apply_filter)
        clear_btn.clicked.connect(self._clear_filter)

        self.model = TableModel([], [])
        self.view = QTableView()
        self.view.setModel(self.model)
        self.view.setAlternatingRowColors(True)
        self.view.setSelectionBehavior(QTableView.SelectionBehavior.SelectItems)
        self.view.clicked.connect(self._cell_clicked)
        self.view.doubleClicked.connect(self._row_opened)
        self.view.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.view.customContextMenuRequested.connect(self._context_menu)
        header = self.view.horizontalHeader()
        header.sectionClicked.connect(self._sort_clicked)
        header.setStretchLastSection(True)

        self.prev_btn = QPushButton("◀ Prev")
        self.next_btn = QPushButton("Next ▶")
        self.page_label = QLabel("")
        self.page_label.setObjectName("SectionHint")
        self.prev_btn.clicked.connect(self._page_prev)
        self.next_btn.clicked.connect(self._page_next)

        filter_row = QHBoxLayout()
        filter_row.addWidget(QLabel("Filter"))
        filter_row.addWidget(self.filter_edit, 1)
        filter_row.addWidget(apply_btn)
        filter_row.addWidget(clear_btn)
        pager = QHBoxLayout()
        pager.addWidget(self.prev_btn)
        pager.addWidget(self.next_btn)
        pager.addWidget(self.page_label, 1)

        data_widget = QWidget()
        data_layout = QVBoxLayout(data_widget)
        data_layout.setContentsMargins(4, 4, 4, 4)
        data_layout.addLayout(filter_row)
        data_layout.addWidget(self.view, 1)
        data_layout.addLayout(pager)

        # ---- ddl + stats
        self.ddl = QPlainTextEdit()
        self.ddl.setReadOnly(True)
        self.ddl.setObjectName("MonoView")
        self.stats = QTableWidget(0, 2)
        self.stats.setHorizontalHeaderLabels(["Item", "Value"])
        self.stats.horizontalHeader().setStretchLastSection(True)
        self.stats.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)

        self.tabs = QTabWidget()
        self.tabs.addTab(data_widget, "Data")
        self.tabs.addTab(self.ddl, "DDL")
        self.tabs.addTab(self.stats, "Stats")

        layout = QVBoxLayout(self)
        layout.setContentsMargins(4, 4, 4, 4)
        layout.addWidget(self.tabs, 1)

        self.refresh()

    # ------------------------------------------------------------------ data

    def refresh(self):
        columns, rows, total = self.grid.fetch(
            filter_text=self.filter_edit.text() or None, exact=self.exact,
            order_by=self.order_by, order_desc=self.order_desc,
            offset=self.offset)
        self.model.set_rows(columns, rows)
        self.total = total
        self._update_pager()
        if not self.ddl.toPlainText():
            self._load_ddl()
        self._load_stats()

    def _update_pager(self):
        page = self.offset // self.grid.page_size + 1
        pages = max(1, (self.total + self.grid.page_size - 1) // self.grid.page_size)
        self.page_label.setText(
            f"Rows {self.offset + 1}–{min(self.offset + self.grid.page_size, self.total)} "
            f"of {self.total}   ·   page {page}/{pages}")
        self.prev_btn.setEnabled(self.offset > 0)
        self.next_btn.setEnabled(self.offset + self.grid.page_size < self.total)

    def _page_prev(self):
        self.offset = max(0, self.offset - self.grid.page_size)
        self.refresh()

    def _page_next(self):
        if self.offset + self.grid.page_size < self.total:
            self.offset += self.grid.page_size
            self.refresh()

    def _apply_filter(self):
        self.exact = None
        self.offset = 0
        self.refresh()

    def _clear_filter(self):
        self.filter_edit.clear()
        self.exact = None
        self.offset = 0
        self.refresh()

    def set_fk_filter(self, column, value):
        self.filter_edit.setText(f"{column} = {value}")
        self.exact = (column, value)
        self.offset = 0
        self.refresh()

    def _sort_clicked(self, section: int):
        if section < 0 or section >= len(self.grid.columns):
            return
        name = self.grid.columns[section]
        if self.order_by == name:
            self.order_desc = not self.order_desc
        else:
            self.order_by, self.order_desc = name, False
        self.offset = 0
        self.refresh()

    def _cell_clicked(self, index: QModelIndex):
        if self.on_value_selected is None or not index.isValid():
            return
        column = self.model.data(index, COLUMN)
        row = self._row_dict(index.row())
        self.on_value_selected(self.table, column, row)

    def _row_dict(self, row_index: int):
        if row_index >= len(self.model._rows):
            return {}
        return {name: self.model._rows[row_index][i]
                for i, name in enumerate(self.grid.columns)}

    def _row_opened(self, index: QModelIndex):
        row = self._row_dict(index.row())
        if row:
            RowViewer(self.table, row, self).show()

    # ---------------------------------------------------------- context menu

    def _context_menu(self, pos):
        index = self.view.indexAt(pos)
        if not index.isValid():
            return
        column = self.model.data(index, COLUMN)
        value = self.model.data(index, RAW)
        menu = QMenu(self)
        menu.addAction("Inspect in value panel",
                       lambda: self._cell_clicked(index))
        if isinstance(value, (bytes, bytearray, memoryview)):
            blob = bytes(value)
            menu.addAction("Copy hex", lambda: self._copy(blob.hex()))
            menu.addAction("Copy Crockford Base32", lambda: self._copy(base32(blob)))
        else:
            menu.addAction("Copy value", lambda: self._copy("" if value is None else str(value)))
        menu.addSeparator()
        for fk in catalog.foreign_keys(self.vault.conn, self.table):
            if fk["from_col"] == column:
                menu.addAction(
                    f"Go to {fk['table']}.{fk['to_col']} = {describe(value)}",
                    lambda t=fk["table"], c=fk["to_col"], v=value:
                    self.on_fk_open(t, c, v))
        if self.table == "t_encrypted_data" and column == "dek_id":
            menu.addAction("Go to t_data_encrypt_key.id = value",
                           lambda: self.on_fk_open("t_data_encrypt_key", "id", value))
        if self.table == "t_password" and column == "encrypted_data_id":
            menu.addAction("Go to t_encrypted_data.id = value",
                           lambda: self.on_fk_open("t_encrypted_data", "id", value))
        if self.table == "t_file" and column == "storage_name":
            menu.addAction("Open in Files: attachments",
                           lambda: self.on_open_files("attachments"))
        if menu.actions():
            menu.exec(self.view.viewport().mapToGlobal(pos))

    @staticmethod
    def _copy(text: str):
        from PyQt6.QtGui import QGuiApplication
        QGuiApplication.clipboard().setText(text)

    # ------------------------------------------------------------ ddl / stats

    def _load_ddl(self):
        self.ddl.setPlainText(catalog.ddl(self.vault.conn, self.table))

    def _load_stats(self):
        rows = []
        for info in catalog.table_info(self.vault.conn, self.table):
            rows.append((f"column {info['name']}",
                         f"{info['type'] or '?'}"
                         + (" NOT NULL" if info["notnull"] else "")
                         + (f" default {info['default']}" if info["default"] is not None else "")
                         + (" PK" if info["pk"] else "")))
        for fk in catalog.foreign_keys(self.vault.conn, self.table):
            rows.append(("fk", f"{fk['from_col']} -> {fk['table']}.{fk['to_col']}"))
        for idx in catalog.indexes(self.vault.conn, self.table):
            rows.append(("index", f"{idx['name']} (unique)" if idx["unique"] else idx["name"]))
        rows.append(("row count", str(self.total)))
        self.stats.setRowCount(len(rows))
        for i, (key, value) in enumerate(rows):
            self.stats.setItem(i, 0, QTableWidgetItem(str(key)))
            self.stats.setItem(i, 1, QTableWidgetItem(str(value)))

    # ------------------------------------------------------------ properties

    def properties(self):
        rows = [
            ("Table", self.table),
            ("Rows", str(self.total)),
            ("Filter", self.filter_edit.text() or "—"),
            ("Order", f"{self.order_by or '—'} {'DESC' if self.order_desc else ''}".strip()),
        ]
        for fk in catalog.foreign_keys(self.vault.conn, self.table):
            rows.append(("FK", f"{fk['from_col']} -> {fk['table']}"))
        if self.table == "t_password":
            rows.append(("Payload", "locked (Android fingerprint)"))
        return rows


class RowViewer(QDialog):
    """Vertical, form-like view of one row."""

    def __init__(self, table, row: dict, parent=None):
        super().__init__(parent)
        self.setWindowTitle(f"{table} — row detail")
        self.resize(640, 480)
        listing = QTableWidget(0, 2)
        listing.setHorizontalHeaderLabels(["Column", "Value"])
        listing.horizontalHeader().setStretchLastSection(True)
        listing.verticalHeader().setVisible(False)
        listing.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        preview = QPlainTextEdit()
        preview.setObjectName("MonoView")
        preview.setReadOnly(True)

        def show_cell(row_index, _column):
            raw = listing.item(row_index, 1).data(Qt.ItemDataRole.UserRole)
            blob = raw if isinstance(raw, (bytes, bytearray, memoryview)) else None
            preview.setPlainText(describe(raw) if blob is None
                                 else f"{describe(raw)}\n\n{hex_dump(blob[:2048])}")

        for name, value in row.items():
            i = listing.rowCount()
            listing.insertRow(i)
            listing.setItem(i, 0, QTableWidgetItem(name))
            item = QTableWidgetItem(describe(value))
            item.setData(Qt.ItemDataRole.UserRole, value)
            listing.setItem(i, 1, item)
        listing.currentCellChanged.connect(show_cell)
        listing.setCurrentCell(0, 1)

        layout = QVBoxLayout(self)
        splitter = QSplitter(Qt.Orientation.Vertical)
        splitter.addWidget(listing)
        splitter.addWidget(preview)
        layout.addWidget(splitter)
