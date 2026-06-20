# Cryptowl

A local-first encrypted vault Flutter application for storing sensitive information (passwords, notes, photos). All data is encrypted on the local device using strong cryptography.

## Features

- **3-level encryption**: Standard, Protected, and Vault tiers with different security properties
- **Strong KDF**: Argon2id (OWASP recommended parameters: m=19456, t=2, p=1)
- **Authenticated encryption**: AES-256-GCM and XChaCha20-Poly1305
- **Per-entry encryption**: Each item gets its own data encryption key (DEK)
- **Chinese full-text search**: Jieba tokenizer for FTS5 indexing
- **Rich text notes**: Fleather editor (Quill delta format)
- **Media storage**: Photos and videos stored as encrypted individual files
- **Cross-platform**: Android, iOS, macOS, Linux, Windows

## Encryption Levels

Cryptowl classifies items into three security tiers. Each tier has different encryption and authentication requirements.

### Standard (C)

| Property | Value |
|----------|-------|
| Encryption | SQLCipher database encryption only |
| Auth to view | Login (master password) |
| Key | Symmetric key (derived from master password) |

Standard items are readable immediately after login. The database-level encryption protects data at rest, but once the app is unlocked, all Standard items are accessible without additional prompts.

**Use for:** Low-sensitivity data that needs fast access and searchability.

### Protected (S)

| Property | Value |
|----------|-------|
| Encryption | SQLCipher + per-entry DEK (AES-256-GCM) |
| Auth to view | Biometric or PIN per item |
| Key | Per-entry DEK, encrypted by a KEK derived on-demand |

Protected items show metadata (title, date) in lists, but the content is encrypted with a per-entry DEK. Viewing the content requires biometric authentication or a PIN. The KEK is derived on-demand and never stored in memory — it is discarded after use.

**Use for:** Passwords, sensitive notes, anything you want gated behind explicit user action.

### Vault (T)

| Property | Value |
|----------|-------|
| Encryption | SQLCipher + per-entry DEK (XChaCha20-Poly1305) |
| Auth to view | Secondary password (separate from master password) |
| Key | Per-entry DEK, encrypted by a KEK derived from the secondary password |

Vault items use a completely separate key hierarchy. The secondary password is set independently from the master password and derives its own key. Even if the master password is compromised, Vault items remain encrypted.

The secondary password is entered when first accessing a Vault item in a session. It can optionally be cached for the duration of the session.

**Use for:** Critical secrets — recovery keys, master credentials, anything that must survive a master password compromise.

## Threat Model

### What each level protects against

| Threat | Standard | Protected | Vault |
|--------|----------|-----------|-------|
| Device off, data at rest | ✅ SQLCipher | ✅ SQLCipher + DEK | ✅ SQLCipher + DEK |
| App locked / backgrounded | ✅ Protected | ✅ Protected | ✅ Protected |
| App unlocked, phone unattended | ❌ All readable | ✅ Needs biometric | ✅ Needs secondary pw |
| Master password compromised | ❌ All readable | ❌ KEK derivable | ✅ Separate key hierarchy |
| Memory dump / debugger attach | ❌ Key in memory | ✅ KEK not stored | ✅ KEK not stored |
| Database file stolen | ✅ SQLCipher | ✅ SQLCipher + DEK | ✅ SQLCipher + DEK |
| Partial DB leak (e.g. SQL injection) | ❌ Plaintext | ✅ DEK encrypted | ✅ DEK encrypted |

### What no level protects against

- **Compromised device** — if the OS itself is compromised (malware, rooted/jailbroken), all bets are off
- **Screen capture** — if the user is viewing content, screenshots or screen recording can capture it
- **Clipboard** — copied passwords are accessible to other apps until overwritten
- **Backup extraction** — cloud backups may include app data depending on platform settings

## Item Types

### Passwords

| Level | Storage | Behavior |
|-------|---------|----------|
| Standard | DB (plaintext attributes) | Username, URL visible in list; password visible on tap |
| Protected | DB (encrypted DEK) | Only title visible in list; biometric to view password |
| Vault | DB (encrypted DEK, secondary KEK) | Only title visible in list; secondary password to view |

