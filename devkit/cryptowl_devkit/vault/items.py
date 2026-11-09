from __future__ import annotations

"""Unified item model (format v3) — see docs/items.md.

All user data lives in `t_item` (a tree: `parent_id`, folders are
`type='folder'`); types are plugins (the registry lives in the UI layer).
Payload routing by classification (docs/design.md):

    C — plaintext inside SQLCipher (`content`)
    S — per-item DEK wrapped by the KEK (`encrypted_data_id`)
    T — per-item DEK wrapped by the TopSecretKEK (secondary password)

History fingerprints: HMAC-SHA256(HFK, title \\0 classification \\0 payload)
with HFK = HKDF-SHA256(VaultKey, salt='', info='fingerprint', 32). An HMAC (not
a raw digest) keeps captured vaults safe from dictionary attacks on contents.
"""

import json
import time
import uuid
from dataclasses import dataclass

from .crypto import hkdf_sha256, hmac_sha256
from .sqlcipher import SqlCipherDatabase
from .tiers import SecondaryPasswordNotSet, TierCrypto
from .vault import Vault, verify_schema

HISTORY_KEEP = 100
FOLDER_TYPE = "folder"
CLASSIFICATIONS = ("C", "S", "T")

_ITEM_COLUMNS = ("id, type, title, classification, parent_id, pinned, "
                 "content, encrypted_data_id, meta, created_at, updated_at")


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
    locked: bool = False          # T tier without the secondary password


@dataclass
class ItemDraft:
    """Editor draft; `message` is the optional commit message."""

    type: str
    title: str = ""
    content: str = ""
    classification: str = "C"
    parent_id: str | None = None
    meta: dict | None = None
    pinned: bool = False
    message: str = ""


