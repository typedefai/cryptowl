from __future__ import annotations

"""Minimal read-only ctypes binding to libsqlcipher (SQLCipher 4).

The vault key is passed as the RAW KEY expression `x'<hex>'` via
`sqlite3_key_v2` — the exact mode the Android app uses
(`vault/SqlCipher.kt::asSqlCipherRawKey`). Passing the bare 32 bytes as a blob
would make SQLCipher treat it as a passphrase (PBKDF2), which "works" only for
vaults created the same way and fails on vaults created elsewhere (AGENTS.md).

Scope: open/key/query/exec/close. No parameter binding yet.
"""

import ctypes
import ctypes.util
import os
import sys

SQLITE_OK = 0
SQLITE_ROW = 100
SQLITE_DONE = 101

SQLITE_OPEN_READONLY = 0x00000001
SQLITE_OPEN_READWRITE = 0x00000002
SQLITE_OPEN_CREATE = 0x00000004

SQLITE_INTEGER = 1
SQLITE_FLOAT = 2
SQLITE_TEXT = 3
SQLITE_BLOB = 4
SQLITE_NULL = 5

_STMT = ctypes.c_void_p


class SqlCipherError(Exception):
    pass


class SqlCipherDatabase:
    """A SQLCipher database handle; use as a context manager."""

    def __init__(self, path: str, raw_key: bytes | None = None,
                 readonly: bool = True, create: bool = False):
        _load_lib()
        self.path = path
        if not os.path.isfile(path) and not create:
            raise FileNotFoundError(f"database not found: {path}")
        if create:
            flags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE
        else:
            flags = SQLITE_OPEN_READONLY if readonly else SQLITE_OPEN_READWRITE
        db = ctypes.c_void_p()
        rc = _lib.sqlite3_open_v2(path.encode("utf-8"), ctypes.byref(db),
                                  flags, None)
        if rc != SQLITE_OK or not db:
            raise SqlCipherError(
                f"sqlite3_open_v2 failed ({rc}): {self._errmsg(db)}")
        self._db = db
        if raw_key is not None:
            self.key(raw_key)

    # -- key ----------------------------------------------------------------

    def key(self, raw_key: bytes) -> None:
        """Set the SQLCipher RAW key (bypasses the passphrase PBKDF2)."""
        if len(raw_key) != 32:
            raise ValueError("VaultKey raw key must be 32 bytes")
        expr = b"x'" + raw_key.hex().encode("ascii") + b"'"
        rc = _lib.sqlite3_key_v2(self._db, b"main", expr, len(expr))
        if rc != SQLITE_OK:
            raise SqlCipherError(f"sqlite3_key_v2 failed ({rc}): {self.errmsg()}")

    # -- statements ---------------------------------------------------------

    def exec_(self, sql: str) -> None:
        """Execute raw SQL (one or more statements, no result rows)."""
        err = ctypes.c_char_p()
        rc = _lib.sqlite3_exec(self._db, sql.encode("utf-8"), None, None,
                               ctypes.byref(err))
        if rc != SQLITE_OK:
            msg = err.value.decode("utf-8") if err.value else self.errmsg()
            if err.value:
                _lib.sqlite3_free(err)
            raise SqlCipherError(msg)

    # -- queries ------------------------------------------------------------

    def query(self, sql: str) -> list[tuple]:
        stmt = _STMT()
        rc = _lib.sqlite3_prepare_v2(self._db, sql.encode("utf-8"), -1,
                                     ctypes.byref(stmt), None)
        if rc != SQLITE_OK:
            raise SqlCipherError(f"sqlite3_prepare_v2: {self.errmsg()}")
        try:
            rows = []
            while True:
                step = _lib.sqlite3_step(stmt)
                if step == SQLITE_ROW:
                    rows.append(self._row(stmt))
                elif step == SQLITE_DONE:
                    break
                else:
                    raise SqlCipherError(f"sqlite3_step: {self.errmsg()}")
            return rows
        finally:
            _lib.sqlite3_finalize(stmt)

    def query_one(self, sql: str) -> tuple | None:
        rows = self.query(sql)
        return rows[0] if rows else None

    # -- introspection ------------------------------------------------------

    def verify_key(self) -> bool:
        """True if the current key opens the database."""
        try:
            self.query_one("SELECT count(*) FROM sqlite_master")
            return True
        except SqlCipherError:
            return False

    def user_version(self) -> int:
        row = self.query_one("PRAGMA user_version")
        return int(row[0]) if row else 0

    def tables(self) -> list[str]:
        rows = self.query(
            "SELECT name FROM sqlite_master "
            "WHERE type IN ('table', 'view') AND name NOT LIKE 'sqlite_%' "
            "ORDER BY name")
        return [r[0] for r in rows]

    def row_count(self, table: str) -> int:
        row = self.query_one(f'SELECT COUNT(*) FROM "{table}"')
        return int(row[0]) if row else 0

    # -- internals ----------------------------------------------------------

    def _row(self, stmt) -> tuple:
        values = []
        for i in range(_lib.sqlite3_column_count(stmt)):
            ctype = _lib.sqlite3_column_type(stmt, i)
            if ctype == SQLITE_INTEGER:
                values.append(_lib.sqlite3_column_int64(stmt, i))
            elif ctype == SQLITE_FLOAT:
                values.append(_lib.sqlite3_column_double(stmt, i))
            elif ctype == SQLITE_TEXT:
                ptr = _lib.sqlite3_column_text(stmt, i)
                size = _lib.sqlite3_column_bytes(stmt, i)
                values.append(ctypes.string_at(ptr, size).decode("utf-8"))
            elif ctype == SQLITE_BLOB:
                ptr = _lib.sqlite3_column_blob(stmt, i)
                size = _lib.sqlite3_column_bytes(stmt, i)
                values.append(ctypes.string_at(ptr, size))
            else:
                values.append(None)
        return tuple(values)

    def _errmsg(self, db=None) -> str:
        ptr = _lib.sqlite3_errmsg(db or self._db)
        return ctypes.string_at(ptr).decode("utf-8") if ptr else "unknown error"

    errmsg = _errmsg

    def close(self) -> None:
        if getattr(self, "_db", None):
            _lib.sqlite3_close_v2(self._db)
            self._db = None

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.close()


