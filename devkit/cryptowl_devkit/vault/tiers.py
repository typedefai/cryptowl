from __future__ import annotations

"""Per-tier payload encryption (docs/design.md; routing in docs/items.md).

    C  Confidential : payload plaintext inside the SQLCipher DB (no DEK)
    S  Secret       : per-item DEK (AES-256-GCM) wrapped by the KEK
    T  Top Secret   : per-item DEK wrapped by the TopSecretKEK, which is
                      wrapped by the TS-KEK = Argon2id(secondary password,
                      secondarySalt) and prompted per access

Binding differences vs Android (documented dev binding):
    Android wraps the KEK with the BioKey (fingerprint per access); the devkit
    wraps it with the SMK (`t_wrapped_key` role='kek', wrapper='smk'), so S
    tier opens with the vault. T tier still gates on the secondary password.
"""

import secrets
import uuid

from .crypto import aes_gcm_decrypt, aes_gcm_encrypt, argon2id_raw
from .vault import Vault, VaultError, _now_ms

KEK_ID = "kek:smk"
TS_KEK_ID = "top_secret_kek:ts_kek"
SALT_LEN = 32


class SecondaryPasswordError(VaultError):
    """Base for secondary-password failures."""


class SecondaryPasswordNotSet(SecondaryPasswordError):
    """Top-secret access needs a secondary password, none configured yet."""


class BadSecondaryPassword(SecondaryPasswordError):
    """The secondary password failed to unwrap the TopSecretKEK."""


