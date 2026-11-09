# AGENTS.md

Native Android client for the CryptOwl encrypted vault (notes, passwords,
photos/videos) — **vault format v3**, byte-compatible with the C++/Qt desktop
reference in the sibling repo `cryptowl-desktop`. Android-native only (minSdk
36), Compose UI, vendored C/C++ crypto. Working branch: `master`.

**The spec lives in [docs/](docs/)** — [`items.md`](docs/items.md) (v3 item
model, schema, history), [`design.md`](docs/design.md) (key hierarchy, C/S/T
tiers, `vault.meta`, memory protection), [`schema.sql`](docs/schema.sql)
(canonical schema, fixed at creation). `docs/` is a byte-mirror of
`cryptowl-desktop/docs/` — edit both together, and if spec and code disagree,
the spec wins.

## Stack

- AGP 9.3.1 + Gradle 9.5 + Java 11 source level; **AGP 9 built-in Kotlin** — do
  NOT add the `kotlin-android` plugin
- KSP 2.3.10 (standalone KSP2), Compose BOM 2026.02.00, Material3
- **SQLCipher 5.0.0-beta.1 vendored** under `app/src/main/cpp/sqlcipher/`
  (upstream amalgamation, do not modify) with the OpenSSL 3.5.x crypto
  provider, built by CMake — same version/defaults as the desktop client
  (AEAD AES-256-GCM, 8 KiB pages). There is **no** `sqlcipher-android` AAR and
  **no Room**; queries go through `vault/SqlCipherDb.kt` + raw SQL
  repositories
- JNI/CMake (CMake 3.22.1, NDK 29), vendored phc-winner-argon2 v20190702 in
  `app/src/main/cpp/argon2/`
- Markdown preview via `com.halilibo.compose-richtext`

## Commands

- Build: `./gradlew :app:assembleDebug` · Release: `:app:assembleRelease` (unsigned)
- Lint: `./gradlew :app:lintDebug` · JVM tests: `:app:testDebugUnitTest`
- **Instrumented tests (the real suite): `./gradlew :app:connectedDebugAndroidTest`**
  — requires a connected device/emulator; JNI and SQLCipher cannot run in JVM tests

## Format invariants (byte-exact contract — do not "clean up")

- `P = HMAC-SHA256(key=DeviceSecret, msg=masterPassword)` — this order.
- `TMK = Argon2id(P, salt=argon2Salt, m=19456 KiB, t=2, p=1)`; parameters are
  read from `vault.meta.kdf`, never hardcoded.
- `SMK = HKDF-SHA256(TMK, salt=hkdfSalt, info=vaultId, L=64)`;
  `SMK[0:32]` unwraps `VaultKey`, `SMK[32:64]` is the MAC key.
- `VaultKey` is the SQLCipher raw key and must be passed as the raw-key
  expression `x'<hex>'` — **never as a bare 32-byte blob** (SQLCipher would
  treat it as a passphrase).
- `FEK = HKDF-SHA256(VaultKey, salt='', info="file", 32)` (C-tier files);
  `HFK = HKDF-SHA256(VaultKey, salt='', info="fingerprint", 32)`.
- AES-256-GCM everywhere. AAD: wrapped keys = wrapped-key id (`role:wrapper`),
  DEK wraps = DEK id, payloads = encrypted-data row id, file envelopes = file
  id, thumbnails = `"<file_id>:thumb"`.
- `vault.meta` v3: **Qt-canonical JSON** — compact separators, lexicographic
  key order at every level, binary fields Crockford Base32 (grouped), MAC =
  HMAC-SHA256 over the document minus `mac`, **no `\uXXXX` escaping of
  non-ASCII** (Qt writes raw UTF-8; escaping would break desktop vaults with
  non-ASCII vault ids). Readers accept only `version == 3`.
- `config.json` = `{"files":{"embed_max_bytes":N},"name":"<vaultId>"}`;
  `config.sig` = Crockford-B32 HMAC text; verified on every open.
