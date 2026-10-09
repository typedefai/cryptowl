# Unified Item Model — vault format v3

The devkit is the **reference implementation**; the Android app re-implements
this spec. Format v3 starts from scratch: it replaces the per-feature tables
(`t_note`, `t_password`, `t_moment`, `t_file` + external attachments) with one
unified item model, a plugin type system, embedded file storage, and built-in
version history.

## Goals

1. **One data structure** — all user data is an *item*; types (note, env,
   password, photo, …) are plugins over the same table.
2. **One database file** — small files (photos, documents) are embedded in
   `vault.db`; external files only past a size threshold.
3. **One UI** — navigator/detail/menus are generated from the type registry.
4. **History** — every save is an immutable version; text types get diffs
   (the `.env.prod` case).

## Non-goals

- Changing the key hierarchy or bootstrap files (see *What stays*).
- Branching/merging (git mechanics). History is linear per item.

## What stays (format v2 core)

- Key chain: `P = HMAC(DeviceSecret, password)` → TMK (Argon2id) → SMK →
  wrapped VaultKey (SQLCipher raw key). FEK = `HKDF(VaultKey, info="file")`.
- Bootstrap files stay **outside** the DB — chicken-and-egg: `vault.meta`
  (salts, wrapped key) and `config.json`/`config.sig` are needed before the
  DB key exists. A vault folder is 4 small files + `vault.db` + optional
  `attachments/`.
- Tier semantics: **C** = SQLCipher is the boundary; **S/T** = per-item DEK
  (`t_encrypted_data` + `t_data_encrypt_key`), unwrapped per access.
- Soft delete (`deleted_at`), `secure_delete = ON`, ms-integer timestamps,
  TEXT uuid ids.

`vault.meta` `version` becomes **3**; readers refuse `version > 3`.

## Schema (single canonical `schema.sql`)

No versioned migrations, no `PRAGMA user_version`: one idempotent file
(`CREATE … IF NOT EXISTS`), canonical at `docs/schema.sql`, byte-mirrored in
the devkit (and later the Android assets); schema changes edit this file in
place on all sides. It is executed at vault creation and ensured (idempotent)
when a repository first touches the DB.

```sql
CREATE TABLE t_item (
    id                TEXT    NOT NULL PRIMARY KEY,
    type              TEXT    NOT NULL,              -- plugin type id ('folder' = container)
    title             TEXT    NOT NULL DEFAULT '',
    classification    CHAR(1) NOT NULL DEFAULT 'C'
                      CHECK (classification IN ('C', 'S', 'T')),
    parent_id         TEXT    REFERENCES t_item (id),  -- NULL = root; tree of items
    pinned            INTEGER NOT NULL DEFAULT 0,    -- pinned folders appear in the sidebar
    content           TEXT,                           -- C-tier payload (SQLCipher boundary)
    encrypted_data_id TEXT    REFERENCES t_encrypted_data (id),  -- S/T payload
    meta              TEXT,                           -- JSON, L1: never secrets
    created_at        INTEGER NOT NULL,
    updated_at        INTEGER NOT NULL,
    deleted_at        INTEGER
);
CREATE INDEX index_t_item_list ON t_item (type, deleted_at, pinned DESC, updated_at DESC);
CREATE INDEX index_t_item_parent ON t_item (parent_id, deleted_at);

CREATE TABLE t_file (
    id            TEXT    NOT NULL PRIMARY KEY,
    item_id       TEXT    NOT NULL REFERENCES t_item (id) ON DELETE CASCADE,
    name          TEXT,                                 -- original filename
    mime_type     TEXT,
    size_bytes    INTEGER NOT NULL,                      -- plaintext size
    storage       TEXT    NOT NULL DEFAULT 'embedded'
                  CHECK (storage IN ('embedded', 'external')),
    content       BLOB,                                  -- embedded envelope (below)
    thumbnail     BLOB,                                  -- always embedded, same key
    external_name TEXT,                                  -- external: attachments/<id>
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL,
    deleted_at    INTEGER
);
CREATE INDEX index_t_file_item ON t_file (item_id);

CREATE TABLE t_item_version (
    id                TEXT    NOT NULL PRIMARY KEY,
    item_id           TEXT    NOT NULL REFERENCES t_item (id) ON DELETE CASCADE,
    seq               INTEGER NOT NULL,                  -- 1..N per item, gaps allowed
    title             TEXT,
    content           TEXT,                              -- snapshot of the C payload
    encrypted_data_id TEXT    REFERENCES t_encrypted_data (id),  -- S/T snapshot
    meta              TEXT,
    message           TEXT,                              -- optional commit message (L1, like title)
    fingerprint       TEXT    NOT NULL,                  -- HMAC (below)
    created_at        INTEGER NOT NULL,
    UNIQUE (item_id, seq)
);
CREATE INDEX index_t_item_version_item ON t_item_version (item_id, seq DESC);
```

