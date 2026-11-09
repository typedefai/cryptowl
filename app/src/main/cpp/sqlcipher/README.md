# SQLCipher

SQLCipher 5.0.0-beta.1 amalgamation, generated from the upstream source:

```bash
git clone --depth 1 --branch v5.0.0-beta.1 \
  https://github.com/sqlcipher/sqlcipher.git
cd sqlcipher
mkdir build && cd build
../configure --with-tempstore=yes --disable-tcl
make sqlite3.c
```

The resulting `sqlite3.c`, `sqlite3.h`, and `sqlite3ext.h` are vendored here so
the build is hermetic and does not need Tcl or the SQLCipher autotools build.

SQLCipher 5.0.0-beta.1 is pre-release software. It uses the v5 defaults
(AES-256-GCM content encryption, 8192-byte pages) and is intentionally not
backward-compatible with SQLCipher 4 databases.

The bundled library is built with SQLCipher's OpenSSL provider; see
`third_party/CMakeLists.txt`. OpenSSL 3.5.8 LTS is the recommended runtime
version. Upstream license: `LICENSE.md`.
