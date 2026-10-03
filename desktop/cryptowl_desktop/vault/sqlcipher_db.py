from __future__ import annotations

"""Minimal ctypes binding to libsqlcipher (SQLCipher 4).

Vendored from wechat_sns_export/vaultlib and extended with prepared-statement
parameter binding (`execute`) and lazy library loading, so importing the vault
package (tests, debug tooling) does not require libsqlcipher until a connection
is actually opened.

Why ctypes instead of pysqlcipher3/sqlcipher3: no pip wheel exists for
Python 3.9 on macOS x86_64, and the binding must let us pass the vault key as
a RAW KEY ("x'hex'" form) — the exact mode the Android side uses per the
design (VaultKey as the SQLCipher raw key). Same SQLCipher version on both
sides (4.17.0) guarantees identical page format/derivation behavior.

This wrapper deliberately exposes only what the vault tooling needs:
open with raw key, exec, query, parameter binding via SQL string interpolation
(values are hex-encoded/escaped by callers).
"""

import ctypes
import ctypes.util
import os

SQLITE_OK = 0
SQLITE_ROW = 100
SQLITE_DONE = 101

SQLITE_OPEN_READWRITE = 0x00000002
SQLITE_OPEN_CREATE = 0x00000004
SQLITE_OPEN_NOMUTEX = 0x00008000

_STMT = ctypes.c_void_p

# SQLite copies bound values immediately when the destructor is SQLITE_TRANSIENT.
SQLITE_TRANSIENT = ctypes.c_void_p(-1)


class SqlCipherError(Exception):
    pass


class SqlCipherConnection:
    """A SQLCipher database handle; use as a context manager."""

    def __init__(self, path: str, raw_key: bytes | None = None,
                 create: bool = False):
        _load_lib()
        self.path = path
        if not os.path.isfile(path) and not create:
            raise FileNotFoundError(f"database not found: {path}")

        flags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_NOMUTEX
        if create:
            flags |= SQLITE_OPEN_CREATE

        db = ctypes.c_void_p()
        rc = _lib.sqlite3_open_v2(path.encode("utf-8"), ctypes.byref(db),
                                  flags, None)
        if rc != SQLITE_OK or not db:
            raise SqlCipherError(f"sqlite3_open_v2 failed ({rc}): {self.errmsg(db)}")
        self._db = db

        if raw_key is not None:
            self.key(raw_key)

    # -- key ----------------------------------------------------------------

    def key(self, raw_key: bytes) -> None:
        """Set the SQLCipher key as a RAW KEY (bypasses passphrase PBKDF2).

        Equivalent to `PRAGMA key = "x'<hex>'"` on the SQLCipher CLI / the
        Android side passing the x'...' form to its SQLCipher factory.
        """
        if len(raw_key) != 32:
            raise ValueError("VaultKey raw key must be 32 bytes")
        expr = b"x'" + raw_key.hex().encode("ascii") + b"'"
        rc = _lib.sqlite3_key_v2(self._db, b"main", expr, len(expr))
        if rc != SQLITE_OK:
            raise SqlCipherError(f"sqlite3_key_v2 failed ({rc}): {self.errmsg()}")

    # -- statements ----------------------------------------------------------

    def exec_(self, sql: str) -> None:
        err = ctypes.c_char_p()
        rc = _lib.sqlite3_exec(self._db, sql.encode("utf-8"), None, None,
                               ctypes.byref(err))
        if rc != SQLITE_OK:
            msg = err.value.decode("utf-8") if err.value else self.errmsg()
            if err.value:
                _lib.sqlite3_free(err)
            raise SqlCipherError(msg)

    def query(self, sql: str, row_factory=None):
        """Run a SELECT and return all rows (tuple per row, or via factory)."""
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
                    rows.append(self._row(stmt, row_factory))
                elif step == SQLITE_DONE:
                    break
                else:
                    raise SqlCipherError(f"sqlite3_step: {self.errmsg()}")
            return rows
        finally:
            _lib.sqlite3_finalize(stmt)

    def execute(self, sql: str, params=()) -> None:
        """Run a write statement with bound parameters (no SQL interpolation)."""
        stmt = _STMT()
        rc = _lib.sqlite3_prepare_v2(self._db, sql.encode("utf-8"), -1,
                                     ctypes.byref(stmt), None)
        if rc != SQLITE_OK:
            raise SqlCipherError(f"sqlite3_prepare_v2: {self.errmsg()}")
        try:
            for index, value in enumerate(params, start=1):
                self._bind(stmt, index, value)
            while True:
                step = _lib.sqlite3_step(stmt)
                if step == SQLITE_DONE:
                    break
                if step != SQLITE_ROW:
                    raise SqlCipherError(f"sqlite3_step: {self.errmsg()}")
        finally:
            _lib.sqlite3_finalize(stmt)

    def _bind(self, stmt, index: int, value) -> None:
        if value is None:
            _lib.sqlite3_bind_null(stmt, index)
        elif isinstance(value, bool):
            _lib.sqlite3_bind_int64(stmt, index, 1 if value else 0)
        elif isinstance(value, int):
            _lib.sqlite3_bind_int64(stmt, index, value)
        elif isinstance(value, float):
            _lib.sqlite3_bind_double(stmt, index, value)
        elif isinstance(value, (bytes, bytearray, memoryview)):
            data = bytes(value)
            _lib.sqlite3_bind_blob(stmt, index, ctypes.c_char_p(data),
                                   len(data), SQLITE_TRANSIENT)
        else:
            data = str(value).encode("utf-8")
            _lib.sqlite3_bind_text(stmt, index, ctypes.c_char_p(data),
                                   len(data), SQLITE_TRANSIENT)

    def query_one(self, sql: str):
        rows = self.query(sql)
        return rows[0] if rows else None

    # -- introspection ---------------------------------------------------------

    def tables(self) -> list[str]:
        rows = self.query(
            "SELECT name FROM sqlite_master "
            "WHERE type IN ('table', 'view') AND name NOT LIKE 'sqlite_%' "
            "ORDER BY name")
        return [r[0] for r in rows]

    def row_count(self, table: str) -> int:
        row = self.query_one(f"SELECT COUNT(*) FROM \"{table}\"")
        return int(row[0]) if row else 0

    def verify_key(self) -> bool:
        """True if the current key opens the database."""
        try:
            self.query_one("SELECT count(*) FROM sqlite_master")
            return True
        except SqlCipherError:
            return False

    # -- internals ---------------------------------------------------------------

    def _row(self, stmt, factory=None):
        ncols = _lib.sqlite3_column_count(stmt)
        values = []
        for i in range(ncols):
            ctype = _lib.sqlite3_column_type(stmt, i)
            if ctype == 1:  # SQLITE_INTEGER
                values.append(_lib.sqlite3_column_int64(stmt, i))
            elif ctype == 2:  # SQLITE_FLOAT
                values.append(_lib.sqlite3_column_double(stmt, i))
            elif ctype == 3:  # SQLITE_TEXT
                ptr = _lib.sqlite3_column_text(stmt, i)
                size = _lib.sqlite3_column_bytes(stmt, i)
                values.append(ctypes.string_at(ptr, size).decode("utf-8"))
            elif ctype == 4:  # SQLITE_BLOB
                ptr = _lib.sqlite3_column_blob(stmt, i)
                size = _lib.sqlite3_column_bytes(stmt, i)
                values.append(ctypes.string_at(ptr, size))
            else:
                values.append(None)
        if factory:
            return factory(values)
        return tuple(values)

    def errmsg(self, db=None) -> str:
        ptr = _lib.sqlite3_errmsg(db or self._db)
        return ctypes.string_at(ptr).decode("utf-8") if ptr else "unknown error"

    def close(self) -> None:
        if self._db:
            _lib.sqlite3_close_v2(self._db)
            self._db = None

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.close()


