from __future__ import annotations

"""Backend tests for catalog / grid / values / files (Qt-free)."""

import os
import secrets
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from cryptowl_desktop.vault import catalog, grid, schema, values  # noqa: E402


def _sqlcipher_conn():
    try:
        from cryptowl_desktop.vault import sqlcipher_db
        sqlcipher_db._load_lib()
        from cryptowl_desktop.vault.sqlcipher_db import SqlCipherConnection
        from cryptowl_desktop.vault.vault import Vault
        return SqlCipherConnection, Vault
    except Exception:  # noqa: BLE001
        return None


def _sqlite3_adapter():
    """stdlib adapter so catalog/grid logic is testable without libsqlcipher."""
    import sqlite3

    class Adapter:
        def __init__(self):
            self.conn = sqlite3.connect(":memory:")

        def exec_(self, sql):
            self.conn.executescript(sql)

        def execute(self, sql, params=()):
            self.conn.execute(sql, params)
            self.conn.commit()

        def query(self, sql, params=()):
            return self.conn.execute(sql, params).fetchall()

        def query_one(self, sql, params=()):
            return self.conn.execute(sql, params).fetchone()

        def query2(self, sql, params=()):
            cur = self.conn.execute(sql, params)
            columns = [d[0] for d in cur.description]
            return columns, cur.fetchall()

        def row_count(self, table):
            return self.conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]

        def close(self):
            self.conn.close()

    return Adapter()


class CatalogGridTest(unittest.TestCase):
    def setUp(self):
        self.conn = _sqlite3_adapter()
        schema.apply_migrations(self.conn)
        self.conn.execute(
            "INSERT INTO t_data_encrypt_key (id, algorithm, ciphertext, nonce, auth_tag, created_at, updated_at)"
            " VALUES ('d1', 'AES-256-GCM', x'00', x'000000000000000000000000', x'00000000000000000000000000000000', 1, 2)")
        self.conn.execute(
            "INSERT INTO t_encrypted_data (id, dek_id, algorithm, content, nonce, auth_tag, created_at, updated_at)"
            " VALUES ('e1', 'd1', 'AES-256-GCM', x'00', x'000000000000000000000000', x'00000000000000000000000000000000', 1, 2)")
        self.conn.execute(
            "INSERT INTO t_note (id, classification, title, content, pinned, created_at, updated_at)"
            " VALUES ('n1', 'C', 'Hello', '# hi', 1, 1700000000000, 1700001000000)")
        self.conn.execute(
            "INSERT INTO t_password (id, classification, title, encrypted_data_id, created_at, updated_at)"
            " VALUES ('p1', 'S', 'GitHub', 'e1', 1, 2)")

    def tearDown(self):
        self.conn.close()

    def test_catalog_lists_tables_with_counts_and_ddl(self):
        tables = catalog.tables(self.conn)
        by_name = {t["name"]: t for t in tables}
        self.assertEqual(1, by_name["t_note"]["row_count"])
        self.assertEqual(1, by_name["t_password"]["row_count"])
        self.assertIn("t_note", by_name["t_note"]["ddl"])

    def test_table_info_and_foreign_keys(self):
        info = catalog.table_info(self.conn, "t_password")
        names = [c["name"] for c in info]
        self.assertIn("encrypted_data_id", names)
        fks = catalog.foreign_keys(self.conn, "t_password")
        targets = {(fk["table"], fk["from_col"]) for fk in fks}
        self.assertIn(("t_encrypted_data", "encrypted_data_id"), targets)

    def test_grid_paging_and_total(self):
        conn = self.conn
        for i in range(150):
            conn.execute(
                "INSERT INTO t_note (id, classification, title, content, created_at, updated_at)"
                " VALUES (?, 'C', ?, 'x', 1, 1)", (f"nn{i:03d}", f"note {i}"))
        g = grid.Grid(conn, "t_note", page_size=100)
        columns, rows, total = g.fetch(offset=0)
        self.assertEqual(100, len(rows))
        self.assertEqual(151, total)
        _, rows2, _ = g.fetch(offset=100)
        self.assertEqual(51, len(rows2))

    def test_grid_filter_like_and_exact(self):
        g = grid.Grid(self.conn, "t_note", page_size=10)
        _, rows, total = g.fetch(filter_text="Hello")
        self.assertEqual(1, total)
        exact = grid.parse_filter("title = Hello")
        self.assertEqual(("title", "Hello"), exact)
        _, rows, total = g.fetch(filter_text="title = Hello")
        self.assertEqual(1, total)
        self.assertEqual("Hello", rows[0][2])
        # quoted value form
        exact = grid.parse_filter("title = 'Hello'")
        self.assertEqual(("title", "Hello"), exact)
        # non-matching LIKE
        _, _, total = g.fetch(filter_text="zzz")
        self.assertEqual(0, total)

    def test_grid_order_and_unsafe_names(self):
        g = grid.Grid(self.conn, "t_note", page_size=10)
        columns, rows, _ = g.fetch(order_by="updated_at", order_desc=True)
        self.assertIn("updated_at", columns)
        with self.assertRaises(ValueError):
            grid.Grid(self.conn, "t_note; DROP TABLE t_note")
        with self.assertRaises(ValueError):
            g.fetch(order_by="updated_at; --")


