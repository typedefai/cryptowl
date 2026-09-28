# AGENTS.md

Local-first encrypted vault for Android (notes, passwords, photos/videos) — no plaintext data at rest. Android-native only (minSdk 36), with vendored C/C++ crypto. Working branch: `android-native`.

**Encryption/tiering design lives in [docs/design.md](docs/design.md)** (key hierarchy, C/S/T levels, biometric gating, backup, memory protection) — consult it before adding crypto features.

**Feature designs are separate docs on top of the core**: [docs/moments.md](docs/moments.md) + [docs/migrations/v2__moments.sql](docs/migrations/v2__moments.sql) (Moments — Confidential-tier timeline, CWO1 media format, virtual friends). Feature tables never re-implement the core key/wrapped-key tables.

**Cross-verification oracle**: the desktop reference implementation lives in the sibling repo `wechat_sns_export/vaultlib` (Python) — byte-exact primitives and fixed test vectors (`wechat_sns_export/tests/test_vaultlib.py`). Any crypto change here must reproduce its vectors, and vice versa. Note the HMAC order: `P = HMAC-SHA256(key=DeviceSecret, msg=MasterPassword)` (vaultlib matches the design doc; the older Flutter `cryptowl-ref` used the opposite order and is superseded).

**Moments feature**: tables from `assets/migrations/v2__moments.sql` (mirror of `docs/migrations/v2__moments.sql` — edit both), applied on vault open by `SchemaApplier.migrate` (versioned migration chain, see below); media uses the CWO1 format (`vault/Cwo1.kt`, byte-exact with `wechat_sns_export/migrate_moments.py`, vectors in `app/src/test/resources/vectors/`). Desktop-created vaults carry a `device_secret` file — `UnlockService` re-binds them to the Android Keystore on first open; the in-app transfer path is "Restore from backup" (`vault/VaultBackup.kt`, see gotcha block below). The timeline is paginated (50 posts/page, batched child queries — imported archives are thousands of posts). The vault DB is NOT Room-managed (Room cannot open externally-created SQLCipher DBs); queries go through raw SQLCipher repositories.

## Stack (verify against `gradle/libs.versions.toml`)

- AGP 9.3.1 + Gradle 9.5 + Java 11; **AGP 9 built-in Kotlin** — do NOT add the `kotlin-android` plugin
- KSP 2.3.10 (standalone KSP2 versioning, compatible with AGP built-in Kotlin)
- sqlcipher-android 4.17.0 (`@aar`) — the vault DB is raw SQLCipher only; **there is no Room layer** (Room cannot open externally-created SQLCipher DBs; the former Room `VaultDatabase` was removed)
- **UI: Jetpack Compose** (Material3, compose BOM 2026.02.00) — use Compose for all UI, no View/XML layouts. Markdown rendering via `com.halilibo.compose-richtext` (richtext-commonmark + richtext-ui-material3), same as the AI Edge Gallery reference app
- JNI/CMake (CMake 3.22.1, NDK 29), vendored phc-winner-argon2 v20190702 in `app/src/main/cpp/argon2/`

## Commands

- Build: `./gradlew :app:assembleDebug` · Release: `:app:assembleRelease` (unsigned)
- Lint: `./gradlew :app:lintDebug` · JVM tests: `:app:testDebugUnitTest`
- **Instrumented tests (the real test suite): `./gradlew :app:connectedDebugAndroidTest`** — requires a connected device/emulator; JNI and SQLCipher can't run in JVM unit tests
- Full local check: lint + unit + instrumented; CI runs all three in parallel

## Gotchas (all hard-earned)