`t_wrapped_key`, `t_data_encrypt_key`, `t_encrypted_data` (tier plumbing) are
kept from the v1 core.

## Payload routing (tier security is preserved)

- **C tier** (`note`, `env`, C-tier `photo`): payload in `t_item.content`
  (plaintext inside SQLCipher) — fast, no per-item crypto.
- **S/T tier** (`password`, …): payload is a per-item DEK row in
  `t_encrypted_data` referenced by `encrypted_data_id`; the DEK is wrapped by
  the KEK. On Android the KEK is BioKey-wrapped (per-access prompt); on the
  devkit a **dev binding** wraps the KEK with the SMK (`t_wrapped_key`
  role=`kek`, wrapper=`smk`) — documented dev-only convenience, since desktops
  have no fingerprint gate.
- `meta` is non-secret, L1 metadata (URL, username, dimensions, environment
  name). Anything sensitive goes in the payload.

## File storage: embedded by default

- Envelope (one format, two storages):
  `envelope = nonce(12) || ciphertext || tag(16)`, AES-256-GCM,
  key = FEK (C tier) or the item's DEK (S/T), AAD = file id (thumbnails:
  AAD = `"<file_id>:thumb"`).
- `size_bytes` (plaintext) ≤ `embed_max_bytes` (default **512 KiB**, from
  `config.json`) → envelope in `t_file.content`; larger → same envelope in
  `attachments/<file_id>`, `external_name` set. Storage choice is made once at
  write time; readers branch on `storage`.
- Thumbnails always embedded (`t_file.thumbnail`), same key as the original.
- Backup/transfer = copy 4 files + `vault.db` (+ `attachments/` only if
  large media exists).
- Housekeeping: after mass deletes run `PRAGMA secure_delete`-verified
  `VACUUM` to reclaim blob pages.

## Folders (Explorer-style)

- A folder is `t_item` with `type = 'folder'` and no payload; the hierarchy is
  `parent_id` (NULL = root). No second table, uniform pin/history/rename.
- Moving a folder into its own descendant is rejected (walk ancestors).
- **Cascade soft delete**: deleting a folder stamps `deleted_at` on the whole
  subtree with one shared timestamp (the batch id); restoring would restore
  exactly that batch (`WHERE deleted_at = <stamp>`). Moves/renames are not
  versioned; folder title changes are (same fingerprint rule as items).
- Sidebar: `Pinned` (pinned folders), `All Items` (the root tree), `Types`
  (virtual filters: Plain, Password, Photo, Video). Type stays an attribute,
  not a location.
- Smart queries: children of a folder (folders first, then pinned, then name),
  flat by-type lists, pinned folders, title/content search (content only for
  C-tier rows; S/T payloads cannot be searched without unlocking).

## History (git UX, not git mechanics)

- **On every save**: compute
  `fingerprint = HMAC-SHA256(HFK, title \0 classification \0 payload)` where
  `HFK = HKDF-SHA256(VaultKey, salt='', info='fingerprint', 32)`. If it equals
  the latest version's fingerprint, no version row is written.
  (HMAC, not SHA-256: raw digests of `.env` contents would allow dictionary
  attacks against captured vaults.)
- **Optional commit message** (MediaWiki-style edit summary): a save may carry
  a short message; it is stored on the version row and shown in the history
  list. It is *not* part of the fingerprint, and it is L1 data (inside
  SQLCipher, like `title`) — never put secrets in it. Unchanged saves write no
  version row, so a message alone records nothing.
