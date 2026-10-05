from __future__ import annotations

"""Open a CryptOwl vault (read-only), byte-exact with docs/design.md.

    P    = HMAC-SHA256(key=DeviceSecret, msg=MasterPassword)
    TMK  = Argon2id(P, salt=argon2Salt, m/t/p from vault.meta.kdf)     (32 B)
    SMK  = HKDF-SHA256(ikm=TMK, salt=hkdfSalt, info=vaultId, L=64)
    SMK[0:32]  --AES-256-GCM(AAD="vault_key:smk")--> unwraps VaultKey
    SMK[32:64] = MAC key verifying config.sig and the vault.meta mac
    VaultKey   = SQLCipher RAW KEY of vault.db (32 B, never plaintext)

Desktop Device Secret analog: the `<vault>/device_secret` file (32 bytes, hex,
mode 600). Vaults re-bound to an Android Keystore cannot be opened here.

Step 1 scope: open/verify only — no migrations, no writes, no create.
"""

import hashlib
import hmac
import json
import os
from dataclasses import dataclass

from . import crockford32
from .crypto import (aes_gcm_decrypt, argon2id_raw, hkdf_sha256, hmac_sha256)
from .sqlcipher import SqlCipherDatabase

META_VERSION = 2
WRAPPED_KEY_ID = "vault_key:smk"
DEVICE_SECRET_LEN = 32

REQUIRED_FILES = ("vault.meta", "vault.db", "config.json", "config.sig")


class VaultError(Exception):
    """Base for every expected vault-open failure (UI shows str(exc))."""


class NotAVaultError(VaultError):
    """The folder has no vault.meta (or is not a folder at all)."""


class AndroidBoundError(VaultError):
    """No device_secret: the vault is bound to an Android Keystore."""


class WrongPasswordError(VaultError):
    """The master password failed to unwrap the vault key."""


class CorruptVaultError(VaultError):
    """vault.meta/config/database is missing, malformed, or tampered with."""


def key_fingerprint(key: bytes) -> str:
    """Short stable fingerprint (first 8 bytes of SHA-256, hex)."""
    return hashlib.sha256(key).hexdigest()[:16]


@dataclass(frozen=True)
class VaultFolderStatus:
    """What `inspect_folder` found — the UI's folder detection result."""

    path: str
    exists: bool = False
    is_dir: bool = False
    present: tuple[str, ...] = ()
    missing: tuple[str, ...] = ()
    has_device_secret: bool = False
    vault_id: str | None = None
    meta_version: int | None = None
    error: str | None = None

    @property
    def is_vault(self) -> bool:
        return "vault.meta" in self.present

    @property
    def android_bound(self) -> bool:
        return self.is_vault and self.error is None and not self.has_device_secret

    @property
    def ready(self) -> bool:
        return (self.is_dir and self.is_vault and self.error is None
                and not self.missing and self.has_device_secret)

    @property
    def summary(self) -> str:
        if not self.exists:
            return "Folder does not exist"
        if not self.is_dir:
            return "Not a folder"
        if not self.is_vault:
            return "Not a vault — missing vault.meta"
        if self.error:
            return f"Invalid vault — {self.error}"
        parts = []
        if self.vault_id:
            version = f", meta v{self.meta_version}" if self.meta_version else ""
            parts.append(f"vault '{self.vault_id}'{version}")
        if self.missing:
            parts.append("missing " + ", ".join(self.missing))
        if not self.has_device_secret:
            parts.append("no device_secret (Android-bound, cannot open here)")
        return "Ready — " + "; ".join(parts) if not self.missing and self.has_device_secret \
            else "Problem — " + "; ".join(parts or ["unknown"])