class TierCrypto:
    """Wrapped keys, DEKs and payload encryption for S/T tiers."""

    def __init__(self, vault: Vault):
        self.vault = vault
        self.db = vault.db

    # -- KEK / TopSecretKEK ---------------------------------------------------

    def ensure_kek(self) -> bytes:
        """KEK for the Secret tier (dev binding: wrapped by the SMK)."""
        row = self._wrapped_key("kek", "smk")
        if row is not None:
            return self._unwrap_wrapped_key(row, self.vault.smk[:32], KEK_ID)
        kek = secrets.token_bytes(32)
        self._store_wrapped_key("kek", "smk", kek, self.vault.smk[:32], KEK_ID)
        return kek

    def derive_ts_kek(self, password: bytes) -> bytes:
        salt = self.vault.secondary_salt
        if salt is None:
            raise SecondaryPasswordNotSet(
                "no secondary password is configured")
        kdf = self.vault.meta.get("kdf", {})
        return argon2id_raw(password, salt, m_kib=kdf.get("m_kib", 19456),
                            t=kdf.get("t", 2), p=kdf.get("p", 1), hash_len=32)

    def unwrap_top_secret_kek(self, password: bytes) -> bytes:
        row = self._wrapped_key("top_secret_kek", "ts_kek")
        if row is None:
            raise SecondaryPasswordNotSet("no TopSecretKEK exists yet")
        ts_kek = self.derive_ts_kek(password)
        try:
            return self._unwrap_wrapped_key(row, ts_kek, TS_KEK_ID)
        except Exception as exc:  # noqa: BLE001 - AEAD failure = wrong password
            raise BadSecondaryPassword("wrong secondary password") from exc

    def set_secondary_password(self, password: bytes,
                               old_password: bytes | None = None) -> None:
        """Configure (or re-key) the secondary password and TopSecretKEK."""
        existing = self._wrapped_key("top_secret_kek", "ts_kek")
        top_secret_kek = None
        if existing is not None:
            if old_password is None:
                raise SecondaryPasswordError(
                    "a secondary password is already configured")
            top_secret_kek = self.unwrap_top_secret_kek(old_password)

        salt = secrets.token_bytes(SALT_LEN)
        ts_kek = argon2id_raw(password, salt,
                              m_kib=self.vault.meta.get("kdf", {}).get(
                                  "m_kib", 19456),
                              t=self.vault.meta.get("kdf", {}).get("t", 2),
                              p=self.vault.meta.get("kdf", {}).get("p", 1),
                              hash_len=32)
        if top_secret_kek is None:
            top_secret_kek = secrets.token_bytes(32)
        self.vault.set_secondary_salt(salt)  # re-signs vault.meta
        if existing is not None:
            self._update_wrapped_key(existing, top_secret_kek, ts_kek, TS_KEK_ID)
        else:
            self._store_wrapped_key("top_secret_kek", "ts_kek",
                                    top_secret_kek, ts_kek, TS_KEK_ID)

    # -- DEKs and payloads ----------------------------------------------------

    def encrypt_for_tier(self, plaintext: str, classification: str,
                         ts_password: bytes | None = None,
                         existing_dek_id: str | None = None) -> str:
        """Encrypt `plaintext` for S/T; returns the t_encrypted_data row id."""
        if classification == "S":
            unwrap_key = self.ensure_kek()
        elif classification == "T":
            if ts_password is None:
                raise SecondaryPasswordNotSet(
                    "the secondary password is required for Top Secret items")
            unwrap_key = self.unwrap_top_secret_kek(ts_password)
        else:
            raise ValueError("only S/T payloads are encrypted")

        if existing_dek_id is not None:
            dek_id = existing_dek_id
            dek = self.unwrap_dek(existing_dek_id, unwrap_key)
        else:
            dek_id, dek = self._create_dek(unwrap_key)

        encrypted_data_id = str(uuid.uuid4())
        nonce = secrets.token_bytes(12)
        ciphertext, tag = aes_gcm_encrypt(
            dek, nonce, encrypted_data_id.encode("ascii"),
            plaintext.encode("utf-8"))
        now = _now_ms()
        self.db.execute(
            "INSERT INTO t_encrypted_data (id, dek_id, algorithm, content, "
            "nonce, auth_tag, created_at, updated_at) "
            "VALUES (?, ?, 'AES-256-GCM', ?, ?, ?, ?, ?)",
            (encrypted_data_id, dek_id, ciphertext, nonce, tag, now, now))
        return encrypted_data_id

    def decrypt_payload(self, encrypted_data_id: str, classification: str,
                        ts_password: bytes | None = None) -> str:
        if classification == "S":
            unwrap_key = self.ensure_kek()
        elif classification == "T":
            if ts_password is None:
                raise SecondaryPasswordNotSet(
                    "the secondary password is required for Top Secret items")
            unwrap_key = self.unwrap_top_secret_kek(ts_password)
        else:
            raise ValueError("only S/T payloads are encrypted")

        row = self.db.query_one(
            "SELECT dek_id, content, nonce, auth_tag FROM t_encrypted_data "
            "WHERE id = ? AND deleted_at IS NULL", (encrypted_data_id,))
        if row is None:
            raise VaultError(f"encrypted payload missing: {encrypted_data_id}")
        dek_id, ciphertext, nonce, tag = row
        dek = self.unwrap_dek(dek_id, unwrap_key)
        plaintext = aes_gcm_decrypt(
            dek, nonce, encrypted_data_id.encode("ascii"), ciphertext, tag)
        return plaintext.decode("utf-8")

    def item_dek_id(self, encrypted_data_id: str | None) -> str | None:
        if not encrypted_data_id:
            return None
        row = self.db.query_one(
            "SELECT dek_id FROM t_encrypted_data WHERE id = ?",
            (encrypted_data_id,))
        return row[0] if row else None

    # -- garbage collection ---------------------------------------------------

    def gc(self) -> None:
        """Drop payload rows no item/version references, then unused DEKs."""
        self.db.execute(
            "DELETE FROM t_encrypted_data WHERE id NOT IN ("
            "  SELECT encrypted_data_id FROM t_item "
            "  WHERE encrypted_data_id IS NOT NULL"
            ") AND id NOT IN ("
            "  SELECT encrypted_data_id FROM t_item_version "
            "  WHERE encrypted_data_id IS NOT NULL)")
        self.db.execute(
            "DELETE FROM t_data_encrypt_key WHERE id NOT IN ("
            "  SELECT dek_id FROM t_encrypted_data)")

    # -- internals ------------------------------------------------------------

    def _create_dek(self, wrapping_key: bytes) -> tuple[str, bytes]:
        dek = secrets.token_bytes(32)
        dek_id = str(uuid.uuid4())
        nonce = secrets.token_bytes(12)
        ciphertext, tag = aes_gcm_encrypt(
            wrapping_key, nonce, dek_id.encode("ascii"), dek)
        now = _now_ms()
        self.db.execute(
            "INSERT INTO t_data_encrypt_key (id, algorithm, ciphertext, "
            "nonce, auth_tag, created_at, updated_at) "
            "VALUES (?, 'AES-256-GCM', ?, ?, ?, ?, ?)",
            (dek_id, ciphertext, nonce, tag, now, now))
        return dek_id, dek

    def unwrap_dek(self, dek_id: str, wrapping_key: bytes) -> bytes:
        row = self.db.query_one(
            "SELECT ciphertext, nonce, auth_tag FROM t_data_encrypt_key "
            "WHERE id = ?", (dek_id,))
        if row is None:
            raise VaultError(f"wrapped DEK missing: {dek_id}")
        ciphertext, nonce, tag = row
        return aes_gcm_decrypt(wrapping_key, nonce, dek_id.encode("ascii"),
                               ciphertext, tag)

    def _wrapped_key(self, role: str, wrapper: str):
        return self.db.query_one(
            "SELECT id, ciphertext, nonce, auth_tag FROM t_wrapped_key "
            "WHERE role = ? AND wrapper = ? AND deleted_at IS NULL "
            "ORDER BY updated_at DESC LIMIT 1", (role, wrapper))

    def _unwrap_wrapped_key(self, row, wrapping_key: bytes,
                            aad_id: str) -> bytes:
        _id, ciphertext, nonce, tag = row
        return aes_gcm_decrypt(wrapping_key, nonce, aad_id.encode("ascii"),
                               ciphertext, tag)

    def _store_wrapped_key(self, role: str, wrapper: str, secret: bytes,
                           wrapping_key: bytes, aad_id: str) -> None:
        nonce = secrets.token_bytes(12)
        ciphertext, tag = aes_gcm_encrypt(
            wrapping_key, nonce, aad_id.encode("ascii"), secret)
        now = _now_ms()
        self.db.execute(
            "INSERT INTO t_wrapped_key (id, role, wrapper, algorithm, "
            "ciphertext, nonce, auth_tag, created_at, updated_at) "
            "VALUES (?, ?, ?, 'AES-256-GCM', ?, ?, ?, ?, ?)",
            (str(uuid.uuid4()), role, wrapper, ciphertext, nonce, tag,
             now, now))

    def _update_wrapped_key(self, row, secret: bytes, wrapping_key: bytes,
                            aad_id: str) -> None:
        nonce = secrets.token_bytes(12)
        ciphertext, tag = aes_gcm_encrypt(
            wrapping_key, nonce, aad_id.encode("ascii"), secret)
        self.db.execute(
            "UPDATE t_wrapped_key SET ciphertext = ?, nonce = ?, auth_tag = ?, "
            "updated_at = ? WHERE id = ?",
            (ciphertext, nonce, tag, _now_ms(), row[0]))
