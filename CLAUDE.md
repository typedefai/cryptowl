# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Cryptowl is a local-first encrypted vault Flutter application for storing sensitive information (passwords, notes, photos). It uses strong cryptography including Argon2id, SQLCipher, and AES-256-GCM/XChaCha20-Poly1305.

## Development Commands

```bash
flutter pub get
flutter run -d macos
flutter test
flutter test test/path/to/test.dart
flutter test --name "test description"
flutter analyze

# Build for specific platforms (web not supported due to FFI)
flutter build apk / ios / macos / linux / windows

# Code generation (Drift, Freezed, JSON serializable)
dart run build_runner build --delete-conflicting-outputs
dart run build_runner watch --delete-conflicting-outputs
```

## Technical Design Principles

### 1. Cross-Platform (No Web)

The app supports Android, iOS, macOS, Linux, and Windows. Web is not supported due to `dart:ffi` dependencies (native Argon2 and SQLCipher libraries).

### 2. Dart FFI for Native Code

Native implementations (Argon2, SQLCipher) use Dart FFI via `dart:ffi`. The native libraries are in `deps/` as local FFI plugins:

- `deps/native_argon2` — Argon2 C library with sync and async (isolate) APIs
- `deps/native_sqlcipher` — SQLCipher with Jieba FTS5 tokenizer

**macOS/iOS**: Native code is compiled via Swift Package Manager (Package.swift in `darwin/` directories). No CocoaPods.

**Android/Linux/Windows**: Native code is compiled via CMake automatically by Flutter's build system.

CPU-intensive operations (Argon2 key derivation) run in a separate Dart isolate.

### 3. Full Test Coverage for Crypto

All core cryptographic implementations have unit tests with known test vectors (from RFCs, OWASP, or official test suites). Test vectors are stored as constants in test files.

Crypto primitives tested:
- Argon2id key derivation
- AES-256-GCM encryption/decryption
- ChaCha20-Poly1305 / XChaCha20-Poly1305
- HKDF-SHA256 (extract + expand)
- HMAC-SHA256

## Architecture

The project follows a layered architecture:

- **Crypto layer** (`lib/src/crypto/`): Pure Dart + FFI implementations for Argon2, AEAD encryption, HKDF, HMAC, and memory-protected values
- **Database layer** (`lib/src/database/`): Drift ORM with `.drift` schema files, encrypted via SQLCipher (FFI)
- **Domain models** (`lib/src/domain/`): Data classes for Password, Note, Category, Session
- **Repositories** (`lib/src/repositories/`): Data access layer over Drift
- **Services** (`lib/src/service/`): Business logic including KDF, config, and encryption services
- **Providers** (`lib/src/providers/`): Riverpod state management
- **Pages** (`lib/src/pages/`): UI screens with GoRouter navigation
- **Components** (`lib/src/components/`): Reusable widgets
- **Localization** (`lib/src/localization/`): English + Chinese translations (manual, not gen-l10n)

## Key Dependencies

- **State management**: Riverpod 2.x with hooks
- **Routing**: GoRouter 16.x
- **ORM**: Drift 2.28.x with SQLCipher (sqlite3 2.x — NOT 3.x, which removed `overrideForAll`)
- **Crypto**: `cryptography` package, `ffi`, Dart FFI to native Argon2/SQLCipher C code
- **Theming**: FlexColorScheme 8.x
- **Rich text**: Fleather (Quill delta format)
- **Secure storage**: File-based (`.secrets/` directory, CrockfordBase32 encoded)

## Important Constraints

### Riverpod 2.x (Not 3.x)

Riverpod 3.x forces `sqlite3` 3.x which removed the `DynamicLibrary` override API (`overrideForAll`) needed for SQLCipher FFI. Stay on Riverpod 2.x until native_sqlcipher is rewritten as a native asset package.

### sqlite3 2.x (Not 3.x)

sqlite3 3.x uses native assets instead of `DynamicLibrary.open()`. The SQLCipher FFI approach requires sqlite3 2.x's `open.overrideForAll()` mechanism.

### FTS5 in Drift

Drift's code generator doesn't understand FTS5 virtual tables, but it includes the SQL from `.drift` files in the generated code. The FTS5 virtual table, triggers, and `searchNotes` query must stay in `note.drift` for Drift to handle them during `m.createAll()`.

### FlutterSecureStorage → File-based Storage

`FlutterSecureStorage` requires macOS Keychain entitlements which need proper code signing. The project uses file-based storage in `.secrets/` instead (see `config_service.dart`).

### Flutter Version

CI uses Flutter 3.44.2 (Dart 3.8.1). Package versions must be compatible — avoid packages requiring Dart >=3.9 or Flutter >=3.35.

## CI/CD

GitHub Actions workflow (`.github/workflows/ci.yml`) builds for all platforms on push to `master` and `feat/*` branches. The test job builds native libraries via CMake and runs `flutter test`.