_lib = None


def _candidate_library_paths():
    """LIBSQLCIPHER, the devkit-local native/ build, then OS package managers."""
    env = os.environ.get("LIBSQLCIPHER")
    if env:
        yield env
    here = os.path.dirname(os.path.abspath(__file__))
    project = os.path.abspath(os.path.join(here, "..", ".."))
    names = ("libsqlcipher.dylib",) if sys.platform == "darwin" \
        else ("libsqlcipher.so",)
    for name in names:
        yield os.path.join(project, "native", name)
    yield "/opt/homebrew/opt/sqlcipher/lib/libsqlcipher.dylib"
    yield "/usr/local/opt/sqlcipher/lib/libsqlcipher.dylib"
    yield "/opt/local/lib/libsqlcipher.dylib"
    yield "/usr/local/lib/libsqlcipher.dylib"


def _load_lib():
    global _lib
    if _lib is not None:
        return _lib
    path = next((p for p in _candidate_library_paths()
                 if p and os.path.exists(p)), None)
    if not path:
        path = ctypes.util.find_library("sqlcipher")
    if not path:
        raise ImportError(
            "libsqlcipher not found. Run `make native` in the devkit folder "
            "(builds the pinned SQLCipher submodule), install SQLCipher "
            "(`brew install sqlcipher` / `port install sqlcipher`), or set "
            "LIBSQLCIPHER to the library path.")
    lib = ctypes.cdll.LoadLibrary(path)
    for name, argtypes, restype in [
        ("sqlite3_open_v2", [ctypes.c_char_p, ctypes.POINTER(ctypes.c_void_p),
                             ctypes.c_int, ctypes.c_void_p], ctypes.c_int),
        ("sqlite3_key_v2", [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_void_p,
                            ctypes.c_int], ctypes.c_int),
        ("sqlite3_exec", [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_void_p,
                          ctypes.c_void_p, ctypes.POINTER(ctypes.c_char_p)],
         ctypes.c_int),
        ("sqlite3_free", [ctypes.c_void_p], None),
        ("sqlite3_prepare_v2", [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int,
                                ctypes.POINTER(_STMT), ctypes.c_void_p],
         ctypes.c_int),
        ("sqlite3_step", [_STMT], ctypes.c_int),
        ("sqlite3_finalize", [_STMT], ctypes.c_int),
        ("sqlite3_column_count", [_STMT], ctypes.c_int),
        ("sqlite3_column_type", [_STMT, ctypes.c_int], ctypes.c_int),
        ("sqlite3_column_int64", [_STMT, ctypes.c_int], ctypes.c_int64),
        ("sqlite3_column_double", [_STMT, ctypes.c_int], ctypes.c_double),
        ("sqlite3_column_text", [_STMT, ctypes.c_int], ctypes.c_void_p),
        ("sqlite3_column_blob", [_STMT, ctypes.c_int], ctypes.c_void_p),
        ("sqlite3_column_bytes", [_STMT, ctypes.c_int], ctypes.c_int),
        ("sqlite3_errmsg", [ctypes.c_void_p], ctypes.c_char_p),
        ("sqlite3_close_v2", [ctypes.c_void_p], ctypes.c_int),
    ]:
        fn = getattr(lib, name)
        fn.argtypes = argtypes
        fn.restype = restype
    _lib = lib
    return _lib
