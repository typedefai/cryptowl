from __future__ import annotations

"""Generic read-only data grid over any vault table (Qt-free).

Paged SELECT with a global text filter or an exact column filter, plus safe
identifier handling (table/columns validated before interpolation).
"""

import re

from .sqlcipher_db import SqlCipherError

_NAME_RE = re.compile(r"^[A-Za-z0-9_]+$")


def _safe(name: str) -> str:
    if not _NAME_RE.match(name or ""):
        raise ValueError(f"unsafe identifier: {name!r}")
    return name


class Grid:

    def __init__(self, conn, table: str, page_size: int = 100):
        self.conn = conn
        self.table = table
        self.page_size = page_size
        self.columns = self.columns()

    # ------------------------------------------------------------------ meta

    def columns(self) -> list:
        rows = self.conn.query2(f"PRAGMA table_info({_safe(self.table)})")[1]
        return [r[1] for r in rows]

    def type_of(self, column: str) -> str:
        rows = self.conn.query2(f"PRAGMA table_info({_safe(self.table)})")[1]
        for r in rows:
            if r[1] == column:
                return r[2] or ""
        return ""

    # ------------------------------------------------------------------ data

    def fetch(self, filter_text: str | None = None, exact=None,
              order_by: str | None = None, order_desc: bool = False,
              offset: int = 0) -> tuple:
        """Returns (columns, rows, total) for one page.

        `filter_text` parses as `col = value` (exact match) when the column
        exists, else acts as a global LIKE over every column. `exact` is an
        explicit (column, value) override used by FK navigation.
        """
        where_sql, params = self._where(filter_text, exact)
        order_sql = ""
        if order_by:
            if order_by not in self.columns:
                raise ValueError(f"unknown column {order_by!r} in {self.table}")
            order_sql = f' ORDER BY "{_safe(order_by)}" ' + ("DESC" if order_desc else "ASC")
        total = self._count(where_sql, params)
        sql = (f'SELECT * FROM "{_safe(self.table)}"{where_sql}{order_sql} '
               f"LIMIT {int(self.page_size)} OFFSET {int(offset)}")
        columns, rows = self.conn.query2(sql, params)
        return columns, rows, total

    def one(self, exact) -> tuple | None:
        col, value = exact
        columns, rows = self.conn.query2(
            f'SELECT * FROM "{_safe(self.table)}" WHERE "{_safe(col)}" = ? LIMIT 1',
            (value,))
        return rows[0] if rows else None

    # --------------------------------------------------------------- helpers

    def _count(self, where_sql: str, params) -> int:
        _, rows = self.conn.query2(
            f'SELECT COUNT(*) FROM "{_safe(self.table)}"{where_sql}', params)
        return int(rows[0][0]) if rows else 0

    def _filter_clause(self, filter_text: str | None, exact):
        if exact is not None:
            col, value = exact
            if col not in self.columns:
                raise ValueError(f"unknown column {col!r} in {self.table}")
            return f' WHERE "{_safe(col)}" = ?', [value]
        if not filter_text:
            return "", []
        parsed = parse_filter(filter_text)
        if parsed is not None and parsed[0] in self.columns:
            col, value = parsed
            return f' WHERE "{_safe(col)}" = ?', [value]
        needle = f"%{filter_text}%"
        clauses, params = [], []
        for col in self.columns:
            clauses.append(f'CAST("{_safe(col)}" AS TEXT) LIKE ?')
            params.append(needle)
        return " WHERE " + " OR ".join(clauses), params

    def _where(self, filter_text: str | None, exact):
        try:
            return self._filter_clause(filter_text, exact)
        except ValueError as exc:
            raise SqlCipherError(str(exc)) from exc


def parse_filter(text: str):
    """`col = value` -> exact (col, value); otherwise -> LIKE text (or None)."""
    text = (text or "").strip()
    if not text:
        return None
    if "=" in text:
        col, _, value = text.partition("=")
        col, value = col.strip(), value.strip().strip("'\"")
        if col and value and _NAME_RE.match(col):
            return (col, value)
    return None
