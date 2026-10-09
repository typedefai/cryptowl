from __future__ import annotations

"""Unified item model (format v3) — see docs/items.md.

All user data lives in `t_item` (a tree: `parent_id`, folders are
`type='folder'`); types are plugins (the registry lives in the UI layer).
This repository is type-agnostic: generic CRUD, folders, cascade soft delete,
search, and immutable version history with optional commit messages.

History fingerprint: HMAC-SHA256(HFK, title \\0 classification \\0 payload)
with HFK = HKDF-SHA256(VaultKey, salt='', info='fingerprint', 32). An HMAC (not
a raw digest) keeps captured vaults safe from dictionary attacks on contents.
"""

import json
import time
import uuid
from dataclasses import dataclass

from .crypto import hkdf_sha256, hmac_sha256
from .sqlcipher import SqlCipherDatabase
from .vault import Vault, ensure_schema

HISTORY_KEEP = 100
FOLDER_TYPE = "folder"

_ITEM_COLUMNS = ("id, type, title, classification, parent_id, pinned, "
                 "content, meta, created_at, updated_at")


@dataclass(frozen=True)
class ItemSummary:
    id: str
    type: str
    title: str
    classification: str
    pinned: bool
    created_at: int
    updated_at: int


@dataclass(frozen=True)
class Item:
    id: str
    type: str
    title: str
    classification: str
    parent_id: str | None
    pinned: bool
    content: str
    meta: dict
    created_at: int
    updated_at: int


@dataclass
class ItemDraft:
    """Editor draft; `message` is the optional commit message."""

    type: str
    title: str = ""
    content: str = ""
    parent_id: str | None = None
    meta: dict | None = None
    pinned: bool = False
    message: str = ""


@dataclass(frozen=True)
class ItemVersion:
    id: str
    seq: int
    title: str
    message: str
    fingerprint: str
    created_at: int


@dataclass(frozen=True)
class FolderInfo:
    id: str
    title: str
    parent_id: str | None
    pinned: bool


def _now_ms() -> int:
    return int(time.time() * 1000)


def _parse_json(text) -> dict:
    if not text:
        return {}
    try:
        return json.loads(text)
    except ValueError:
        return {}


def _dump_json(meta: dict | None) -> str | None:
    if not meta:
        return None
    return json.dumps(meta, sort_keys=True, separators=(",", ":"))


def _escape_like(text: str) -> str:
    return (text.replace("\\", "\\\\")
            .replace("%", "\\%").replace("_", "\\_"))