@dataclass(frozen=True)
class ItemVersion:
    id: str
    seq: int
    classification: str
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
    """Generic item CRUD + folders + version history + tier encryption."""

    def __init__(self, vault: Vault, history_keep: int = HISTORY_KEEP):
        self.vault = vault
        self.db: SqlCipherDatabase = vault.db
        self.history_keep = history_keep
        self.tiers = TierCrypto(vault)
        self._fingerprint_key = hkdf_sha256(vault.vault_key, b"",
                                            b"fingerprint", 32)
        verify_schema(self.db)

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
        return [self._summary(r) for r in self.db.query(sql, params)]

    def list(self, item_type: str | None = None) -> list[ItemSummary]:
        """Flat list across all folders (by type, or all types)."""
        sql = f"SELECT {_ITEM_COLUMNS} FROM t_item WHERE deleted_at IS NULL"
        params: tuple = ()
        if item_type is not None:
            sql += " AND type = ?"
            params = (item_type,)
        sql += (" ORDER BY (type = 'folder') DESC, pinned DESC, "
                "updated_at DESC, id DESC")
        return [self._summary(r) for r in self.db.query(sql, params)]

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
        """Title search; content is plaintext only for C-tier rows."""
        pattern = f"%{_escape_like(query)}%"
        sql = (f"SELECT {_ITEM_COLUMNS} FROM t_item "
               "WHERE deleted_at IS NULL "
               "AND (title LIKE ? ESCAPE '\\' OR content LIKE ? ESCAPE '\\')")
        params: tuple = (pattern, pattern)
        if item_type is not None:
            sql += " AND type = ?"
            params += (item_type,)
        sql += " ORDER BY (type = 'folder') DESC, pinned DESC, updated_at DESC"
        return [self._summary(r) for r in self.db.query(sql, params)]

    def get(self, item_id: str,
            ts_password: bytes | None = None) -> Item | None:
        row = self._row(item_id)
        if row is None:
            return None
        return self._item_from_row(row, ts_password)

    def _item_from_row(self, row,
                       ts_password: bytes | None = None) -> Item:
        (_id, item_type, title, classification, parent_id, pinned, content,
         encrypted_data_id, meta, created_at, updated_at) = row
        locked = False
        if classification == "C":
            payload = content or ""
        elif ts_password is None and classification == "T":
            payload, locked = "", True
        else:
            payload = self.tiers.decrypt_payload(
                encrypted_data_id, classification, ts_password)
        return Item(_id, item_type, title, classification, parent_id,
                    bool(pinned), payload, _parse_json(meta),
                    created_at, updated_at, locked)

    # -- writes -------------------------------------------------------------

    def create(self, draft: ItemDraft,
               ts_password: bytes | None = None) -> str:
        self._validate_classification(draft.classification)
        item_id = str(uuid.uuid4())
        now = _now_ms()
        content, encrypted_data_id = self._payload_for(
            draft.content, draft.classification, ts_password, None)
        fingerprint = self._fingerprint(draft.title, draft.classification,
                                        draft.content)
        self.db.execute(
            "INSERT INTO t_item (id, type, title, classification, parent_id, "
            "pinned, content, encrypted_data_id, meta, created_at, updated_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (item_id, draft.type, draft.title, draft.classification,
             draft.parent_id, 1 if draft.pinned else 0, content,
             encrypted_data_id, _dump_json(draft.meta), now, now))
        self._append_version(item_id, 1, draft, fingerprint, now)
        return item_id

    def create_folder(self, title: str,
                      parent_id: str | None = None) -> str:
        return self.create(ItemDraft(type=FOLDER_TYPE, title=title,
                                     parent_id=parent_id,
                                     message="folder created"))

    def update(self, item_id: str, draft: ItemDraft,
               ts_password: bytes | None = None) -> bool:
        """Update the item; append a version only when content changed.

        Returns True when a new version row was written.
        """
        self._validate_classification(draft.classification)
        row = self._row(item_id)
        if row is None:
            raise KeyError(f"no such item: {item_id}")
        old_classification = row[3]
        old_encrypted_data_id = row[7]

        existing_dek_id = None
        if draft.classification == old_classification:
            existing_dek_id = self.tiers.item_dek_id(old_encrypted_data_id)
        content, encrypted_data_id = self._payload_for(
            draft.content, draft.classification, ts_password, existing_dek_id)
        fingerprint = self._fingerprint(draft.title, draft.classification,
                                        draft.content)
        now = _now_ms()
        self.db.execute(
            "UPDATE t_item SET title = ?, classification = ?, content = ?, "
            "encrypted_data_id = ?, meta = ?, pinned = ?, updated_at = ? "
            "WHERE id = ?",
            (draft.title, draft.classification, content, encrypted_data_id,
             _dump_json(draft.meta), 1 if draft.pinned else 0, now, item_id))
        if fingerprint == self._latest_fingerprint(item_id):
            self.tiers.gc()
            return False
        max_row = self.db.query_one(
            "SELECT MAX(seq) FROM t_item_version WHERE item_id = ?", (item_id,))
        seq = int(max_row[0] or 0) + 1
        self._append_version(item_id, seq, draft, fingerprint, now)
        self._purge(item_id)
        self.tiers.gc()
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
            "SELECT id, seq, classification, title, message, fingerprint, "
            "created_at FROM t_item_version WHERE item_id = ? "
            "ORDER BY seq DESC", (item_id,))
        return [ItemVersion(r[0], int(r[1]), r[2], r[3] or "", r[4] or "",
                            r[5], r[6]) for r in rows]

    def restore(self, version_id: str,
                ts_password: bytes | None = None) -> str | None:
        """Write the version's snapshot as a new version; returns the item id."""
        rows = self.db.query(
            "SELECT item_id, seq, classification, title, content, "
            "encrypted_data_id, meta FROM t_item_version WHERE id = ?",
            (version_id,))
        if not rows:
            return None
        (item_id, seq, classification, title, content,
         encrypted_data_id, meta) = rows[0]
        current = self._row(item_id)
        if current is None:
            return None
        if classification == "C":
            payload = content or ""
        else:
            if classification == "T" and ts_password is None:
                raise SecondaryPasswordNotSet(
                    "the secondary password is required for Top Secret items")
            payload = self.tiers.decrypt_payload(
                encrypted_data_id, classification, ts_password)
        self.update(item_id, ItemDraft(
            type=current[1], title=title or "", content=payload,
            classification=classification, meta=_parse_json(meta),
            pinned=bool(current[5]),
            message=f"restored from seq {seq}"), ts_password=ts_password)
        return item_id

    # -- internals ----------------------------------------------------------

    def _row(self, item_id: str):
        return self.db.query_one(
            f"SELECT {_ITEM_COLUMNS} FROM t_item "
            "WHERE id = ? AND deleted_at IS NULL", (item_id,))

    @staticmethod
    def _summary(row) -> ItemSummary:
        return ItemSummary(row[0], row[1], row[2], row[3], bool(row[5]),
                           row[9], row[10])

    def _payload_for(self, plaintext: str, classification: str,
                     ts_password: bytes | None,
                     existing_dek_id: str | None):
        """Returns (content, encrypted_data_id) for a write."""
        if classification == "C":
            return plaintext, None
        encrypted_data_id = self.tiers.encrypt_for_tier(
            plaintext, classification, ts_password, existing_dek_id)
        return None, encrypted_data_id

    @staticmethod
    def _validate_classification(classification: str) -> None:
        if classification not in CLASSIFICATIONS:
            raise ValueError(f"unknown classification: {classification}")

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
        row = self.db.query_one(
            "SELECT content, encrypted_data_id FROM t_item "
            "WHERE id = ?", (item_id,))
        content, encrypted_data_id = row if row else (None, None)
        self.db.execute(
            "INSERT INTO t_item_version (id, item_id, seq, classification, "
            "title, content, encrypted_data_id, meta, message, fingerprint, "
            "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (str(uuid.uuid4()), item_id, seq, draft.classification,
             draft.title, content, encrypted_data_id, _dump_json(draft.meta),
             draft.message or None, fingerprint, now))

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