class ValuesTest(unittest.TestCase):
    def test_describe_and_hex_dump(self):
        self.assertEqual("NULL", values.describe(None))
        self.assertEqual("<blob 4B>", values.describe(b"abcd"))
        self.assertIn("abcd", values.hex_dump(b"abcd"))
        import datetime as _dt
        expected = _dt.datetime.fromtimestamp(1700000000000 / 1000).strftime("%Y-%m-%d %H:%M:%S")
        self.assertEqual(expected, values.fmt_ms(1700000000000))
        self.assertTrue(values.fmt_size(2048).endswith("KB"))

    def test_cwo1_summary_and_json(self):
        from cryptowl_desktop.vault import cwo1
        blob = cwo1.encrypt_chunked(b"k" * 32, b"aad", b"x" * (cwo1.CHUNK_SIZE + 5))
        summary = values.cwo1_summary(blob)
        self.assertTrue(summary["chunked"])
        self.assertEqual(2, summary["chunk_count"])
        self.assertEqual(None, values.pretty_json_if("nope"))
        pretty = values.pretty_json_if('{"b":1,"a":2}')
        self.assertTrue(pretty.startswith("{"))
        self.assertIn('"a": 2', pretty)

    def test_base32_round_trip(self):
        from cryptowl_desktop.vault import crockford32
        raw = secrets.token_bytes(32)
        self.assertEqual(raw, crockford32.decode(values.base32(raw)))


class FilesGridRoundTrip(unittest.TestCase):

    def test_files_helpers(self):
        lib = _sqlcipher_conn()
        if lib is None:
            self.skipTest("libsqlcipher not installed")
        SqlCipherConnection, Vault = lib
        from cryptowl_desktop.vault import files as filesv
        from cryptowl_desktop.vault import cwo1

        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "personal")
            vault = Vault.create(path, b"grid-test-password", vault_id="personal")
            try:
                file_id = "11111111-2222-3333-4444-555555555555"
                aad = file_id.encode("utf-8")
                attachment = os.path.join(vault.attachments_dir, f"{file_id}.cwo")
                os.makedirs(vault.attachments_dir, exist_ok=True)
                plain = secrets.token_bytes(1024)
                with open(attachment, "wb") as f:
                    f.write(cwo1.encrypt_whole_file(vault.file_encryption_key, aad, plain))
                listing = filesv.list_files(vault, "attachments")
                self.assertEqual(1, len(listing))
                self.assertFalse(listing[0]["cwo1"]["chunked"])
                got = filesv.decrypt_bytes(vault, file_id, attachment)
                self.assertEqual(plain, got)
            finally:
                vault.close()


if __name__ == "__main__":
    unittest.main()