def inspect_folder(path: str) -> VaultFolderStatus:
    """Detect whether `path` is an openable desktop vault (no password needed)."""
    if not (path or "").strip():
        return VaultFolderStatus(path="")
    path = os.path.abspath(os.path.expanduser(path.strip()))
    if not os.path.exists(path):
        return VaultFolderStatus(path=path)
    if not os.path.isdir(path):
        return VaultFolderStatus(path=path, exists=True)

    present = tuple(name for name in REQUIRED_FILES
                    if os.path.isfile(os.path.join(path, name)))
    missing = tuple(name for name in REQUIRED_FILES if name not in present)
    has_secret = os.path.isfile(os.path.join(path, "device_secret"))

    vault_id = None
    meta_version = None
    error = None
    meta_path = os.path.join(path, "vault.meta")
    if os.path.isfile(meta_path):
        try:
            with open(meta_path, encoding="utf-8") as f:
                meta = json.load(f)
            vault_id = meta.get("vault_id")
            meta_version = meta.get("version")
            if not isinstance(meta_version, int):
                error = "vault.meta has no version"
            elif meta_version > META_VERSION:
                error = f"vault.meta version {meta_version} is newer than supported {META_VERSION}"
        except (OSError, ValueError) as exc:
            error = f"vault.meta unreadable ({exc})"

    return VaultFolderStatus(
        path=path,
        exists=True,
        is_dir=True,
        present=present,
        missing=missing,
        has_device_secret=has_secret,
        vault_id=vault_id,
        meta_version=meta_version,
        error=error,
    )


class Vault:
    """An opened vault (read-only in step 1)."""

    def __init__(self, path: str, meta: dict, config: dict,
                 vault_key: bytes, db: SqlCipherDatabase):
        self.path = path
        self.meta = meta
        self.config = config
        self.vault_key = vault_key
        self.db = db

    # -- identity -----------------------------------------------------------

    @property
    def vault_id(self) -> str:
        return self.meta["vault_id"]

    @property
    def name(self) -> str:
        return self.config.get("name") or self.vault_id

    @property
    def db_path(self) -> str:
        return os.path.join(self.path, "vault.db")

    # -- schema / keys ------------------------------------------------------

    @property
    def schema_version(self) -> int:
        return self.db.user_version()

    @property
    def tables(self) -> list[str]:
        return self.db.tables()

    @property
    def file_encryption_key(self) -> bytes:
        """FEK = HKDF-SHA256(VaultKey, salt="", info="file", L=32)."""
        return hkdf_sha256(self.vault_key, b"", b"file", 32)

    @property
    def vault_key_fingerprint(self) -> str:
        return key_fingerprint(self.vault_key)

    @property
    def fek_fingerprint(self) -> str:
        return key_fingerprint(self.file_encryption_key)

    # -- lifecycle ----------------------------------------------------------

    def close(self) -> None:
        self.db.close()

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.close()

    # -- open ---------------------------------------------------------------

    @staticmethod
    def open(path: str, master_password: bytes,
             device_secret: bytes | None = None) -> "Vault":
        """Verify integrity, unwrap the VaultKey, and open `vault.db`."""
        path = os.path.abspath(os.path.expanduser(path))
        meta_path = os.path.join(path, "vault.meta")
        if not os.path.isfile(meta_path):
            raise NotAVaultError(f"not a vault (no vault.meta): {path}")
        try:
            with open(meta_path, encoding="utf-8") as f:
                meta = json.load(f)
        except (OSError, ValueError) as exc:
            raise CorruptVaultError(f"vault.meta unreadable: {exc}") from exc

        version = meta.get("version", 0)
        if not isinstance(version, int) or version > META_VERSION:
            raise CorruptVaultError(
                f"vault.meta version {version} > supported {META_VERSION}")
        vault_id = meta.get("vault_id")
        if not isinstance(vault_id, str) or not vault_id:
            raise CorruptVaultError("vault.meta has no vault_id")

        secret = device_secret if device_secret is not None \
            else _read_device_secret(path)
        try:
            _, smk = _derive_keys(secret, master_password, vault_id, meta)
        except (KeyError, TypeError, ValueError) as exc:
            raise CorruptVaultError(f"vault.meta key material invalid: {exc}") from exc

        try:
            vault_key = _unwrap_vault_key(meta, smk)
        except ValueError as exc:
            raise WrongPasswordError(
                "wrong master password (vault key unwrap failed)") from exc

        _verify_meta_mac(meta, smk[32:64])
        config = _load_and_verify_config(path, smk[32:64])

        db_path = os.path.join(path, "vault.db")
        if not os.path.isfile(db_path):
            raise CorruptVaultError(f"vault database missing: {db_path}")
        db = SqlCipherDatabase(db_path)
        try:
            db.key(vault_key)
            if not db.verify_key():
                raise CorruptVaultError(
                    "wrong master password or corrupt database (key verification failed)")
        except Exception:
            db.close()
            raise

        return Vault(path, meta, config, vault_key, db)


