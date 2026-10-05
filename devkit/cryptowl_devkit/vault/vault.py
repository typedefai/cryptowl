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
import secrets
import tempfile
import time
from dataclasses import dataclass
from importlib.resources import files as _resource_files

from . import crockford32
from .crypto import (ARGON2_M_KIB, aes_gcm_decrypt, aes_gcm_encrypt,
                     argon2id_raw, hkdf_sha256, hmac_sha256)
from .sqlcipher import SqlCipherDatabase

META_VERSION = 2
SALT_LEN = 32
DEVICE_SECRET_LEN = 32
WRAPPED_KEY_ID = "vault_key:smk"

KDF_DEFAULTS = {"algorithm": "argon2id", "m_kib": ARGON2_M_KIB, "t": 2, "p": 1}

REQUIRED_FILES = ("vault.meta", "vault.db", "config.json", "config.sig")


class VaultError(Exception):
    """Base for every expected vault-open failure (UI shows str(exc))."""


class NotAVaultError(VaultError):
    """The folder has no vault.meta (or is not a folder at all)."""


class AndroidBoundError(VaultError):
    """No device_secret: the vault is bound to an Android Keystore."""


class WrongPasswordError(VaultError):
    """The master password failed to unwrap the vault key."""


class VaultExistsError(VaultError):
    """Refusing to create a vault over existing vault files."""


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

    # -- create -------------------------------------------------------------

    @staticmethod
    def create(path: str, master_password: bytes, vault_id: str | None = None,
               name: str | None = None) -> "Vault":
        """Create a new desktop-bound vault, byte-exact with vaultlib.

        Writes `device_secret`, config.json + config.sig, vault.meta with the
        wrapped VaultKey, and vault.db with the SQLCipher raw key. Schema comes
        from the vendored migration scripts (currently `v1__init.sql`,
        user_version 1); Android's SchemaApplier applies any newer ones on open.
        """
        path = os.path.abspath(os.path.expanduser(path))
        if not master_password:
            raise VaultError("master password must not be empty")
        vault_id = (vault_id or os.path.basename(path)).strip()
        if (not vault_id or vault_id in (".", "..")
                or os.sep in vault_id or "/" in vault_id):
            raise VaultError("vault id must be a simple folder name")
        name = (name or vault_id).strip() or vault_id
        if (os.path.exists(os.path.join(path, "vault.meta"))
                or os.path.exists(os.path.join(path, "vault.db"))):
            raise VaultExistsError(
                f"'{path}' already contains a vault — refusing to overwrite it")
        os.makedirs(path, exist_ok=True)

        device_secret = secrets.token_bytes(DEVICE_SECRET_LEN)
        _write_device_secret(path, device_secret)

        argon2_salt = secrets.token_bytes(SALT_LEN)
        hkdf_salt = secrets.token_bytes(SALT_LEN)
        meta = {
            "version": META_VERSION,
            "vault_id": vault_id,
            "created_at": _now_ms(),
            "updated_at": _now_ms(),
            "kdf": dict(KDF_DEFAULTS),
            "salts": {
                "argon2": crockford32.encode(argon2_salt),
                "hkdf": crockford32.encode(hkdf_salt),
            },
            "wrapped_keys": [],
        }
        _, smk = _derive_keys(device_secret, master_password, vault_id, meta)

        # config.json + config.sig (written before vault.meta, per design order)
        config_bytes = _canonical_json({"name": name})
        _atomic_write(os.path.join(path, "config.json"), config_bytes)
        _atomic_write(
            os.path.join(path, "config.sig"),
            crockford32.encode(hmac_sha256(smk[32:64], config_bytes)).encode("ascii"))

        # wrap VaultKey with SMK[0:32], AAD = wrapped-key id
        vault_key = secrets.token_bytes(32)
        nonce = secrets.token_bytes(12)
        ciphertext, tag = aes_gcm_encrypt(
            smk[:32], nonce, WRAPPED_KEY_ID.encode("ascii"), vault_key)
        meta["wrapped_keys"].append({
            "id": WRAPPED_KEY_ID,
            "role": "vault_key",
            "wrapper": "smk",
            "algorithm": "AES-256-GCM",
            "ciphertext": crockford32.encode(ciphertext),
            "nonce": crockford32.encode(nonce),
            "auth_tag": crockford32.encode(tag),
        })
        meta["updated_at"] = _now_ms()
        meta["mac"] = {
            "algorithm": "HMAC-SHA256",
            "value": crockford32.encode(
                hmac_sha256(smk[32:64],
                            _canonical_json(_meta_without_mac(meta)))),
        }
        _atomic_write(os.path.join(path, "vault.meta"), _canonical_json(meta))

        # create vault.db with the raw key + vendored schema migrations
        db = SqlCipherDatabase(os.path.join(path, "vault.db"),
                               raw_key=vault_key, readonly=False, create=True)
        try:
            if not db.verify_key():
                raise CorruptVaultError("vault.db raw-key verification failed")
            _apply_migrations(db)
        finally:
            db.close()

        # reopen to verify the whole chain end-to-end
        return Vault.open(path, master_password, device_secret)


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


def _now_ms() -> int:
    return int(time.time() * 1000)


def _atomic_write(path: str, data: bytes) -> None:
    fd, tmp = tempfile.mkstemp(dir=os.path.dirname(path), prefix=".tmp-")
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(data)
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):
            os.remove(tmp)


def _write_device_secret(path: str, secret: bytes) -> None:
    secret_path = os.path.join(path, "device_secret")
    _atomic_write(secret_path, secret.hex().encode("ascii"))
    os.chmod(secret_path, 0o600)


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


# -- schema migrations ------------------------------------------------------
#
# Vendored copies of docs/migrations/*.sql, byte-for-byte mirrors of the
# Android app's assets/migrations/. Applied scripts are immutable: add a new
# vN__<desc>.sql on all three sides instead of editing an existing one. Keep
# them comment-free — SchemaApplier splits on ';' before executing.

MIGRATIONS = [
    (1, "v1__init.sql"),
]


def expected_version() -> int:
    return MIGRATIONS[-1][0] if MIGRATIONS else 0


def _read_migration(filename: str) -> str:
    return (_resource_files("cryptowl_devkit.vault")
            .joinpath("migrations", filename)
            .read_text(encoding="utf-8"))


def _apply_migrations(db: SqlCipherDatabase) -> int:
    """Applies every migration newer than PRAGMA user_version; returns it."""
    current = db.user_version()
    for version, filename in MIGRATIONS:
        if version > current:
            db.exec_(_read_migration(filename))
            db.exec_(f"PRAGMA user_version = {version}")
            current = version
    return current
