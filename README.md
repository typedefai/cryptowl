# Cryptowl

A local-first encrypted vault Flutter application for storing sensitive information (passwords, notes, photos). All data is encrypted on the local device using strong cryptography.

## Features

- **Encrypted vault**: All data stored in SQLCipher-encrypted SQLite databases
- **Strong KDF**: Argon2id (OWASP recommended parameters: m=19456, t=2, p=1)
- **Authenticated encryption**: AES-256-GCM and XChaCha20-Poly1305
- **Per-entry encryption**: Each password gets its own data encryption key (DEK)
- **Chinese full-text search**: Jieba tokenizer for FTS5 indexing
- **Rich text notes**: Fleather editor (Quill delta format)
- **Cross-platform**: Android, iOS, macOS, Linux, Windows

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

## Security Model

1. Master password + secret key → HMAC-SHA256 → Argon2id → HKDF stretched key
2. First 32 bytes: AES-256-GCM encrypt the symmetric key
3. Last 32 bytes: HMAC key for config integrity verification
4. Symmetric key's first 32 bytes: SQLCipher database key
5. Individual passwords: per-entry DEK encrypted by KEK

The secret key is stored in the app's documents directory (`.secrets/`), encoded with CrockfordBase32.

## License

See [LICENSE](LICENSE) for details.