_lib = None


def _candidate_library_paths():
    """LIBSQLCIPHER, the app-local native/ build, then OS package managers."""
    env = os.environ.get("LIBSQLCIPHER")
    if env:
        yield env
    here = os.path.dirname(os.path.abspath(__file__))
    desktop = os.path.abspath(os.path.join(here, "..", ".."))
    yield os.path.join(desktop, "native", "libsqlcipher.dylib")
    yield os.path.join(desktop, ".native", "libsqlcipher.dylib")
    yield os.path.join(here, "libsqlcipher.dylib")
    # keg-only Homebrew / MacPorts / common install prefixes
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
            "libsqlcipher not found. Build a local copy with "
            "`scripts/build_sqlcipher.sh`, install SQLCipher "
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
        ("sqlite3_free", [ctypes.c_void_p], None),
        ("sqlite3_bind_null", [_STMT, ctypes.c_int], ctypes.c_int),
        ("sqlite3_bind_int64", [_STMT, ctypes.c_int, ctypes.c_int64], ctypes.c_int),
        ("sqlite3_bind_double", [_STMT, ctypes.c_int, ctypes.c_double], ctypes.c_int),
        ("sqlite3_bind_text", [_STMT, ctypes.c_int, ctypes.c_char_p, ctypes.c_int,
                               ctypes.c_void_p], ctypes.c_int),
        ("sqlite3_bind_blob", [_STMT, ctypes.c_int, ctypes.c_char_p, ctypes.c_int,
                               ctypes.c_void_p], ctypes.c_int),
    ]:
        fn = getattr(lib, name)
        fn.argtypes = argtypes
        fn.restype = restype
    _lib = lib
    return _lib