Passwords always use per-entry DEK encryption at Protected and Vault levels. Each password entry has its own DEK, so compromising one entry does not expose others. Attributes (username, URL, remark) follow the same classification as the password itself.

### Notes

| Level | Storage | Behavior |
|-------|---------|----------|
| Standard | DB (plaintext content + FTS5 index) | Full content readable after login; searchable via Jieba |
| Protected | DB (encrypted DEK) | Title visible; biometric to view content; NOT searchable |
| Vault | DB (encrypted DEK, secondary KEK) | Title visible; secondary password to view content; NOT searchable |

Standard notes are stored in plaintext within the SQLCipher-encrypted database, enabling full-text search via the Jieba tokenizer. Protected and Vault notes sacrifice searchability for encryption — the content is encrypted with a per-entry DEK and cannot be indexed.

**Note history** follows the same classification as the note. Previous versions are encrypted at the same level.

### Photos and Videos

| Level | Storage | Behavior |
|-------|---------|----------|
| Standard | Individual encrypted files | Thumbnail visible; full-size viewable after login |
| Protected | Individual encrypted files | Thumbnail visible; biometric to view full-size |
| Vault | Individual encrypted files | Thumbnail visible; secondary password to view |

Media files are stored as individual encrypted files on disk (not in the database). The database stores metadata (filename, classification, timestamps) and a reference to the encrypted file. Each file is encrypted with its own DEK, following the same key hierarchy as other item types.

File encryption:
```
Original file → AES-256-GCM (or XChaCha20-Poly1305 for Vault) with per-file DEK
              → encrypted file stored in app's documents directory
              → DEK encrypted by KEK, stored in database
```

This approach keeps the database small and allows efficient storage of large media files while maintaining the same 3-level security model.

## Key Hierarchy

```
Master password
  + Secret key (stored in .secrets/, CrockfordBase32 encoded)
  → HMAC-SHA256
  → Argon2id (m=19456, t=2, p=1)
  → HKDF (64 bytes)
      ├─ First 32 bytes: symmetric key
      │   ├─ SQLCipher database key (first 32 bytes)
      │   └─ Standard KEK → encrypts DEKs for Protected items
      └─ Last 32 bytes: HMAC key for config integrity

Secondary password
  + Secret salt (stored in config)
  → Argon2id (m=19456, t=2, p=1)
  → Vault KEK → encrypts DEKs for Vault items
```

The symmetric key is encrypted with AES-256-GCM and stored in the config file. The config file itself is integrity-verified with HMAC-SHA256.

## Architecture

```
lib/src/
  crypto/        → Pure Dart + FFI: Argon2, AEAD, HKDF, HMAC, ProtectedValue
  database/      → Drift ORM with .drift schema files, encrypted via SQLCipher
  domain/        → Data models: Password, Note, Category, Session
  repositories/  → Data access layer over Drift
  service/       → Business logic: KDF, config, encryption, file services
  providers/     → Riverpod state management
  pages/         → UI screens with GoRouter navigation
  components/    → Reusable widgets
  localization/  → English + Chinese translations
```

## Getting Started

```bash
# Clone with submodules
git clone --recurse-submodules git@github.com:typedefai/cryptowl.git
cd cryptowl

# Install dependencies
flutter pub get

# Run
flutter run -d macos    # or -d android, -d ios, -d linux, -d windows

# Run tests
flutter test

# Code generation (Drift, Freezed, JSON serializable)
dart run build_runner build --delete-conflicting-outputs
```

## Native Dependencies

The project uses two local FFI plugins (in `deps/`):

- **native_argon2** — Argon2 C library for key derivation
- **native_sqlcipher** — SQLCipher encrypted SQLite (with Jieba FTS5 tokenizer)

These are compiled automatically by Flutter's build system via CMake (Android/Linux/Windows) and Swift Package Manager (macOS/iOS).

## CI/CD

GitHub Actions builds and tests on all platforms:

| Platform | Runner | Artifact |
|----------|--------|----------|
| Tests | ubuntu-latest | — |
| Android | ubuntu-latest | APK |
| iOS | macos-latest | — |
| macOS | macos-latest | .app |
| Linux | ubuntu-latest | bundle |
| Windows | windows-latest | exe |

## License

See [LICENSE](LICENSE) for details.