- **Schema is fixed at vault creation** (`docs/schema.sql`, idempotent
  `CREATE IF NOT EXISTS`). No `PRAGMA user_version`, no migrations, no ALTER.
  Open verifies the 6 tables (`t_wrapped_key`, `t_data_encrypt_key`,
  `t_encrypted_data`, `t_item`, `t_file`, `t_item_version`) and refuses
  anything else with a clear "recreate the vault" error.
- `fingerprint = Crockford32(HMAC-SHA256(HFK, title \0 classification \0 payload))`;
  unchanged saves write no version row. Restore writes a new version.
- External files: CWO1 chunked envelope (22-byte header, 64 KiB chunks, chunk
  nonce `u64be(index) || iv_prefix`, AAD = file id). Embedded files:
  `nonce(12) || ciphertext || tag(16)`.

## Gotchas (hard-earned)

**JNI** (`app/src/main/cpp/argon2-jni.cpp` ↔ `crypto/Argon2.kt`):
- JNI symbol names must match the Kotlin `private external` names exactly
  (`nativeHash`, `nativeHashEncoded`, `nativeVerify`).
- JNI arg order is `(password, salt, mCost, tCost, parallelism, hashLen, type)`
  — **mCost before tCost**.
- `argon2_hash` with `encoded != NULL` still validates/uses hashLen — always
  pass the real value.
- The vendored argon2/ tree is upstream reference code; edit the wrapper only.

**SQLCipher**:
- `System.loadLibrary("sqlcipher")` before any DB use (own vendored build).
- Raw key expression only (see above). A bare-blob open "works" for vaults
  created the same wrong way, so Android-only round-trips won't catch it;
  desktop-created vaults fail with `file is not a database (code 26)`.
- SQLCipher 5 creates AEAD-GCM/8 KiB-page databases by default. Do **not**
  add `cipher_compatibility=4` — the desktop also uses v5 defaults.
- Bind strings via parameters; inline internal Integers (`LIMIT $n`) instead
  of `?` binds where the driver's binding is quirky; never inline
  user-controlled strings.
- `PRAGMA foreign_keys = ON` / `secure_delete = ON` at every open.

**vault.meta / re-bind**:
- Re-binding a desktop vault re-signs **`config.sig` and the meta MAC**: SMK
  depends on the Device Secret, so `UnlockService.rebind` must re-sign both,
  re-wrap `vault_key:smk`, delete `device_secret`, and consume any `kek:smk`
  row (unwrap with the old SMK, rewrap as `kek:biokey`, delete the old row).
  Missing re-sign = the vault unlocks exactly once, then fails `config.sig
  mismatch`.
- Onboarding writes `vault.meta` *before* creating the DB (the SQLCipher key
  comes from unwrapping it). Copy salts into `VaultMeta` — local buffers are
  wiped on exit.

**Error handling**:
- Always log the raw exception before mapping it to a user-facing message
  (`Log.e(TAG, "<what> failed", e)`); catch `Throwable` around JNI/DB calls
  (`UnsatisfiedLinkError`/`OutOfMemoryError` are `Error`s, not `Exception`s).
- Keys never logged, never in exception messages; derived on demand, zeroized
  after use (`ProtectedValue`).

**Tests**:
- JVM unit tests touch `android.util.Log`/`SystemClock` — keep
  `testOptions.unitTests.isReturnDefaultValues = true`.
- androidTest fixture assets live in the **test APK** — read them via
  `InstrumentationRegistry.getInstrumentation().context.assets`.
- Crypto golden vectors (P/TMK/SMK/FEK/HFK, GCM wrap) are pinned in
  `app/src/androidTest/.../CryptoVectorsTest.kt` and mirror
  `cryptowl-desktop/tests/core/tst_crypto_vectors.cpp` exactly.

**Device/CI**:
- Physical device installs can fail with `INSTALL_FAILED_USER_RESTRICTED` —
  enable "Install via USB" and (Xiaomi/MIUI) "USB debugging (Security
  settings)", then confirm the on-screen prompt.
- CI runs on all branches. The emulator job needs `libpulse0`,
  `sudo chmod 666 /dev/kvm`, `-no-window`, and the API 36 image (minSdk 36).
