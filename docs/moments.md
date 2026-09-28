# Cryptowl — Moments Feature Design (Confidential tier)

Personal moments timeline (朋友圈-style) inside the encrypted vault. This document
specifies the feature's data model, media encryption, visibility model, and the
import path from WeChat SNS exports. It builds **on top of** the encryption core
in [design.md](design.md) / [migrations/v1__init.sql](migrations/v1__init.sql) — it does not re-implement
the key hierarchy.

> **Supersedes the earlier self-contained `moments.sql` draft** ("moments
> edition", re-implementing the vault plumbing). The feature now uses the
> canonical core: one SQLCipher database per vault, wrapped keys per
> `v1__init.sql`, C-tier files encrypted with FEK.

## 1. Overview & Goals

Moments is a private, local-only timeline of personal posts (text, photos,
videos, links, locations — whatever the user wants to keep, including imported
WeChat Moments history). Think "personal diary with media", rendered like
WeChat Moments.

Goals:

- **Confidential (C) tier only** — everything is readable after vault unlock
  (master password or biometric remember-me). No per-access prompts, no
  secondary password. The at-rest boundary is SQLCipher (L0/L1 data) plus FEK
  for files.
- **Encrypted everywhere on disk** — no plaintext media files, ever.
- **Importable from WeChat SNS exports** — one-time migration of the existing
  archive (2418 posts, ~3300 media items, comments, likes) with idempotent
  re-import (dedup by `source_id`).
- **Future: AI virtual friends** — on-device AI agents that may view moments
  the owner chooses to share, with per-friend grants and a redaction scope.
  The schema models this now; enforcement is app-layer.

Non-goals (v1):

- Social networking, cloud sync, sharing outside the device.
- S/T classification of moments (the `classification` column and
  `encrypted_data_id` pointers exist for a future escalation path but no
  moment is created above C today).
- Live fetching of WeChat data — import is archive-based, one-way.

## 2. Security Classification

| Item | Tier | At rest | Unlock condition |
| --- | --- | --- | --- |
| Moment text, comments, card titles, location, media metadata | L0/L1 (C) | SQLCipher (VaultKey) | vault unlock |
| Media files, thumbnails, card covers, avatars | C files | whole-file/chunked AES-256-GCM with **FEK** = `HKDF-SHA256(VaultKey, info="file")` | vault unlock |
| (future) Escalated moments | S/T | per-item DEK via `t_data_encrypt_key` / `t_encrypted_data` | per-access auth |

Consequences:

- **No `t_data_encrypt_key` rows at C tier.** Per the canonical design, C-tier
  items share the DB's key material; files use FEK. This is a deliberate
  deviation from the *old* moments draft, which gave every media item its own
  random DEK ("vault" wrapping) — a pattern the canonical hierarchy reserves
  for S/T.
- Because FEK is derived from the unwrapped VaultKey, **final media encryption
  must happen inside the app at import time** (see §6) — a desktop tool cannot
  pre-encrypt C-tier media with the correct key.
- AAD on every AEAD operation = the media row id (`CHAR(36)`, ASCII), per the
  canonical anti-ciphertext-swap rule.

## 3. Data Model

All tables follow the canonical conventions (see `v1__init.sql`): `CHAR(36)`
UUID primary keys, `INTEGER` epoch-millisecond timestamps (UTC), soft delete
via `deleted_at`, `PRAGMA foreign_keys = ON`. The full DDL is
[migrations/v2__moments.sql](migrations/v2__moments.sql); the summary here documents intent.

```
t_moment (post)
 ├── 1:N t_moment_card        rich cards: link / video_link / finder / music /
 │                            mini_program / live / note
 ├── 1:N t_moment_media       photos / videos / audio, ordered by sort_order
 ├── 1:N t_moment_comment     reply threading via parent_id (self-FK)
 ├── 1:N t_moment_like        PK (moment_id, author_username)
 └── M:N t_friend             share grants via t_moment_share
```

### `t_moment`

The post itself. `type` drives rendering (mirrors WeChat content types so
imports map 1:1): `text`, `media`, `link`, `location`, `music`, `note`,
`mini_program`, `live`.

- `content` — plaintext column at C tier (SQLCipher is the boundary);
  `encrypted_data_id` stays NULL unless a moment is later escalated to S/T.
- `author_name` / `author_username` / `author_avatar_filename` — who posted.
  For imported archives the author is usually the vault owner (the WeChat
  account); non-owner posts (e.g. shares) keep their original author info.
- `location` — L0 JSON `{"lat","lng","poi_name"}`.
- `visibility` — `'private'` (仅自己可见) or `'friends'` (visible to virtual
  friends); maps from WeChat's `is_private`. Semantics in §5.
- `source_id` — UNIQUE; the original WeChat feed id. This is the idempotent
  re-import key (`INSERT OR IGNORE` on conflict).
- `source_created_at` — original timeline timestamp (epoch ms after
  conversion; WeChat exports seconds) for chronological ordering.
- `like_count` / `comment_count` — denormalized, kept in sync by triggers.

### `t_moment_card`

Rich-card payload for link/video-link/finder(视频号)/music/mini-program/live/
note posts. Same shape for all card types; finder cards additionally carry the
channel's `author_name`/`author_avatar_filename`. `extra` is a JSON escape
hatch (e.g. mini-program `appid`/`path`). `thumb_filename` points to an
FEK-encrypted cover image. Cards are metadata — nothing secret beyond L0/L1.

### `t_moment_media`

One row per photo/video/audio item, `sort_order` preserving the original
gallery order.

- `filename` — `<uuid>.cwo` in `<vault>/attachments/` (always encrypted; the
  format is in §4). No plaintext `original_name` path is ever stored — only
  the original file name for display.
- `width`/`height`/`duration_ms` — rendering hints without reading the file.
- `thumbnail_filename` — FEK-encrypted thumbnail in `<vault>/thumbnails/`
  (same key as the original per the canonical file-tier rule). Videos use
  whole-file GCM here since a thumbnail is always an image.
- No `dek_id`: C tier has no per-item DEKs (see §2). Media metadata is L0/L1
  inside SQLCipher.

### `t_moment_comment`

Reply threading via `parent_id` (NULL = top-level). WeChat "withdrawn"
comments are imported as `deleted_at` rows (soft-delete, keeping threading).

### `t_moment_like`

PK `(moment_id, author_username)` — one like per user per moment, matching
WeChat semantics. Denormalized counters live on `t_moment` and are maintained
by triggers (in `v2__moments.sql`).

### `t_friend` + `t_moment_share` (virtual friends, future)

Friends are named entities — today only placeholders, tomorrow on-device AI
agents that chat about shared moments.

- `t_friend`: `kind IN ('ai','human')`, display `name`, and for AI agents a
  `model_id` (local model) + `role_prompt` (persona, L1 text). `is_active`
  gates whether the friend may access anything.
- `t_moment_share`: the only grant mechanism. PK `(friend_id, moment_id)`,
  with `scope IN ('full','redacted')` and `granted_at`/`revoked_at` for
  explicit share/revoke overrides (see §5).

## 4. Media File Format (CWO1)

Media files are self-describing encrypted blobs; all keys/parameters live in
the file header, so the DB needs only `filename` + rendering hints. Two
layouts (defined in the import tool, `wechat_sns_export/migrate_moments.py`,
and re-implemented in the app):

```
whole-file (images, thumbnails, covers):
  header 22 B  = b"CWO1" | u16 version(1) | u32 chunk_size(0) | nonce(12)
  payload      = AES-256-GCM(FEK, data, AAD = media row id) || tag(16)

chunked (video/audio):
  header 22 B  = b"CWO1" | u16 version(1) | u32 chunk_size(65536)
                 | u64 chunk_count | iv_prefix(4)
  record N     = AES-256-GCM(FEK, chunk N, nonce = u64(N) || iv_prefix) || tag(16)
```

- Nonce uniqueness is guaranteed by random per-file nonce (whole-file) or
  random `iv_prefix` (chunked) — never reused with the same FEK.
- Random access: record `N` starts at `22 + N * (chunk_size + 16)`; videos can
  be streamed/scrubbed without reading the whole file or DB lookups.
- `chunk_size = 0` marks whole-file mode; versioning lives in the header for
  forward compatibility.
- Thumbnails are whole-file mode with the same FEK (AAD = media row id), even
  for video items.

## 5. Visibility & AI Virtual Friends

### Semantics

- `visibility = 'private'`: only the owner. A friend can still be granted
  access via an explicit `t_moment_share` row.
- `visibility = 'friends'`: every **active** friend can view by default.
  `t_moment_share` rows act as overrides:
  - `revoked_at` set → that friend is excluded (shadow-ban per moment);
  - `scope = 'redacted'` → friend views the redacted projection (below).

Effective access for a friend = `is_active(friend) AND NOT revoked AND
(visibility='friends' OR explicit grant)`.

### Redaction

`scope = 'redacted'` means the friend sees the moment with PII and sensitive
fields removed: no `location`, no author identity (only "me"), card URLs
stripped, media limited to thumbnails unless `t_moment_share` says otherwise
in a future revision. Redaction is applied in-memory by the app when
assembling the view for the friend; the DB records only the *policy* (the
`scope` value). A future `t_moment_share.redact_mask` (bitmask over fields)
can refine this without schema churn — the column is reserved via the CHECK
on `scope`.

### AI friend access flow (future)

The vault runs an on-device LLM (the app already ships a local-model chat
stack). When an AI friend is "invited" to view a moment:

1. App assembles the effective projection (redacted if `scope='redacted'`)
   entirely in memory — moments are C tier, so they are already decrypted at
   vault unlock; the friend never triggers any key operation.
2. The projection is passed to the local model in-process; nothing is logged,
   exported, or written to disk.
3. The friend's replies are stored as regular moments/comments authored by
   `t_friend.id` (kind = 'ai'), so the timeline stays one consistent model.

No schema work is needed to *run* this; the tables above already model the
policy. Enforcement is 100% app-layer — this is a personal vault, not a
multi-party system, so the DB cannot be the trust boundary.

## 6. Import from WeChat SNS (desktop migration)

Source: `wechat_sns_export` produces `sns_export.json` (posts with media
metadata, comments, likes) and decrypted media under `media_decrypted/`.
The migration runs **on the desktop** into a vault that was copied off the
Android device (or created with the desktop tooling), then the vault is
copied back:

```
Android vault ──copy──▶ local `vaultlib/` (opens with the master password)
                         └─ migrate_moments.py ──▶ CWO1 files + DB rows
                          copy back ──▶ Android
```

The "copy back" step is the in-app **Restore from backup** flow
(`vault/VaultBackup.kt`, `RestoreScreen.kt`): the user copies the vault
folder (vault.meta, vault.db, config.json, config.sig, device_secret,
`attachments/`, `thumbnails/`) to the device (adb push / file manager),
picks it with a SAF folder picker, the app copies it into
`<filesDir>/vaults/<id>` (staging dir → validate → atomic swap), and the
Unlock screen then opens it — the first unlock re-binds a desktop
`device_secret` to the Android Keystore. "Back up vault" on the Home
screen is the inverse direction (a timestamped subdirectory of a picked
tree).

`migrate_moments.py` (`wechat_sns_export/`) is the reference implementation
of this feature — it is byte-exact with the design and doubles as the
cross-verification oracle for the Android implementation (`vaultlib/` holds
the fixed test vectors):

1. Open the vault with the master password (meta mac, config.sig, wrapped
   `vault_key:smk` unwrap, SQLCipher raw-key verify) → derive FEK.
2. Create the moments schema (`v2__moments.sql`, idempotent `IF NOT EXISTS`).
3. Per post: deterministic IDs `uuid5(N, "cryptowl:<scope>:<key>")`,
   `source_id` = WeChat feed id (dedup key; re-import is a no-op),
   `source_created_at` converted seconds → ms, `is_private` →
   `visibility` (`private`/`friends`).
4. Per media item: stream the plaintext file → CWO1 (FEK, AAD = media row
   id) → `attachments/<media_id>.cwo`; thumbnail →
   `thumbnails/<media_id>_t.cwo` (same FEK). Comments (two-pass for reply
   threading) and likes are inserted after their moment.

Result on this machine: 2418 moments, 1040 cards, 2780 media, 7497 comments,
6715 likes; ~960 MB of FEK-encrypted media + 25 MB thumbnails.

### Mapping summary

| WeChat export | t_moment |
| --- | --- |
| `id` | `source_id` (UNIQUE) |
| `content_type` + finder/link flags | `type` (text/media/link/location/music/note/mini_program/live) |
| `content_desc` | `content` |
| `create_time` (seconds) | `source_created_at` (ms) |
| `is_private` | `visibility` (`private` / `friends`) |
| `location` | `location` (JSON) |
| `username`/`nickname` | `author_username`/`author_name` |
| `media[]` | `t_moment_media` (+ `t_moment_card` for finder/link covers) |
| `comments[]` (`ref_comment_id` threading) | `t_moment_comment` (`parent_id`) |
| `likes[]` | `t_moment_like` |

## 7. Schema & Change Log

- Schema: [migrations/v2__moments.sql](migrations/v2__moments.sql) — feature tables only, built against
  `v1__init.sql` conventions.
- **vs. the old moments draft** (superseded): dropped the self-contained
  vault plumbing (`t_vault`, re-implemented `t_wrapped_key`/`t_data_encrypt_key`
  shapes, `DATETIME DEFAULT CURRENT_TIMESTAMP`), removed per-media C-tier
  DEKs and BLOB thumbnails (FEK files instead), added `visibility`,
  `t_friend`, `t_moment_share`, and normalized all timestamps to epoch ms.

## 8. Next Steps

Done (implementation status):

2. Android first-open import flow — **done**: "Restore from backup" (SAF,
   `vault/VaultBackup.kt`) + `UnlockService` re-bind (`vault_key:smk`
   re-wrapped with the Android Keystore Device Secret, `device_secret`
   deleted, `config.sig` + `vault.meta` mac re-signed).
3. Moments timeline UI (Compose, WeChat-album layout like the reference
   frontend in `wechat_sns_export/frontend`) — **done**: paged timeline
   (50 posts per page, children fetched in batched `IN` queries — an
   imported archive is thousands of posts), decrypted-thumbnail LruCache,
   full-screen image viewer on tap.

Remaining:

1. AI friend: `t_friend` CRUD + share sheet; later, local-model viewing.
2. Video playback (CWO1 chunked random access via `decryptChunkAt` → local
   temp file → player), audio items.
3. Moment writes (create post with photos, comment, like), friend shares
   (§5 redacted projection).
