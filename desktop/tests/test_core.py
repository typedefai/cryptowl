from __future__ import annotations

"""Core compatibility tests for the desktop vault library.

Run with an interpreter that has pycryptodome + argon2-cffi:

    python -m unittest discover -s desktop/tests -v

The vault round-trip test additionally needs libsqlcipher (brew install
sqlcipher) and is skipped otherwise. No PyQt6 is required here.
"""

import os
import secrets
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from cryptowl_desktop.vault import crockford32, cwo1, schema  # noqa: E402
from cryptowl_desktop.vault.crypto import (  # noqa: E402
    aes_gcm_decrypt, aes_gcm_encrypt, hkdf_sha256)
from cryptowl_desktop.vault.vault import (  # noqa: E402
    _canonical_json, key_fingerprint)


class Crockford32Test(unittest.TestCase):
    def test_round_trip_random(self):
        for length in (0, 1, 16, 32, 64):
            raw = secrets.token_bytes(length)
            self.assertEqual(raw, crockford32.decode(crockford32.encode(raw)))

    def test_known_encoding(self):
        # 32 zero bytes -> 52 base32 chars in groups of 5
        encoded = crockford32.encode(bytes(32))
        self.assertTrue(encoded.startswith("00000-00000"))
        self.assertEqual(32, len(crockford32.decode(encoded)))

    def test_aliases_and_case(self):
        self.assertEqual(crockford32.decode("o1lI"), crockford32.decode("0111"))


class HkdfTest(unittest.TestCase):
    def test_rfc5869_case_1(self):
        ikm = bytes.fromhex("0b" * 22)
        salt = bytes.fromhex("000102030405060708090a0b0c")
        info = bytes.fromhex("f0f1f2f3f4f5f6f7f8f9")
        expected = bytes.fromhex(
            "3cb25f25faacd57a90434f64d0362f2a"
            "2d2d0a90cf1a5a4c5db02d56ecc4c5bf"
            "34007208d5b887185865")
        self.assertEqual(expected, hkdf_sha256(ikm, salt, info, 42))

    def test_empty_salt_uses_zero_block(self):
        ikm = b"secret"
        self.assertEqual(hkdf_sha256(ikm, b"", b"", 32),
                         hkdf_sha256(ikm, bytes(32), b"", 32))


class AesGcmTest(unittest.TestCase):
    def test_round_trip_with_aad(self):
        key = secrets.token_bytes(32)
        nonce = secrets.token_bytes(12)
        aad = b"row-id-aad"
        plain = "密鸮 secret payload".encode("utf-8")
        ciphertext, tag = aes_gcm_encrypt(key, nonce, aad, plain)
        self.assertEqual(plain, aes_gcm_decrypt(key, nonce, aad, ciphertext, tag))

    def test_wrong_aad_fails(self):
        key = secrets.token_bytes(32)
        nonce = secrets.token_bytes(12)
        ciphertext, tag = aes_gcm_encrypt(key, nonce, b"a", b"data")
        with self.assertRaises(Exception):
            aes_gcm_decrypt(key, nonce, b"b", ciphertext, tag)


class Cwo1Test(unittest.TestCase):
    def setUp(self):
        self.fek = secrets.token_bytes(32)
        self.aad = b"11111111-2222-3333-4444-555555555555"

    def test_whole_file_round_trip(self):
        plain = secrets.token_bytes(4096)
        blob = cwo1.encrypt_whole_file(self.fek, self.aad, plain)
        self.assertEqual(b"CWO1", blob[:4])
        header = cwo1.parse_header(blob)
        self.assertFalse(header.is_chunked)
        self.assertEqual(plain, cwo1.decrypt_whole_file(self.fek, self.aad, blob))

    def test_chunked_round_trip_and_random_access(self):
        plain = secrets.token_bytes(cwo1.CHUNK_SIZE * 2 + 1234)
        blob = cwo1.encrypt_chunked(self.fek, self.aad, plain)
        header = cwo1.parse_header(blob)
        self.assertTrue(header.is_chunked)
        self.assertEqual(3, header.chunk_count)
        self.assertEqual(plain, cwo1.decrypt_chunked(self.fek, self.aad, blob))
        self.assertEqual(plain[:cwo1.CHUNK_SIZE],
                         cwo1.decrypt_chunk_at(self.fek, self.aad, blob, 0))
        self.assertEqual(plain[-1234:],
                         cwo1.decrypt_chunk_at(self.fek, self.aad, blob, 2))

    def test_streaming_chunked_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = os.path.join(tmp, "source.bin")
            encrypted = os.path.join(tmp, "attachment.cwo")
            restored = os.path.join(tmp, "restored.bin")
            plain = secrets.token_bytes(cwo1.CHUNK_SIZE + 77)
            with open(source, "wb") as f:
                f.write(plain)
            count = cwo1.encrypt_file_chunked(self.fek, self.aad, source, encrypted)
            self.assertEqual(2, count)
            size = cwo1.decrypt_chunked_to_file(self.fek, self.aad, encrypted, restored)
            self.assertEqual(len(plain), size)
            with open(restored, "rb") as f:
                self.assertEqual(plain, f.read())

    def test_wrong_aad_fails(self):
        blob = cwo1.encrypt_whole_file(self.fek, self.aad, b"data")
        with self.assertRaises(Exception):
            cwo1.decrypt_whole_file(self.fek, b"other", blob)