# -- key derivation / verification ------------------------------------------

def _derive_keys(device_secret: bytes, master_password: bytes,
                 vault_id: str, meta: dict):
    """P -> TMK -> SMK; returns (tmk, smk). KDF params come from vault.meta."""
    p = hmac_sha256(device_secret, master_password)
    argon2_salt = crockford32.decode(meta["salts"]["argon2"])
    hkdf_salt = crockford32.decode(meta["salts"]["hkdf"])
    kdf = meta["kdf"]
    tmk = argon2id_raw(p, argon2_salt, m_kib=kdf["m_kib"], t=kdf["t"],
                       p=kdf["p"], hash_len=32)
    smk = hkdf_sha256(tmk, hkdf_salt, vault_id.encode("utf-8"), 64)
    return tmk, smk


def _canonical_json(obj: dict) -> bytes:
    """Canonical JSON: lexicographic keys, compact separators."""
    return json.dumps(obj, sort_keys=True, separators=(",", ":")).encode("utf-8")


def _meta_without_mac(meta: dict) -> dict:
    return {k: v for k, v in meta.items() if k != "mac"}


def _read_device_secret(path: str) -> bytes:
    secret_path = os.path.join(path, "device_secret")
    if not os.path.isfile(secret_path):
        raise AndroidBoundError(
            f"no device_secret in '{path}' — this vault is bound to an Android "
            "Keystore Device Secret (or was re-bound after import), so it can only "
            "be opened on that device.")
    try:
        with open(secret_path, encoding="utf-8") as f:
            secret = bytes.fromhex(f.read().strip())
    except (OSError, ValueError) as exc:
        raise CorruptVaultError(f"device_secret unreadable: {exc}") from exc
    if len(secret) != DEVICE_SECRET_LEN:
        raise CorruptVaultError(
            f"device_secret must be {DEVICE_SECRET_LEN} bytes, got {len(secret)}")
    return secret


def _unwrap_vault_key(meta: dict, smk: bytes) -> bytes:
    for wk in meta.get("wrapped_keys", []):
        if wk.get("id") != WRAPPED_KEY_ID:
            continue
        if wk.get("wrapper") != "smk":
            raise CorruptVaultError(f"{WRAPPED_KEY_ID} must be wrapped by smk")
        return aes_gcm_decrypt(
            smk[:32],
            crockford32.decode(wk["nonce"]),
            WRAPPED_KEY_ID.encode("ascii"),
            crockford32.decode(wk["ciphertext"]),
            crockford32.decode(wk["auth_tag"]),
        )
    raise CorruptVaultError(f"no {WRAPPED_KEY_ID} wrapped key in vault.meta")


def _verify_meta_mac(meta: dict, mac_key: bytes) -> None:
    mac = meta.get("mac")
    if not mac or mac.get("algorithm") != "HMAC-SHA256":
        raise CorruptVaultError("vault.meta missing/invalid mac")
    expected = hmac_sha256(mac_key, _canonical_json(_meta_without_mac(meta)))
    try:
        stored = crockford32.decode(mac["value"])
    except (KeyError, ValueError) as exc:
        raise CorruptVaultError(f"vault.meta mac unreadable: {exc}") from exc
    if not hmac.compare_digest(stored, expected):
        raise CorruptVaultError("vault.meta mac mismatch — metadata tampered")


def _load_and_verify_config(path: str, mac_key: bytes) -> dict:
    config_path = os.path.join(path, "config.json")
    sig_path = os.path.join(path, "config.sig")
    if not os.path.isfile(config_path):
        raise CorruptVaultError(f"missing config.json in vault '{path}'")
    with open(config_path, "rb") as f:
        config_bytes = f.read()
    expected = hmac_sha256(mac_key, config_bytes)
    try:
        with open(sig_path, "r", encoding="ascii") as f:
            stored = crockford32.decode(f.read())
    except OSError as exc:
        raise CorruptVaultError(f"config.sig unreadable: {exc}") from exc
    except ValueError as exc:
        raise CorruptVaultError(f"config.sig invalid: {exc}") from exc
    if not hmac.compare_digest(stored, expected):
        raise CorruptVaultError("config.sig mismatch — vault config tampered")
    try:
        return json.loads(config_bytes.decode("utf-8"))
    except ValueError as exc:
        raise CorruptVaultError(f"config.json invalid: {exc}") from exc
