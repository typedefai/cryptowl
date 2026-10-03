from __future__ import annotations

"""Schema catalog for the navigator and table editors (Qt-free).

Every table/column name is validated against `^[A-Za-z0-9_]+$` before it is
interpolated into PRAGMA SQL (bound parameters do not work there).
"""

import re


_NAME = re.compile(r"^[A-Za-z0-9_]+$")


def _safe(name: str) -> str:
    if not _NAME.match(name or ""):
        raise ValueError(f"unsafe identifier: {name!r}")
    return name


def user_version(conn) -> int:
    row = conn.query_one("PRAGMA user_version")
    return int(row[0]) if row else 0


def tables(conn) -> list:
    """All user tables/views with live row counts and DDL, name-sorted."""
    result = []
    _, rows = conn.query2(
        "SELECT name, type, sql FROM sqlite_master "
        "WHERE type IN ('table', 'view') AND name NOT LIKE 'sqlite_%' "
        "ORDER BY name")
    for name, kind, ddl in rows:
        count = None
        if kind == "table":
            try:
                count = conn.row_count(name)
            except Exception:  # noqa: BLE001 - shadow tables etc.
                count = -1
        result.append(dict(name=name, type=kind, row_count=count, ddl=ddl or ""))
    return result


def table_names(conn) -> list:
    return [t["name"] for t in tables(conn)]


def table_info(conn, table: str) -> list:
    return [dict(cid=r[0], name=r[1], type=r[2] or "", notnull=r[3],
                 default=r[4], pk=r[5])
            for r in conn.query2(f"PRAGMA table_info({_safe(table)})")[1]]


def foreign_keys(conn, table: str) -> list:
    """Outgoing FKs: what a value in `from` column points at."""
    return [dict(id=r[0], seq=r[1], table=r[2], from_col=r[3], to_col=r[4])
            for r in conn.query2(f"PRAGMA foreign_key_list({_safe(table)})")[1]]


def indexes(conn, table: str) -> list:
    return [dict(seq=r[0], name=r[1], unique=r[2], origin=r[3], partial=r[4])
            for r in conn.query2(f"PRAGMA index_list({_safe(table)})")[1]]


def ddl(conn, table: str) -> str:
    _, rows = conn.query2(
        "SELECT sql FROM sqlite_master WHERE type IN ('table', 'view') AND name = ?",
        (table,))
    return rows[0][0] if rows else ""
