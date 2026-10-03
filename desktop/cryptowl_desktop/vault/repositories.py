from __future__ import annotations

"""Feature repositories over the vault's SQLCipher connection.

Mirrors the Android raw-SQL repositories (no ORM). Writes use bound parameters
(via `SqlCipherConnection.execute`) and epoch-millisecond timestamps, exactly
like the app. Confidential-tier rows are plaintext inside SQLCipher; S/T rows
reference `t_encrypted_data` whose DEK is wrapped by the (Android-Only) KEK and
cannot be decrypted on the desktop.
"""

import time
import uuid


def now_ms() -> int:
    return int(time.time() * 1000)


def new_uuid() -> str:
    return str(uuid.uuid4())


# ---------------------------------------------------------------------- notes

class NoteRepository:
    """`t_note` (v4): C-tier notes are plaintext (SQLCipher is the boundary)."""

    def __init__(self, conn):
        self.conn = conn

    def list(self, classification: str = "C") -> list:
        return self._query_list(classification)

    def _query_list(self, classification: str) -> list:
        rows = self._raw(
            """SELECT id, title, pinned, created_at, updated_at
               FROM t_note
               WHERE deleted_at IS NULL AND classification = ?
               ORDER BY pinned DESC, updated_at DESC, id DESC""",
            (classification,))
        return [dict(id=r[0], title=r[1] or "", pinned=bool(r[2]),
                     created_at=r[3], updated_at=r[4]) for r in rows]

    def get(self, note_id: str):
        rows = self._raw(
            """SELECT id, title, content, pinned, classification, encrypted_data_id,
                      created_at, updated_at
               FROM t_note WHERE id = ? AND deleted_at IS NULL""",
            (note_id,))
        if not rows:
            return None
        r = rows[0]
        return dict(id=r[0], title=r[1] or "", content=r[2] or "",
                    pinned=bool(r[3]), classification=r[4],
                    encrypted_data_id=r[5], created_at=r[6], updated_at=r[7])

    def create(self, title: str, content: str, pinned: bool = False) -> str:
        note_id = new_uuid()
        ts = now_ms()
        self.conn.execute(
            """INSERT INTO t_note (id, classification, title, content, pinned,
                                   created_at, updated_at)
               VALUES (?, 'C', ?, ?, ?, ?, ?)""",
            (note_id, title, content, 1 if pinned else 0, ts, ts))
        return note_id

    def update(self, note_id: str, title: str, content: str,
               pinned: bool = False) -> None:
        self.conn.execute(
            "UPDATE t_note SET title = ?, content = ?, pinned = ?, updated_at = ? "
            "WHERE id = ?",
            (title, content, 1 if pinned else 0, now_ms(), note_id))

    def set_pinned(self, note_id: str, pinned: bool) -> None:
        self.conn.execute(
            "UPDATE t_note SET pinned = ?, updated_at = ? WHERE id = ?",
            (1 if pinned else 0, now_ms(), note_id))

    def soft_delete(self, note_id: str) -> None:
        ts = now_ms()
        self.conn.execute(
            "UPDATE t_note SET deleted_at = ?, updated_at = ? WHERE id = ?",
            (ts, ts, note_id))

    def _raw(self, sql: str, params=()):
        """Bound SELECT (the vendored binding only parameter-binds writes)."""
        return _bound_query(self.conn, sql, params)


# ---------------------------------------------------------------------- media

class MediaRepository:
    """`t_file` (v1): C-tier files use the FEK and carry no DEK."""

    def __init__(self, conn):
        self.conn = conn

    def list(self, classification: str = "C", limit: int = 500) -> list:
        rows = _bound_query(
            self.conn,
            """SELECT id, classification, storage_name, original_name, mime_type,
                      size_bytes, dek_id, created_at, updated_at
               FROM t_file
               WHERE deleted_at IS NULL AND classification = ?
               ORDER BY created_at DESC, id DESC
               LIMIT ?""",
            (classification, limit))
        return [self._item(r) for r in rows]

    def get(self, file_id: str):
        rows = _bound_query(
            self.conn,
            """SELECT id, classification, storage_name, original_name, mime_type,
                      size_bytes, dek_id, created_at, updated_at
               FROM t_file WHERE id = ? AND deleted_at IS NULL""",
            (file_id,))
        return self._item(rows[0]) if rows else None

    def insert(self, *, file_id: str, storage_name: str, original_name: str,
               mime_type: str, size_bytes: int, classification: str = "C") -> None:
        ts = now_ms()
        self.conn.execute(
            """INSERT INTO t_file (id, dek_id, classification, storage_name,
                                   original_name, mime_type, size_bytes,
                                   created_at, updated_at)
               VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?)""",
            (file_id, classification, storage_name, original_name, mime_type,
             size_bytes, ts, ts))

    def soft_delete(self, file_id: str) -> None:
        ts = now_ms()
        self.conn.execute(
            "UPDATE t_file SET deleted_at = ?, updated_at = ? WHERE id = ?",
            (ts, ts, file_id))

    @staticmethod
    def _item(r) -> dict:
        return dict(id=r[0], classification=r[1], storage_name=r[2],
                    original_name=r[3], mime_type=r[4], size_bytes=r[5],
                    dek_id=r[6], created_at=r[7], updated_at=r[8])


