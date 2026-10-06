from __future__ import annotations

"""Tab editors: overview, key chain, text/JSON viewer, table viewer."""

import json

from PySide6.QtCore import Qt
from PySide6.QtGui import QFontDatabase
from PySide6.QtWidgets import (QAbstractItemView, QFormLayout, QHeaderView,
                               QLabel, QPlainTextEdit, QTableWidget,
                               QTableWidgetItem, QVBoxLayout, QWidget)

from ..vault import Vault, key_fingerprint

MAX_TABLE_ROWS = 200
MAX_COLUMN_WIDTH = 320


def _mono() -> object:
    return QFontDatabase.systemFont(QFontDatabase.SystemFont.FixedFont)


def _readonly_item(text: str) -> QTableWidgetItem:
    item = QTableWidgetItem(text)
    item.setFlags(Qt.ItemFlag.ItemIsEnabled | Qt.ItemFlag.ItemIsSelectable)
    return item


class OverviewEditor(QWidget):
    """Summary of the opened vault."""

    def __init__(self, vault: Vault, parent=None):
        super().__init__(parent)
        title = QLabel(vault.name)
        title.setStyleSheet("font-size: 18px; font-weight: 600;")

        form = QFormLayout()
        rows = [
            ("Vault id", vault.vault_id),
            ("Path", vault.path),
            ("Schema version", str(vault.schema_version)),
            ("Tables", ", ".join(vault.tables)),
            ("Metadata version", str(vault.meta.get("version", ""))),
            ("Config", json.dumps(vault.config, ensure_ascii=False,
                                  sort_keys=True)),
            ("VaultKey fingerprint", vault.vault_key_fingerprint),
            ("FEK fingerprint", vault.fek_fingerprint),
        ]
        for name, value in rows:
            label = QLabel(str(value))
            label.setTextInteractionFlags(
                Qt.TextInteractionFlag.TextSelectableByMouse)
            label.setWordWrap(True)
            form.addRow(name, label)

        layout = QVBoxLayout(self)
        layout.addWidget(title)
        layout.addLayout(form)
        layout.addStretch(1)
        layout.setContentsMargins(20, 16, 20, 16)


class KeyChainEditor(QWidget):
    """P -> TMK -> SMK -> VaultKey -> FEK, with live bytes."""

    def __init__(self, vault: Vault, parent=None):
        super().__init__(parent)
        note = QLabel("Live key material — developer view; do not share or log.")
        note.setStyleSheet("color: #9a6700;")

        self.table = QTableWidget()
        self.table.setColumnCount(4)
        self.table.setHorizontalHeaderLabels(
            ["Step", "Bytes (hex)", "Fingerprint", "Formula"])
        self.table.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.table.setSelectionBehavior(
            QAbstractItemView.SelectionBehavior.SelectItems)
        self.table.setAlternatingRowColors(True)
        self.table.verticalHeader().setVisible(False)

        chain = vault.key_chain()
        self.table.setRowCount(len(chain))
        mono = _mono()
        for row, (name, value, formula) in enumerate(chain):
            name_item = _readonly_item(name)
            hex_item = _readonly_item(value.hex())
            hex_item.setFont(mono)
            hex_item.setToolTip(value.hex())
            fp_item = _readonly_item(key_fingerprint(value))
            fp_item.setFont(mono)
            formula_item = _readonly_item(formula)
            formula_item.setToolTip(formula)
            self.table.setItem(row, 0, name_item)
            self.table.setItem(row, 1, hex_item)
            self.table.setItem(row, 2, fp_item)
            self.table.setItem(row, 3, formula_item)

        header = self.table.horizontalHeader()
        header.setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
        header.setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        header.setSectionResizeMode(2, QHeaderView.ResizeMode.ResizeToContents)
        header.setSectionResizeMode(3, QHeaderView.ResizeMode.Stretch)

        layout = QVBoxLayout(self)
        layout.addWidget(note)
        layout.addWidget(self.table)
        layout.setContentsMargins(12, 12, 12, 12)


class TextViewer(QWidget):
    """Read-only monospace text (JSON, hex dumps, ...)."""

    def __init__(self, text: str, parent=None):
        super().__init__(parent)
        self.edit = QPlainTextEdit()
        self.edit.setReadOnly(True)
        self.edit.setFont(_mono())
        self.edit.setLineWrapMode(QPlainTextEdit.LineWrapMode.NoWrap)
        self.edit.setPlainText(text)
        layout = QVBoxLayout(self)
        layout.addWidget(self.edit)
        layout.setContentsMargins(8, 8, 8, 8)


class TableViewer(QWidget):
    """First 200 rows of a vault table; blobs are shown by size."""

    def __init__(self, vault: Vault, table: str, parent=None):
        super().__init__(parent)
        total = vault.db.row_count(table)
        columns = [
            row[1] for row in
            vault.db.query(f'PRAGMA table_info("{table}")')
        ]
        rows = vault.db.query(f'SELECT * FROM "{table}" LIMIT {MAX_TABLE_ROWS}')

        label = QLabel(f"{table} — {total} rows"
                       + (f" (showing {MAX_TABLE_ROWS})" if total > MAX_TABLE_ROWS
                          else ""))
        label.setStyleSheet("font-weight: 600;")

        grid = QTableWidget()
        grid.setColumnCount(len(columns))
        grid.setRowCount(len(rows))
        grid.setHorizontalHeaderLabels(columns)
        grid.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        grid.setSelectionBehavior(
            QAbstractItemView.SelectionBehavior.SelectItems)
        grid.setAlternatingRowColors(True)
        for r, row in enumerate(rows):
            for c, value in enumerate(row):
                item = _readonly_item(_render(value))
                item.setToolTip(str(value)[:500])
                grid.setItem(r, c, item)
        grid.resizeColumnsToContents()
        for c in range(len(columns)):
            grid.setColumnWidth(c, min(grid.columnWidth(c), MAX_COLUMN_WIDTH))

        layout = QVBoxLayout(self)
        layout.addWidget(label)
        layout.addWidget(grid)
        layout.setContentsMargins(12, 12, 12, 12)


def _render(value) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, (bytes, bytearray)):
        return f"<blob {len(value)} B>"
    return str(value)