**JNI** (`app/src/main/cpp/argon2-jni.cpp` ↔ `Argon2.kt`):
- JNI symbol names must match the Kotlin `private external` names exactly (`nativeHash`, `nativeHashEncoded`, `nativeVerify`); mismatch → `UnsatisfiedLinkError`
- JNI arg order is `(password, salt, mCost, tCost, parallelism, hashLen, type)` — **mCost before tCost**; swapping silently swaps values → confusing errors like "Memory cost is too small"
- `argon2_hash` with `encoded != NULL` still validates/uses hashLen — passing 0 yields "Output is too short"; always pass the real value
- The vendored argon2/ tree is upstream reference code; don't refactor it, edit the wrapper instead

**SQLCipher + Room**:
- `sqlcipher-android` is declared `@aar` → no transitive deps → keep `androidx.sqlite:sqlite` explicit in `app/build.gradle.kts`
- `System.loadLibrary("sqlcipher")` required before any DB use (already in `VaultDatabase.create`)
- **Vault DB is initialized from raw SQL, not Room entities**: `VaultCreator` creates `vault.db` via `net.zetetic...SQLiteDatabase.openOrCreateDatabase(File, vaultKey.asSqlCipherRawKey(), ...)` and `SchemaApplier.migrate` applies the versioned chain in `assets/migrations/` (`v1__init.sql`, `v2__moments.sql`, ... — mirrors of `docs/migrations/`, edit both). Room's `VaultDatabase` is a separate layer for record tables
- **VaultKey must be passed as the raw-key expression `x'<hex>'` (`vault/SqlCipher.kt::asSqlCipherRawKey`), never as a bare 32-byte `byte[]`**: SQLCipher treats a plain blob as a *passphrase* (PBKDF2 256k iterations), not a raw key — oracle-verified with vaultlib (`sqlite3_key_v2` with `b"x'<hex>'"` opens the DB, the same 32 bytes as a blob does not). A bare-blob open "works" for vaults created the same way, so Android-only round-trips won't catch it; desktop-created vaults fail with `file is not a database (code 26)` — that asymmetry is the symptom
- **Re-binding a desktop vault re-signs `config.sig`**: SMK depends on the Device Secret (`P = HMAC(DeviceSecret, password)`), so after `UnlockService.rebindVaultKey` re-wraps `vault_key:smk` with the Android SMK, `config.sig` (HMAC'd with SMK[32:64]) is invalid unless re-signed with the new MAC key. Missing re-sign = the vault unlocks exactly once, then fails `config.sig mismatch` on every later open
- **net.zetetic `rawQuery` binding quirks** (probed empirically on-device): `LIMIT ? OFFSET ?` with bound args throws `SQLiteDatatypeMismatchException (code 20)` regardless of arg type; the `Object...` varargs overload silently returns 0 rows; only the `String[]` overload binds correctly. Inline internal Ints (`LIMIT $n`) and bind strings via `arrayOf(String)`; never bind user-controlled strings inline
- Onboarding writes `vault.meta` (canonical sorted-key JSON, MAC'd with SMK[32:64], Crockford Base32 binary fields) before creating the DB — see `vault/VaultMeta.kt`; the `mac` field is excluded from its own MAC computation. `VaultCreator` must put **copies** of the salts into `VaultMeta` — it wipes the local salt buffers on exit, and `Salts` holds the same array references
- Room 2.8: `RoomDatabase` implements `AutoCloseable`, **not** `Closeable` — `kotlin.io.use {}` does not compile on it
- Schema exported to committed `app/schemas/` (`room.schemaLocation` in build.gradle.kts); any entity/column change updates `.../VaultDatabase/1.json`
- **Migrations: Room `Migration` objects + `MigrationTestHelper`, not Flyway.** Never `fallbackToDestructiveMigration` in release (silent data loss in a vault)
- Passphrase is passed into `VaultDatabase.create()`; the intended flow (see `passphraseCanBeDerivedFromArgon2` test) is Argon2 KDF → SQLCipher passphrase. Key rotation (master password change) is `PRAGMA rekey` at unlock time, orthogonal to schema migrations

**Vault backup / restore (SAF)** (`vault/VaultBackup.kt`, `RestoreScreen.kt`):
- The WeChat-migration transfer path: desktop `migrate_moments.py` produces a vault folder → user copies it to the device → in-app "Restore from backup" (SAF folder picker) copies it into `<filesDir>/vaults/<id>` (staging dir → validate → atomic swap) → Unlock screen re-binds it
- Restore validates before swapping: `vault.meta` parses, vault id matches, `vault.db` ≥ 4 KiB, and per-file sizes must match the SAF listing
- Backup writes into a **timestamped subdirectory** (`cryptowl-backup-<stamp>`) of the picked tree — never merges into existing folders
- SAF tree enumeration goes through `DocumentsContract` (children query + `buildDocumentUriUsingTree`); `DocumentsContract.createDocument(resolver, parentDoc, MIME_TYPE_DIR, name)` is the dir-creation API (there is no `ContentResolver.createDocument`)

**Error handling**:
- **Always log the raw exception before mapping it to a user-facing message** (`Log.e(TAG, "<what> failed", e)`) — `e.message` alone (e.g. SQLite's `unknown error (code 0)`) is useless for diagnosis and must never be the only trace of a failure. Catch `Throwable` in coroutine catch sites that guard JNI/DB calls: `UnsatisfiedLinkError`/`OutOfMemoryError` are `Error`s, not `Exception`s, and slip through `catch (e: Exception)`
- **Vault DB migrations are a versioned chain, not a rolling schema**: scripts in `assets/migrations/` are immutable once applied — add new ones (`v3__<desc>.sql`), never edit old ones. Progress is `PRAGMA user_version`; forward-only, a newer-version vault fails loudly on an older app. `SchemaApplier` forces `IF NOT EXISTS` on CREATEs (desktop-created vaults may have later-version tables while reporting an older version) and routes `PRAGMA` statements through `rawQuery` (some return rows; `execSQL` rejects that). Schema changes must also be replayed by the desktop tool (`wechat_sns_export`) — plain SQL, no tool-specific history table
- Migration scripts (`v*.sql`) must stay comment-free: `SchemaApplier` splits on `;` before executing, so a `;` inside a `--` comment produces bogus "statements" (SQL syntax errors on vault open). Keep docs in the git history / design docs instead

**Tests**:
- JVM unit tests touch `android.util.Log`/`SystemClock` (KdfService logging) — `testOptions.unitTests.isReturnDefaultValues = true` in `app/build.gradle.kts` keeps them from throwing "not mocked"
- `androidTest` fixture assets (e.g. `app/src/androidTest/assets/fixture_vault.tar`) live in the **test APK** — read them via `InstrumentationRegistry.getInstrumentation().context.assets`, not the app-under-test context (app context only sees `app/src/main/assets`)

**Device/CI**:
- Physical device installs can fail with `INSTALL_FAILED_USER_RESTRICTED` → user must enable "Install via USB" in Developer options. On Xiaomi/MIUI that alone is not enough: **"USB debugging (Security settings)"** must also be enabled and the on-screen install prompt must be confirmed within its short timeout (the "canceled by user" error repeats otherwise)
- CI runs on **all branches** (push + PR). The emulator job requires the `libpulse0` apt install and `sudo chmod 666 /dev/kvm` steps (hosted runners lack kvm group perms) and `-no-window`; don't remove them. minSdk 36 forces the API 36 emulator image

## Design constraints

- No plaintext data: everything stored is encrypted (SQLCipher at rest; column-level ciphertext via `encrypted_data` tables in the ref design)
- Tiered access: notes readable after login; passwords require per-access auth (biometric) — structure data accordingly
- Crypto: Argon2id (JNI binding, done), AES-256 (SQLCipher), export/backup support planned
- Reference product design lives in the sibling Flutter repo (`cryptowl-ref`, has drift schema + crypto tests) — mirror its `t_password` schema conventions when extending
