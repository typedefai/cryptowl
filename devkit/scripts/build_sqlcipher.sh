#!/usr/bin/env bash
# Builds a local macOS libsqlcipher.dylib from the SQLCipher amalgamation.
#
# No Homebrew/MacPorts required: the amalgamation source already exists in the
# sibling cryptowl-ref repo (deps/native_sqlcipher/src). SQLCipher is compiled
# with CommonCrypto (SQLCIPHER_CRYPTO_CC), so no OpenSSL is needed either.
#
#   ./scripts/build_sqlcipher.sh
#   SQLCIPHER_SRC=/path/to/folder-with-sqlite3.c ./scripts/build_sqlcipher.sh
#
# The devkit auto-discovers native/libsqlcipher.dylib (see
# cryptowl_devkit/vault/sqlcipher.py). Alternatively install SQLCipher via
# Homebrew/MacPorts or point LIBSQLCIPHER at an existing dylib.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${SQLCIPHER_OUT:-$ROOT/native}"
mkdir -p "$OUT"

SRC="${SQLCIPHER_SRC:-}"
if [ -z "$SRC" ]; then
  for candidate in \
    "$ROOT/../../cryptowl-ref/deps/native_sqlcipher/src" \
    "$ROOT/../../../cryptowl-ref/deps/native_sqlcipher/src" \
    "$HOME/workspace/drriguz/cryptowl-ref/deps/native_sqlcipher/src" \
    "$HOME/cryptowl-ref/deps/native_sqlcipher/src"; do
    if [ -f "$candidate/sqlite3.c" ]; then
      SRC="$candidate"
      break
    fi
  done
fi

if [ -z "$SRC" ] || [ ! -f "$SRC/sqlite3.c" ]; then
  cat >&2 <<'EOF'
SQLCipher amalgamation not found.
Set SQLCIPHER_SRC to a folder containing sqlite3.c + sqlite3.h, e.g.:
  SQLCIPHER_SRC=../cryptowl-ref/deps/native_sqlcipher/src ./scripts/build_sqlcipher.sh
or install SQLCipher (brew install sqlcipher / port install sqlcipher)
or set LIBSQLCIPHER to an existing libsqlcipher.dylib.
EOF
  exit 1
fi

echo "Building SQLCipher from $SRC"
clang -dynamiclib -O2 -DNDEBUG \
  -DSQLITE_HAS_CODEC \
  -DSQLITE_TEMP_STORE=3 \
  -DSQLITE_THREADSAFE=1 \
  -DSQLITE_ENABLE_FTS5 \
  -DSQLITE_OMIT_LOAD_EXTENSION \
  -DSQLITE_OMIT_DEPRECATED \
  -DSQLCIPHER_CRYPTO_CC \
  -DSQLITE_EXTRA_INIT=sqlcipher_extra_init \
  -DSQLITE_EXTRA_SHUTDOWN=sqlcipher_extra_shutdown \
  -I "$SRC" \
  -o "$OUT/libsqlcipher.dylib" "$SRC/sqlite3.c" \
  -framework Security -framework Foundation

echo "built $OUT/libsqlcipher.dylib"
