# Cryptowl — Feature Roadmap

Everything not yet implemented, specified to be **implementable directly**: schema,
crypto chain (reusing existing primitives), files to create, routes, strings, and
tests. Work top-to-bottom inside a priority group; groups are independent unless
noted.

Status legend: ☐ todo · ◐ partially done (noted what remains) · ✅ implemented

## Implementation status (updated)

| Item | Status | Notes / decisions recorded during implementation |
| --- | --- | --- |
| F1 Notes | ✅ | `v4__notes.sql`, `NoteRepository`/`NoteViewModel`/`NotesUi` (markdown preview), routes, strings, `NoteRepositoryTest`. |
| F2 Change master password | ✅ | `MasterPasswordChange` rewraps `vault_key:smk` and re-signs **both** config.sig + meta mac. Known limitation: the two file renames are not one atomic unit (same as the desktop-rebind path); a crash between them leaves an unopenable vault. Journaling is a future hardening. |
| F3 Auto-lock / FLAG_SECURE | ✅ | `SettingsStore` (`cryptowl.settings`), `AutoLockPolicy` (+ JVM test), ProcessLifecycleOwner lock, timeout ticker, FLAG_SECURE window flag. Timeout-only options (no "off"). |
| F4 Media vault | ◐ | Import (images in memory, videos streamed two-pass) + encrypted thumbnails + grid + viewer + delete; `MediaServiceTest`. **Deferred:** custom CameraX capture screen (import via the photo picker only — avoids a plaintext capture file; the design's no-system-intent camera remains a follow-up). |
| F5 Video/audio playback | ◐ | media3 ExoPlayer + `Cwo1.decryptChunkedToFile` streaming decrypt to an app-private cache file, deleted on stop (documented moments.md §8 bridge). **Deferred:** a media3 `DataSource` fed by `decryptChunkAt` with no plaintext on disk. |
| F6 Top-Secret tier | ◐ | `TopSecretService` (TS-KEK via Argon2id, BioKey wrap, TopSecretKEK wrap), two-factor gate (fingerprint then secondary password, both verified), T-tier notes CRUD + UI, `TopSecretServiceTest`. **Deferred:** secondary-password change flow. |
| F7 Moment writes | ☐ | not started |
| F8 AI friends + share grants | ☐ | not started |
| F9 Recovery key + `.vbp` | ☐ | not started |
| F10 Multi-vault | ☐ | not started |
| F11 Password niceties | ☐ | not started |
| F12 Cleanups | ◐ | Settings language row wired; About dialog added (version + vault params). **Remaining:** dead-string/`StatusChip` cleanup, on-device `connectedDebugAndroidTest` run. |

Cross-cutting decisions:
- Shared decrypted-image cache moved to `ui/MediaLoader` and is **cleared when the shell is disposed** (lock/re-lock) so decrypted bitmaps never outlive a session.
- Top-Secret keys are per-action: the fingerprint factor holds TS-KEK only until the password dialog completes, then everything is wiped; saving from the editor re-runs the full gate.
- F2/F6 follow the existing `vault.meta` recalculation rules; no new on-disk formats were introduced for these features (F6 reuses `t_wrapped_key`/`t_data_encrypt_key`/`t_encrypted_data`).
.

## Conventions (apply to every item)

- **Migrations**: append `vN__<desc>.sql` to **both** `docs/migrations/` and
  `app/src/main/assets/migrations/` (byte-identical mirrors), comment-free
  (SchemaApplier splits on `;`), never edit old scripts. Next free version:
  **v4** (v3 = passwords).
- **Crypto**: reuse `KdfService.wrapKey/unwrapKey` (AAD = wrapped-key id),
  `ProtectedValue` end-to-end (no plaintext `ByteArray`/`String` lingering;
  `use {}` + `fill(0)`), `Cwo1` for files, `BioKeyManager` +
  `authenticateWithBiometric` (needs `negativeButtonText`) for prompts.
  Catch `Throwable` and `Log.e(TAG, "...", e)` before mapping to UI messages.
- **DB**: raw SQLCipher repositories — `rawQuery(sql, arrayOf(...))` (the
  `String[]` overload; never bind Ints via `?` in LIMIT/OFFSET — inline them),
  `execSQL(sql, arrayOf<Any>(...))` for writes, `beginTransaction()` for
  multi-row writes. No Room.
- **UI**: Compose, `nav/Routes.kt` routes, `PasswordsUi.kt`/`PasswordViewModel`
  as the pattern for fingerprint-gated CRUD, strings in **all three** locales
  (`values`, `values-zh-rCN`, `values-zh-rTW`).
- **Verify**: `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`
  plus `./gradlew :app:connectedDebugAndroidTest` with a connected device
  (JNI/Keystore/SQLCipher cannot run in JVM tests).

---

## P1 — Core vault gaps

### F1. Notes (Confidential tier CRUD) ☐

C tier: plaintext inside SQLCipher (L1), readable right after unlock — no
fingerprint, no KEK. `encrypted_data_id` is reserved for future S/T escalation
(same pattern as `t_moment`).

- **Migration `v4__notes.sql`**:

  ```sql
  PRAGMA foreign_keys = ON;
  PRAGMA secure_delete = ON;
  CREATE TABLE t_note (
      id                CHAR(36) NOT NULL PRIMARY KEY,
      classification    CHAR(1)  NOT NULL DEFAULT 'C'
                        CHECK (classification IN ('C', 'S', 'T')),
      title             TEXT     NOT NULL DEFAULT '',
      content           TEXT,
      pinned            INTEGER  NOT NULL DEFAULT 0,
      encrypted_data_id CHAR(36),
      created_at        INTEGER  NOT NULL,
      updated_at        INTEGER  NOT NULL,
      deleted_at        INTEGER,
      FOREIGN KEY (encrypted_data_id) REFERENCES t_encrypted_data (id)
  );
  CREATE INDEX index_t_note_deleted_at ON t_note (deleted_at, updated_at DESC);
  CREATE INDEX index_t_note_pinned ON t_note (pinned, updated_at DESC);
  ```

- **Files**: `vault/NoteRepository.kt` (list/get/create/update/softDelete —
  model after `PasswordRepository`, no crypto), `NoteViewModel.kt` (no
  biometric gate), `NotesUi.kt` (list + editor; render markdown preview with
  the existing `MarkdownText.kt` / richtext-commonmark).
- **Routes**: replace the `Routes.NOTES` placeholder screen with
  `NotesScreen`; add `NOTE_EDIT = "vault/notes/edit?id={id}"` +
  `Routes.noteEdit(id)` helper (same pattern as `passwordEdit`).
- **Strings**: `notes_*` (title/empty/hint, field title/content, save/delete
  dialog, pinned).
- **Tests**: androidTest `NoteRepositoryTest` (CRUD + soft delete, like
  `PasswordServiceTest` without crypto). Optional follow-up: FTS5 search
  (unicode61 tokenizer — jieba is desktop-only) in a later migration.

### F2. Change master password (fast rewrap) ☐

O(1) per docs/design.md "Master Password Change": no `PRAGMA rekey`, no data
re-encryption. The BioKey-wrapped copies are untouched (BioKey does not depend
on the password).

- **Files**: `vault/MasterPasswordChange.kt`:
  1. Derive old TMK/SMK (old password) → unwrap `vault_key:smk` (verify old pw).
  2. Derive new TMK/SMK; rewrap VaultKey (AAD `vault_key:smk`), update the
     `wrapped_keys` entry in `vault.meta` (atomic `.tmp` + rename).
  3. **Re-sign both MACs with the NEW MAC key** (`SMK[32:64]` changed):
     `vault.meta` mac and `config.sig` — mirror `UnlockService.rebindVaultKey`
     (missing the config.sig re-sign = "unlocks exactly once" bug, AGENTS.md).
  4. Session VaultKey is unchanged → nothing else to do.
- **UI**: Settings → Security → "Change master password": dialog with
  old/new/confirm fields (reuse `MasterPasswordScreen` validation strings);
  on success show a confirmation and offer re-lock.
- **Strings**: `settings_change_password*`, `password_change_*`.
- **Tests**: androidTest `MasterPasswordChangeTest` — create vault (or reuse
  fixture), change password, assert: old password fails, new password opens,
  meta mac + config.sig verify, `vault_key:biokey` copy still unwraps
  (needs the BioKey alias — skip that leg if the runner has no biometrics).

### F3. Auto-lock, lock-on-background, FLAG_SECURE ☐

- **Files**:
  - `settings/SettingsStore.kt` — `SharedPreferences("cryptowl.settings")`
    (design's `global_prefs.xml`): `auto_lock_timeout_ms` (0/60_000/300_000/1_800_000),
    `flag_secure` (Boolean).
  - `MainViewModel`: inject `SettingsStore`; expose `autoLock`/`flagSecure`
    StateFlows; `ProcessLifecycleOwner` (lifecycle-process is already a dep)
    `ON_STOP` → `lockVault()`.
  - Timeout: coroutine ticking every 30 s against `lastInteractionAt`,
    updated by a root-level pointer/scroll observer
    (`CryptowlApp` wraps content in a `Modifier.pointerInput` pass-through
    that refreshes the timestamp).
  - FLAG_SECURE: apply/clear `WindowManager.LayoutParams.FLAG_SECURE` on the
    activity whenever the setting changes.
- **UI**: Settings rows — timeout dropdown, FLAG_SECURE switch.
- **Strings**: `settings_autolock*`, `settings_flag_secure*`.
- **Tests**: JVM unit test for `SettingsStore` persistence + timeout decision
  logic (pure, no Android deps beyond SharedPreferences — put the decision
  "shouldLock(now, lastInteraction)" as a pure function and unit-test it).

---

## P1 — Media

### F4. Media vault (Confidential-tier photos/videos) ☐

C tier per docs/design.md "File Encryption by Tier": whole-file CWO1 with
**FEK** (already on `VaultSession.fek`), **no per-item DEK** — so no
migration: rows go into the existing `t_file` (v1) with `dek_id = NULL`,
`classification = 'C'`.

- **Files**:
  - `vault/MediaRepository.kt` — `t_file` rows: `storage_name = <uuid>.cwo`,
    `original_name`, `mime_type`, `size_bytes`; list (paginated grid) /
  softDelete.
  - `vault/MediaService.kt` — write: bytes → `Cwo1.encryptWholeFile(fek,
    aad = t_file row id)` → `attachments/<uuid>.cwo`; thumbnail (downscale +
    EXIF-rotate — reuse the chat's `ChatUi` bitmap helpers) → `thumbnails/
    <uuid>_t.cwo` (same key/AAD per design). Video/audio → `Cwo1.encryptChunked`.
  - Generalize `MomentsScreen.MomentsMediaLoader` → `ui/MediaLoader.kt`
    (LRU of decrypted thumbnails keyed by vault/subdir/file) and reuse in both
    the moments grid and the media grid; the `MediaViewer` moves there too.
  - `MediaViewModel.kt` (C tier — no fingerprint gate), `MediaUi.kt` (grid,
    tap → viewer).
  - Capture: **CameraX** (per design "custom CameraX preview, no system camera
    intent") — add `androidx.camera:camera-core/camera2/lifecycle/view`,
    capture → in-memory bytes → `MediaService` (never plaintext on disk).
    Import: `PickVisualMedia` (as in chat).
- **Routes**: replace `Routes.MEDIA` placeholder with `MediaScreen`.
- **Strings**: `media_*` (title/empty/hint, capture, import, delete dialog).
- **Tests**: androidTest `MediaServiceTest` — round-trip encrypt/decrypt via
  `session.fek` + `t_file` row + thumbnail AAD verification.

### F5. Video/audio playback for CWO1 chunked media ◐

`Cwo1.decryptChunkAt` already exists; only the player bridge is missing
(`viewer_video_unsupported` toast is the current stub).

- **Files**:
  - `media/Cwo1DataSource.kt` — media3 `DataSource` reading chunk records on
    demand (decrypt → feed; **no plaintext file at rest**; cacheDir temp file
    deleted `onStop` is the documented fallback, moments.md §8, but prefer
    the DataSource).
  - `media/VideoPlayerScreen.kt` — `PlayerView` (or Compose `AndroidView`)
    + ExoPlayer with the custom source; scrubbing works because records are
    random-access.
  - Wire into the shared `MediaViewer` (replace the toast for `media_type ==
    "video"`); audio items get a minimal player surface.
- **Deps**: `androidx.media3:media3-exoplayer`, `media3-ui` (pick the latest
  stable compatible with compileSdk 37).
- **Strings**: `player_*` errors.
- **Tests**: androidTest — decrypt a chunked CWO1 fixture end-to-end via the
  DataSource (feed a small synthetic video buffer).

---

## P2 — Top-Secret tier & social

### F6. Top-Secret tier (secondary password, L3) ☐

Full chain per docs/design.md: `ts_kek:biokey` + `top_secret_kek:ts_kek` rows
in `t_wrapped_key` (v1 schema already allows these roles).

- **Setup** (Settings → Security → "Set up secondary password"):
  1. User types secondary password (twice) + fingerprint prompt.
  2. `TS-KEK = kdf.createSecondaryKey(pw, meta.salts.secondary)` (already
     implemented).
  3. Wrap TS-KEK with the biometric-authorized **encrypt** cipher, AAD
     `"ts_kek:biokey"` → `t_wrapped_key` row (mirror `KekService.completeCreate`).
  4. Generate `TopSecretKEK` (random 32 B) → `kdf.wrapKey(TS-KEK-wrapped)`,
     AAD `"top_secret_kek:ts_kek"` → row.
- **Per access (true two-factor — both required, in this order):**
  1. Fingerprint → BioKey decrypts `ts_kek:biokey` → `TS-KEK_bio`.
  2. Dialog: user types the secondary password → `TS-KEK_pw` derived; verify
     `MessageDigest.isEqual(TS-KEK_bio, TS-KEK_pw)` (both factors confirmed).
  3. Unwrap `top_secret_kek:ts_kek` → TopSecretKEK → per-item DEK → content.
     All intermediates cleared immediately (mirror `PasswordViewModel`).
- **Storage**: T-tier notes — `t_note` rows with `classification='T'` +
  `encrypted_data_id` set (F1's schema is already T-ready); content encrypted
  with a per-item DEK wrapped by TopSecretKEK (direct copy of `PasswordService`
  with TopSecretKEK in the KEK slot).
- **Files**: `vault/TopSecretService.kt` (wrap/unwrap + two-factor verify),
  `TopSecretViewModel.kt` (gate = fingerprint + password dialog), reuse the
  Notes editor UI behind the gate; replace the `Routes.TOP_SECRET` placeholder.
- **Secondary password change**: unwrap TopSecretKEK via the BioKey path
  (no old password needed), rewrap with the new TS-KEK, rewrite
  `ts_kek:biokey` — O(1), mirrors F2.
- **Strings**: `top_secret_*`, `secondary_password_*`.
- **Tests**: androidTest with direct TS-KEK injection (bypass the BioKey wrap
  only), `TopSecretServiceTest` mirroring `PasswordServiceTest`.

### F7. Moment writes (compose / like / comment) ☐

No migration (v2 tables + counter triggers already exist).

- **Files**:
  - `MomentsRepository`: add `insertMoment` (text + media rows), `addComment`,
    `addLike`/`removeLike` (triggers maintain counters); `deleteMoment` soft.
  - `MomentComposerViewModel` + `MomentsComposerUi.kt`: compose screen (text,
    attach photos via F4's capture/import helpers → CWO1 whole-file with FEK,
    `t_moment_media` rows, thumbnails) — the capture path is the design's
    "In-App Photo Capture" applied to moments.
  - Like/comment actions inline on `MomentPostView` (no fingerprint — C tier).
- **Routes**: `MOMENTS_COMPOSE = "moments/compose"`.
- **Strings**: `moments_compose_*`, `moments_comment_*`, `moments_like_*`.
- **Tests**: androidTest extending the fixture-vault tests (insert + read
  back, trigger counters).

### F8. AI friends + share grants (redaction) ☐

`t_friend` / `t_moment_share` already exist (v2). Pure app-layer enforcement
(moments.md §5).

- **Files**:
  - `vault/FriendRepository.kt` — CRUD on `t_friend` (kind ai/human, name,
    `model_id`, `role_prompt`, `is_active`), grants on `t_moment_share`
    (scope full/redacted, granted/revoked).
  - `FriendsUi.kt` — friends list (from Settings and/or a moments overflow
    menu), add/edit AI friend with persona prompt + model picker (reuse the
    chat model management), share sheet per moment: grant/revoke + scope.
  - `vault/Redaction.kt` — §5 projection: strip `location`, author identity
    ("me" only), card URLs; media thumbnails only unless a future revision
    extends `t_moment_share`.
  - Chat integration: when an AI friend is selected in `ChatScreen`, assemble
    the granted-moments projection **in memory** into the system context —
    never logged, written, or exported.
- **Strings**: `friends_*`, `share_*`, `redacted_*`.
- **Tests**: JVM unit test for `Redaction` (pure mapping) + androidTest for
  `FriendRepository` grant semantics (effective access =
  `is_active AND NOT revoked AND (visibility='friends' OR grant)`).

---

## P3 — Robustness & ecosystem

### F9. Recovery key + `.vbp` single-file export/import ☐

Per docs/design.md "Backup / Export Format (.vbp)".

- **Recovery key** (opt-in): 32 B CSPRNG, shown **once** (Crockford Base32
  grouped for printing, confirm-back dialog); never used in daily derivation.
- **`.vbp` container**: versioned header + streaming AES-256-GCM (Argon2id +
  HKDF from an export passphrase **or** the recovery key — never the master
  password) over a ZIP of `{manifest.json, vault.db, vault.meta, config.json,
  config.sig, attachments/, thumbnails/}`. Implementation: write ZIP to a
  `cacheDir` temp (or stream), encrypt in 64 KiB chunks (CWO1-style header,
  own magic e.g. `VBP1`) to the SAF stream; wipe temp. Import: reverse +
  the existing re-bind path.
- **Files**: `vault/VbpExport.kt` (export/import + key derivation),
  `RecoveryKeyDialog`, Settings rows, `RestoreScreen` gains a `.vbp` entry
  (MIME filter `application/octet-stream`).
- **Strings**: `recovery_key_*`, `vbp_*`.
- **Tests**: androidTest round-trip (export with passphrase → import →
  unlock) on a small vault.

### F10. Multi-vault support ☐

`vault_index.json` + `VaultStore.primaryVaultId` already model it; the app is
hard-wired to `DEFAULT_VAULT_ID`.

- **Files**: make `MainViewModel.vaultId` a `StateFlow<String>` (init from
  `primaryVaultId`), pass it into `UnlockService`/`VaultCreator`/backup calls
  (all already take `vaultId` params); `VaultsUi` (list/create/switch/delete
  from Settings); "Restore from backup" gains "restore as new vault".
- **Notes**: switching = lock + swap id + unlock screen; delete = confirm
  dialog + `vaultDir.deleteRecursively()` (keep the wrapped-key AAD
  vault-id binding in mind — vault ids are AAD, never rename in place).

### F11. Password manager niceties ☐ (extensions to the finished F-passwords)

- Generator (secure random, charset rules, strength hint) in the editor.
- Title search (L0 → `WHERE title LIKE ? ESCAPE '\'`, no fingerprint needed).
- Field-level attributes + history (parity with cryptowl-ref): migration
  `v5__password_attributes.sql` with `t_password_attribute` /
  `t_password_history` (epoch-ms timestamps, CHAR(36) ids — adapt the ref
  schema to our conventions) — optional, only if single-blob storage hurts.
- Clipboard policy: username/URL copy allowed; **password copy stays
  disabled** (design: passwords never transit the clipboard).

### F12. Cleanups & polish ☐

- Wire the Settings → Language row to the language dialog (extract
  `LanguageDialog` from `LanguageOption.kt` to a shared component).
- About screen: app version, vault crypto params (read-only from
  `vault.meta`: Argon2 m/t/p, vault id, kdf chain summary).
- Remove dead strings (`home_unlock`, `home_locked_desc`, `moments_chat`
  if unused) and the unused `StatusChip`.
- Empty-state hint for moments should mention in-app compose once F7 lands.
- Run `connectedDebugAndroidTest` on a real device for everything added since
  the last run (password CRUD, biometric settings) — biometrics can't be
  automated; the KEK path is covered by `PasswordServiceTest` with direct
  KEK injection.
- `wechat_sns_export` desktop parity: no replay needed until the desktop tool
  grows password/note features (its `migrate_moments.py` only applies the
  moments schema); `docs/migrations/` is the shared contract when it does.

---

## Done (context for the above)

- Shell/nav/theme, full-screen lock, fingerprint unlock + Settings management.
- Password CRUD (S tier) — v3, `KekService`, `PasswordService`/`Repository`,
  `PasswordViewModel`, list/detail/edit UI, `PasswordServiceTest`.
- Moments read-only timeline (paginated), image viewer, thumbnails LRU.
- Backup/restore (SAF folder), restore re-bind, AI chat (LiteRT-LM).