class ItemRepository:
    """Generic item CRUD + folders + version history."""

    def __init__(self, vault: Vault, history_keep: int = HISTORY_KEEP):
        self.vault = vault
        self.db: SqlCipherDatabase = vault.db
        self.history_keep = history_keep
        self._fingerprint_key = hkdf_sha256(vault.vault_key, b"",
                                            b"fingerprint", 32)
        ensure_schema(self.db)

    # -- queries ------------------------------------------------------------

    def children(self, parent_id: str | None = None,
                 item_type: str | None = None) -> list[ItemSummary]:
        """Children of a folder (folders first, then pinned, then name)."""
        sql = (f"SELECT {_ITEM_COLUMNS} FROM t_item "
               "WHERE deleted_at IS NULL AND parent_id IS ?")
        params: tuple = (parent_id,)
        if item_type is not None:
            sql += " AND type = ?"
            params += (item_type,)
        sql += (" ORDER BY (type = 'folder') DESC, pinned DESC, "
                "title COLLATE NOCASE, id")
        return [ItemSummary(r[0], r[1], r[2], r[3], bool(r[5]), r[8], r[9])
                for r in self.db.query(sql, params)]

    def list(self, item_type: str | None = None) -> list[ItemSummary]:
        """Flat list across all folders (by type, or all types)."""
        sql = (f"SELECT {_ITEM_COLUMNS} FROM t_item WHERE deleted_at IS NULL")
        params: tuple = ()
        if item_type is not None:
            sql += " AND type = ?"
            params = (item_type,)
        sql += " ORDER BY (type = 'folder') DESC, pinned DESC, updated_at DESC, id DESC"
        return [ItemSummary(r[0], r[1], r[2], r[3], bool(r[5]), r[8], r[9])
                for r in self.db.query(sql, params)]

    def counts(self) -> dict[str, int]:
        rows = self.db.query(
            "SELECT type, COUNT(*) FROM t_item WHERE deleted_at IS NULL "
            "GROUP BY type")
        return {r[0]: int(r[1]) for r in rows}

    def folders(self) -> list[FolderInfo]:
        rows = self.db.query(
            "SELECT id, title, parent_id, pinned FROM t_item "
            "WHERE deleted_at IS NULL AND type = ? "
            "ORDER BY title COLLATE NOCASE", (FOLDER_TYPE,))
        return [FolderInfo(r[0], r[1], r[2], bool(r[3])) for r in rows]

    def pinned_folders(self) -> list[FolderInfo]:
        return [f for f in self.folders() if f.pinned]

    def breadcrumb(self, item_id: str) -> list[FolderInfo]:
        """Path from the root down to (and including) `item_id`."""
        trail: list[FolderInfo] = []
        node: str | None = item_id
        for _ in range(64):  # cycle guard
            if node is None:
                break
            row = self.db.query_one(
                "SELECT id, title, parent_id, pinned FROM t_item "
                "WHERE id = ? AND deleted_at IS NULL", (node,))
            if row is None:
                break
            trail.append(FolderInfo(row[0], row[1], row[2], bool(row[3])))
            node = row[2]
        return list(reversed(trail))

    def search(self, query: str, item_type: str | None = None) -> list[ItemSummary]:
        """Title/content search (content only exists for C-tier rows)."""
        pattern = f"%{_escape_like(query)}%"
        sql = (f"SELECT {_ITEM_COLUMNS} FROM t_item "
               "WHERE deleted_at IS NULL "
               "AND (title LIKE ? ESCAPE '\\' OR content LIKE ? ESCAPE '\\')")
        params: tuple = (pattern, pattern)
        if item_type is not None:
            sql += " AND type = ?"
            params += (item_type,)
        sql += " ORDER BY (type = 'folder') DESC, pinned DESC, updated_at DESC"
        return [ItemSummary(r[0], r[1], r[2], r[3], bool(r[5]), r[8], r[9])
                for r in self.db.query(sql, params)]

    def get(self, item_id: str) -> Item | None:
        rows = self.db.query(
            f"SELECT {_ITEM_COLUMNS} FROM t_item "
            "WHERE id = ? AND deleted_at IS NULL", (item_id,))
        if not rows:
            return None
        r = rows[0]
        return Item(r[0], r[1], r[2], r[3], r[4], bool(r[5]), r[6] or "",
                    _parse_json(r[7]), r[8], r[9])

    # -- writes -------------------------------------------------------------

    def create(self, draft: ItemDraft) -> str:
        item_id = str(uuid.uuid4())
        now = _now_ms()
        fingerprint = self._fingerprint(draft.title, "C", draft.content)
        self.db.execute(
            "INSERT INTO t_item (id, type, title, classification, parent_id, "
            "pinned, content, meta, created_at, updated_at) "
            "VALUES (?, ?, ?, 'C', ?, ?, ?, ?, ?, ?)",
            (item_id, draft.type, draft.title, draft.parent_id,
             1 if draft.pinned else 0, draft.content,
             _dump_json(draft.meta), now, now))
        self._append_version(item_id, 1, draft, fingerprint, now)
        return item_id

    def create_folder(self, title: str,
                      parent_id: str | None = None) -> str:
        return self.create(ItemDraft(type=FOLDER_TYPE, title=title,
                                     parent_id=parent_id,
                                     message="folder created"))

    def update(self, item_id: str, draft: ItemDraft) -> bool:
        """Update the item; append a version only when content changed.

        Returns True when a new version row was written.
        """
        current = self.get(item_id)
        if current is None:
            raise KeyError(f"no such item: {item_id}")
        now = _now_ms()
        fingerprint = self._fingerprint(draft.title, current.classification,
                                        draft.content)
        self.db.execute(
            "UPDATE t_item SET title = ?, content = ?, meta = ?, pinned = ?, "
            "updated_at = ? WHERE id = ?",
            (draft.title, draft.content, _dump_json(draft.meta),
             1 if draft.pinned else 0, now, item_id))
        if fingerprint == self._latest_fingerprint(item_id):
            return False
        row = self.db.query_one(
            "SELECT MAX(seq) FROM t_item_version WHERE item_id = ?", (item_id,))
        seq = int(row[0] or 0) + 1
        self._append_version(item_id, seq, draft, fingerprint, now)
        self._purge(item_id)
        return True

    def move(self, item_id: str, parent_id: str | None) -> None:
        """Reparent an item/folder; rejects moves into own descendants."""
        if parent_id is not None:
            if parent_id == item_id:
                raise ValueError("cannot move an item into itself")
            node = parent_id
            while node is not None:
                if node == item_id:
                    raise ValueError("cannot move an item into its descendant")
                row = self.db.query_one(
                    "SELECT parent_id FROM t_item WHERE id = ?", (node,))
                node = row[0] if row else None
        self.db.execute(
            "UPDATE t_item SET parent_id = ?, updated_at = ? WHERE id = ?",
            (parent_id, _now_ms(), item_id))

    def set_pinned(self, item_id: str, pinned: bool) -> None:
        self.db.execute(
            "UPDATE t_item SET pinned = ?, updated_at = ? WHERE id = ?",
            (1 if pinned else 0, _now_ms(), item_id))

    def soft_delete(self, item_id: str) -> int:
        """Cascade soft delete of the subtree; returns the batch timestamp."""
        now = _now_ms()
        self.db.execute(
            """WITH RECURSIVE sub(id) AS (
                   SELECT id FROM t_item WHERE id = ?
                   UNION ALL
                   SELECT i.id FROM t_item i JOIN sub ON i.parent_id = sub.id
               )
               UPDATE t_item SET deleted_at = ?, updated_at = ?
               WHERE id IN (SELECT id FROM sub)""",
            (item_id, now, now))
        return now

    def restore_deleted(self, item_id: str) -> int:
        """Restore the delete batch that contains `item_id` (same timestamp)."""
        row = self.db.query_one(
            "SELECT deleted_at FROM t_item WHERE id = ?", (item_id,))
        if not row or row[0] is None:
            return 0
        stamp = row[0]
        count = self.db.query_one(
            "SELECT COUNT(*) FROM t_item WHERE deleted_at = ?", (stamp,))
        self.db.execute(
            "UPDATE t_item SET deleted_at = NULL, updated_at = ? "
            "WHERE deleted_at = ?", (_now_ms(), stamp))
        return int(count[0]) if count else 0

    # -- history ------------------------------------------------------------

    def versions(self, item_id: str) -> list[ItemVersion]:
        rows = self.db.query(
            "SELECT id, seq, title, message, fingerprint, created_at "
            "FROM t_item_version WHERE item_id = ? ORDER BY seq DESC",
            (item_id,))
        return [ItemVersion(r[0], int(r[1]), r[2] or "", r[3] or "",
                            r[4], r[5]) for r in rows]

    def restore(self, version_id: str) -> str | None:
        """Write the version's snapshot as a new version; returns the item id."""
        rows = self.db.query(
            "SELECT item_id, seq, title, content, meta FROM t_item_version "
            "WHERE id = ?", (version_id,))
        if not rows:
            return None
        item_id, seq, title, content, meta = rows[0]
        current = self.get(item_id)
        if current is None:
            return None
        self.update(item_id, ItemDraft(
            type=current.type, title=title or "", content=content or "",
            meta=_parse_json(meta), pinned=current.pinned,
            message=f"restored from seq {seq}"))
        return item_id

    # -- internals ----------------------------------------------------------

    def _fingerprint(self, title: str, classification: str,
                     content: str) -> str:
        payload = f"{title}\0{classification}\0{content}".encode("utf-8")
        return hmac_sha256(self._fingerprint_key, payload).hex()

    def _latest_fingerprint(self, item_id: str) -> str | None:
        row = self.db.query_one(
            "SELECT fingerprint FROM t_item_version WHERE item_id = ? "
            "ORDER BY seq DESC LIMIT 1", (item_id,))
        return row[0] if row else None

    def _append_version(self, item_id: str, seq: int, draft: ItemDraft,
                        fingerprint: str, now: int) -> None:
        self.db.execute(
            "INSERT INTO t_item_version (id, item_id, seq, title, content, "
            "meta, message, fingerprint, created_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (str(uuid.uuid4()), item_id, seq, draft.title, draft.content,
             _dump_json(draft.meta), draft.message or None, fingerprint, now))

    def _purge(self, item_id: str) -> None:
        if self.history_keep <= 0:
            return
        row = self.db.query_one(
            "SELECT MAX(seq) FROM t_item_version WHERE item_id = ?", (item_id,))
        cutoff = int(row[0] or 0) - self.history_keep
        if cutoff > 0:
            self.db.execute(
                "DELETE FROM t_item_version WHERE item_id = ? AND seq <= ?",
                (item_id, cutoff))
