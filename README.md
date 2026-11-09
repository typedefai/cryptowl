# CryptOwl Android

Local-first encrypted vault for Android — notes, passwords, photos and videos,
with **no plaintext data at rest**. This is the native Android client (Kotlin +
Jetpack Compose, minSdk 36) implementing **vault format v3**, the same format as
the C++/Qt desktop reference client.

The format specification is frozen in [`docs/`](docs/) and mirrored byte-for-byte
from the desktop repository:

| File | Contents |
| --- | --- |
| [docs/items.md](docs/items.md) | **Vault format v3** — unified item model, canonical schema, history, type registry |
| [docs/design.md](docs/design.md) | Key hierarchy, tiers (C/S/T), `vault.meta`, backup, memory protection |
| [docs/schema.sql](docs/schema.sql) | Canonical schema — executed once at vault creation, no migrations |
| [docs/encryption.svg](docs/encryption.svg) | Key hierarchy diagram |

## Why v3

`t_item` is the single data structure: every note, password, photo and folder is
an item with a plugin type. Changes are versioned (`t_item_version`), small
files are embedded in `vault.db`, large ones live in `attachments/` as CWO1
chunked envelopes. The schema is fixed at vault creation — no migrations, no
`user_version`.

## Build

```bash
./gradlew :app:assembleDebug
```

Requires JDK 17+, Android SDK 37, NDK 29. The first build also compiles the
vendored Argon2 and SQLCipher 5 native libraries (see `app/src/main/cpp/`).

## Test

```bash
./gradlew :app:testDebugUnitTest          # JVM: crypto, JSON canonicalization
./gradlew :app:connectedDebugAndroidTest  # device/emulator: JNI + SQLCipher + vault
```

Instrumented tests are the real suite: Argon2 JNI, SQLCipher, Keystore and the
BiometricPrompt-backed paths cannot run on the JVM.

## Compatibility

Vaults are byte-compatible with [`cryptowl-desktop`](../cryptowl-desktop):

- `vault.meta` v3 (canonical sorted-key JSON, MAC = HMAC-SHA256 with SMK[32:64])
- `config.json` / `config.sig` (Crockford Base32 HMAC)
- SQLCipher 5 database, raw key expression `x'<hex>'`
- CWO1 file envelopes for external media
- Fixed `docs/schema.sql` shape (verified on open, never migrated)

A desktop-created vault is re-bound to the Android Keystore on first unlock
(see `docs/design.md`, "Android re-bind on import").