- Versions are **immutable**; restoring writes the old snapshot as a new
  `seq` (audit trail stays linear), with the message defaulting to
  `restored from seq N` (editable before saving).
- Text types diff at the UI layer (`difflib`); binary types show
  "unchanged/changed" (photos: thumbnail pairs, later).
- Retention: keep last `history_keep` versions per item (default **100**, from
  `config.json`); older rows purged right after append. Purged rows rely on
  `secure_delete = ON`. History follows the item's tier: S/T version payloads
  stay DEK-encrypted.

## Type registry (plugins)

Planned plugin set (built-in types):

| type | tier | payload | files | diff | notes |
| --- | --- | --- | --- | --- | --- |
| `plain` | C | pure text | – | text | **default**; anything textual — `.env.prod`, config snippets, notes |
| `password` | S | encrypted JSON (username/url/secret/notes) | – | field-level (later) | devkit dev binding: KEK wrapped by SMK |
| `photo` | C (T later) | – | 1 embedded + thumbnail | binary (thumbnails) | always embedded (small) |
| `video` | C (T later) | – | 1 external (usually) + poster frame | binary | chunked envelope for streaming (spec with the video phase) |

Implementation order: **plain → password → photo → video** (plain first because
it exercises the whole core: items, versioning, messages, UI registry).

The devkit registry entry provides: `type_id`, display name + icon, default
classification, `supports_files`, an editor widget factory, and `diffable`.
Core CRUD (`ItemRepository`) is generic and type-agnostic:
`list(type) / get / create / update (auto-version) / set_pinned / soft_delete /
versions / restore / files`. New types ship as a plugin module — no schema
change unless they need new columns.

## UI (Windows 10+ Explorer style, generated from the registry)

Single-window browser; no dock/tab workspace:

- **Ribbon tabs** on top (like Word/Excel): **File** (New/Open vault, Recent,
  Lock, Exit), **Home** (New ▾, Rename, Pin, Delete, History, Refresh),
  **Manage** (Overview, Key chain, SQLCipher command, vault.meta/config/
  device_secret, About).
- **Address bar**: Back arrow (go to parent folder), an editable path input
  (`/`, `/folder/item.txt`, or `@Plain` for a type view; Enter navigates/opens,
  Esc restores), a **name filter** (filters the current path in place — no
  whole-vault search), and the icons/list/details view switch. Item paths use
  the type's extension (plain → `.txt`).
- **Sidebar** (left): one icon tree rooted at **This vault** — pinned folder
  shortcuts, type filters (Plain, Password, Photo, Video), then the folder
  tree.
- **Content pane** (right): children of the current selection with the three
  view modes; folders first; double-click enters a folder, opens an item.
- **Editors are separate popup windows**, one per open item (non-modal), with
  title + plugin body + optional commit message + Save/Delete/Pin/History.
- Keyboard: Alt+Up back, Ctrl+F filter, F2 rename, Del delete, Ctrl+H history.
- Icons: Microsoft Fluent UI System Icons (MIT, `assets/icons/`, license
  included), folders tinted Explorer-yellow — Windows 11 look (the exact
  Explorer artwork is proprietary).
- Properties: item metadata + type info.

## Implementation plan (devkit first)

1. **P1 core**: drop vendored v1–v4; canonical `docs/schema.sql` + devkit
   mirror; `vault.meta` v3; `ItemRepository` (+ versioning, fingerprints,
   commit messages, retention) with tests. *(done)*
2. **P2 plain plugin**: type registry; generic detail page; `plain` editor
   (title + text + optional commit message). *(done as tabs; superseded by P2b)*
2b. **P2b folders + browser**: `parent_id` + cascade batches + tree ops;
   Explorer-style single window (sidebar / content / popup editors) replacing
   the tab/dock workspace. Schema edits recreate dev vaults (no migrations).
3. **P3 history UI**: version list with messages, text diff, restore,
   retention config.
4. **P4 password plugin**: DEK/KEK path with dev SMK wrap (S tier).
5. **P5 photo plugin**: envelope codec, embedded storage + thumbnails.
6. **P6 video plugin**: chunked envelope, external storage, streaming.

Android re-bases on this file after P1–P2 stabilize; its `SchemaApplier`
becomes the same idempotent `schema.sql` ensure.