class SchemaTest(unittest.TestCase):
    def test_migration_chain_matches_android(self):
        self.assertEqual([1, 2, 3, 4], [v for v, _ in schema.MIGRATIONS])
        self.assertEqual(4, schema.expected_version())
        for version, sql in schema.MIGRATIONS:
            self.assertIn("PRAGMA foreign_keys = ON", sql, f"v{version}")

    def test_idempotent_transform(self):
        transformed = schema._idempotent("CREATE TABLE t_x (id TEXT);")
        self.assertIn("CREATE TABLE IF NOT EXISTS t_x", transformed)

    def test_scripts_execute_on_plain_sqlite(self):
        """The SQL itself (triggers, indexes, FKs) must be valid; validates the
        embedded copies without needing libsqlcipher."""
        import sqlite3

        class Adapter:
            def __init__(self):
                self.conn = sqlite3.connect(":memory:")

            def exec_(self, sql):
                self.conn.executescript(sql)

            def query_one(self, sql):
                return self.conn.execute(sql).fetchone()

        adapter = Adapter()
        version = schema.apply_migrations(adapter)
        self.assertEqual(4, version)
        # running again is a no-op (user_version gate + IF NOT EXISTS)
        self.assertEqual(4, schema.apply_migrations(adapter))
        tables = {row[0] for row in adapter.conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table'")}
        for expected in ("t_wrapped_key", "t_data_encrypt_key", "t_encrypted_data",
                         "t_file", "t_moment", "t_password", "t_note"):
            self.assertIn(expected, tables)


class CanonicalJsonTest(unittest.TestCase):
    def test_sorted_compact(self):
        self.assertEqual(b'{"a":{"c":3,"d":2},"b":1}',
                         _canonical_json({"b": 1, "a": {"d": 2, "c": 3}}))

    def test_fingerprint_stable_and_short(self):
        key = bytes(range(32))
        self.assertEqual(16, len(key_fingerprint(key)))
        self.assertEqual(key_fingerprint(key), key_fingerprint(bytes(key)))


def _sqlcipher_available() -> bool:
    try:
        from cryptowl_desktop.vault import sqlcipher_db
        sqlcipher_db._load_lib()
        return True
    except Exception:  # noqa: BLE001
        return False


@unittest.skipUnless(_sqlcipher_available(), "libsqlcipher not installed")
class VaultRoundTripTest(unittest.TestCase):
    """End-to-end: create -> open -> notes/media -> reopen -> change password."""

    def test_full_round_trip(self):
        from cryptowl_desktop.vault import MediaRepository, NoteRepository, Vault

        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "personal")
            password = b"desktop-test-password"
            vault = Vault.create(path, password, vault_id="personal",
                                 name="Personal")
            try:
                self.assertTrue(os.path.isfile(os.path.join(path, "device_secret")))
                notes = NoteRepository(vault.conn)
                note_id = notes.create("Hello", "# Desktop\nround trip", pinned=True)
                self.assertEqual(1, len(notes.list()))

                media = MediaRepository(vault.conn)
                file_id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
                blob = cwo1.encrypt_whole_file(
                    vault.file_encryption_key, file_id.encode("utf-8"),
                    b"\x89PNG fake")
                os.makedirs(vault.attachments_dir, exist_ok=True)
                with open(os.path.join(vault.attachments_dir,
                                       f"{file_id}.cwo"), "wb") as f:
                    f.write(blob)
                media.insert(file_id=file_id, storage_name=f"{file_id}.cwo",
                             original_name="fake.png", mime_type="image/png",
                             size_bytes=len(blob))
                self.assertEqual(1, len(media.list()))
            finally:
                vault.close()

            # reopen: integrity + schema + rows survive
            vault = Vault.open(path, password)
            try:
                self.assertEqual("Hello", NoteRepository(vault.conn).get(note_id)["title"])
                self.assertEqual(1, len(MediaRepository(vault.conn).list()))
                entry = MediaRepository(vault.conn).get(file_id)
                with open(os.path.join(vault.attachments_dir, entry["storage_name"]), "rb") as f:
                    plain = cwo1.decrypt_whole_file(
                        vault.file_encryption_key, file_id.encode("utf-8"), f.read())
                self.assertEqual(b"\x89PNG fake", plain)

                # change password: old rejected, new accepted (both MACs re-signed)
                vault.change_master_password(password, b"new-desktop-password")
            finally:
                vault.close()

            with self.assertRaises(Exception):
                Vault.open(path, password)
            reopened = Vault.open(path, b"new-desktop-password")
            try:
                self.assertEqual("Hello",
                                 NoteRepository(reopened.conn).get(note_id)["title"])
            finally:
                reopened.close()


if __name__ == "__main__":
    unittest.main()
