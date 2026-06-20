# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Cryptowl is a local-first encrypted vault Flutter application for storing sensitive information (passwords, notes, photos). It uses strong cryptography including Argon2id, SQLCipher, and AES-256-GCM/XChaCha20-Poly1305. The project is being migrated from an older Flutter codebase at `/Users/riguz/workspace/apps/cryptowl`.

## Development Commands

```bash
flutter pub get
flutter run
flutter test
flutter test test/path/to/test.dart
flutter test --name "test description"
flutter analyze

# Build for specific platforms
flutter build apk / ios / macos / linux / windows / web

# Code generation (Drift, Freezed, Riverpod, JSON serializable)
dart run build_runner build --delete-conflicting-outputs
dart run build_runner watch --delete-conflicting-outputs

# Cross-verification (Python, requires: pip install argon2-cffi pynacl pycryptodome)
python cross-verify.py
```

## Technical Design Principles

### 1. Cross-Platform First

The app must support all Flutter platforms: Android, iOS, macOS, Linux, Windows, and Web. Every dependency and implementation choice must be evaluated for cross-platform compatibility. Platform-specific code should be isolated behind abstractions with Dart-only fallbacks where possible.

### 2. Dart FFI for Native Code (Not Plugins)

Native implementations (e.g., Argon2, SQLCipher) use **Dart FFI directly** via `dart:ffi` and `DynamicLibrary.open()`, NOT Flutter plugin packages. This avoids plugin registration issues, platform channel overhead, and compatibility problems across Flutter versions.

Pattern for loading native libraries:
```dart
DynamicLibrary _openLibrary() {
  if (Platform.isMacOS || Platform.isIOS) {
    return DynamicLibrary.open('libfoo.framework/libfoo');
  }
  if (Platform.isAndroid || Platform.isLinux) {
    return DynamicLibrary.open('libfoo.so');
  }
  if (Platform.isWindows) {
    return DynamicLibrary.open('foo.dll');
  }
  throw UnsupportedError('Unsupported platform');
}
```

CPU-intensive operations (Argon2 key derivation) must run in a separate Dart isolate to avoid blocking the UI thread.

### 3. Full Test Coverage + Cross-Verification for Crypto

All core cryptographic implementations require:

- **Unit tests** with known test vectors (from RFCs, OWASP, or official test suites)
- **Cross-verification** against independent implementations (e.g., Python with `argon2-cffi`, `pycryptodome`, `pynacl`) to ensure correctness
- Test vectors stored as constants in test files, not generated at runtime

Crypto primitives requiring cross-verification:
- Argon2id key derivation
- AES-256-GCM encryption/decryption
- ChaCha20-Poly1305 / XChaCha20-Poly1305
- HKDF-SHA256 (extract + expand)
- HMAC-SHA256

The `cross-verify.py` script at the project root contains Python reference implementations for validating Dart crypto outputs.

## Architecture

The project follows a layered architecture:

- **Crypto layer** (`lib/src/crypto/`): Pure Dart + FFI implementations for Argon2, AEAD encryption, HKDF, HMAC, and memory-protected values
- **Database layer** (`lib/src/database/`): Drift ORM with `.drift` schema files, encrypted via SQLCipher (FFI)
- **Domain models** (`lib/src/domain/`): Data classes for Password, Note, Category, Session
- **Repositories** (`lib/src/repositories/`): Data access layer over Drift
- **Services** (`lib/src/service/`): Business logic including KDF, config, and encryption services
- **Providers** (`lib/src/providers/`): Riverpod state management
- **Pages** (`lib/src/pages/`): UI screens with GoRouter navigation

## Key Dependencies

- **State management**: Riverpod 2.x with hooks
- **Routing**: GoRouter
- **ORM**: Drift with SQLCipher
- **Crypto**: `cryptography` package, `ffi`, Dart FFI to native Argon2/SQLCipher C code
- **Theming**: FlexColorScheme
- **Rich text**: Fleather (Quill delta format)