# ------------------------------------------------------------------ passwords

class PasswordRepository:
    """`t_password` (v3): S-tier metadata is readable; the payload is not."""

    def __init__(self, conn):
        self.conn = conn

    def list(self, limit: int = 500) -> list:
        rows = _bound_query(
            self.conn,
            """SELECT id, classification, title, encrypted_data_id,
                      created_at, updated_at
               FROM t_password
               WHERE deleted_at IS NULL
               ORDER BY updated_at DESC, id DESC
               LIMIT ?""",
            (limit,))
        return [dict(id=r[0], classification=r[1], title=r[2],
                     encrypted_data_id=r[3], created_at=r[4], updated_at=r[5])
                for r in rows]


# -------------------------------------------------------------------- moments

class MomentsRepository:
    """`t_moment` + children (v2): read-only timeline for the desktop viewer."""

    def __init__(self, conn):
        self.conn = conn

    def timeline(self, limit: int = 500) -> list:
        rows = _bound_query(
            self.conn,
            """SELECT id, type, author_name, author_username, content, location,
                      visibility, source_created_at, like_count, comment_count
               FROM t_moment
               WHERE deleted_at IS NULL
               ORDER BY source_created_at DESC, created_at DESC, id DESC
               LIMIT ?""",
            (limit,))
        posts = [dict(id=r[0], type=r[1], author_name=r[2],
                      author_username=r[3], content=r[4], location=r[5],
                      visibility=r[6], source_created_at=r[7],
                      like_count=r[8], comment_count=r[9],
                      media=[], comments=[], likes=[]) for r in rows]
        for post in posts:
            post["media"] = self._media(post["id"])
            post["comments"] = self._comments(post["id"])
            post["likes"] = self._likes(post["id"])
        return posts

    def _media(self, moment_id: str) -> list:
        rows = _bound_query(
            self.conn,
            """SELECT id, media_type, filename, original_name, mime_type,
                      width, height, thumbnail_filename
               FROM t_moment_media
               WHERE deleted_at IS NULL AND moment_id = ?
               ORDER BY sort_order""",
            (moment_id,))
        return [dict(id=r[0], media_type=r[1], filename=r[2],
                     original_name=r[3], mime_type=r[4], width=r[5],
                     height=r[6], thumbnail_filename=r[7]) for r in rows]

    def _comments(self, moment_id: str) -> list:
        rows = _bound_query(
            self.conn,
            """SELECT id, parent_id, author_name, author_username, content, created_at
               FROM t_moment_comment
               WHERE deleted_at IS NULL AND moment_id = ?
               ORDER BY created_at""",
            (moment_id,))
        return [dict(id=r[0], parent_id=r[1], author_name=r[2],
                     author_username=r[3], content=r[4], created_at=r[5])
                for r in rows]

    def _likes(self, moment_id: str) -> list:
        rows = _bound_query(
            self.conn,
            """SELECT author_name, author_username FROM t_moment_like
               WHERE moment_id = ?""",
            (moment_id,))
        return [dict(author_name=r[0], author_username=r[1]) for r in rows]


# ----------------------------------------------------------------------- misc

def table_counts(conn) -> dict:
    counts = {}
    for (name,) in conn.query(
            "SELECT name FROM sqlite_master WHERE type='table' "
            "AND name NOT LIKE 'sqlite_%' ORDER BY name"):
        try:
            counts[name] = conn.row_count(name)
        except Exception:  # noqa: BLE001 - FTS shadow tables etc.
            counts[name] = -1
    return counts


def _bound_query(conn, sql: str, params=()):
    """SELECT with bound parameters — mirrors SqlCipherConnection._row."""
    import ctypes

    from . import sqlcipher_db as dbmod

    stmt = dbmod._STMT()
    rc = dbmod._lib.sqlite3_prepare_v2(conn._db, sql.encode("utf-8"), -1,
                                       ctypes.byref(stmt), None)
    if rc != dbmod.SQLITE_OK:
        raise dbmod.SqlCipherError(f"sqlite3_prepare_v2: {conn.errmsg()}")
    try:
        for index, value in enumerate(params, start=1):
            conn._bind(stmt, index, value)
        rows = []
        while True:
            step = dbmod._lib.sqlite3_step(stmt)
            if step == dbmod.SQLITE_ROW:
                rows.append(conn._row(stmt))
            elif step == dbmod.SQLITE_DONE:
                break
            else:
                raise dbmod.SqlCipherError(f"sqlite3_step: {conn.errmsg()}")
        return rows
    finally:
        dbmod._lib.sqlite3_finalize(stmt)
