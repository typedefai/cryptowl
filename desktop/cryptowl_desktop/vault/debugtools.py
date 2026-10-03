from __future__ import annotations

"""Debug/introspection helpers for the desktop tool.

All values are safe to display: keys appear as short SHA-256 fingerprints
(mirroring Android's `CryptoLog`), salts/nonces/macs as truncated hex. Raw key
bytes are never surfaced by the UI.
"""

import os

from . import crockford32
from .repositories import table_counts
from .schema import expected_version
from .vault import key_fingerprint


def _fp(data: bytes, nbytes: int = 8) -> str:
    return data[:nbytes].hex() + "…"


def _salt_fp(encoded) -> str:
    try:
        raw = crockford32.decode(encoded)
    except Exception:  # noqa: BLE001 - corrupt meta is exactly what we report
        return f"<undecodable: {encoded[:12]}…>"
    return f"{raw[:8].hex()}… ({len(raw)} B)"


def vault_report(vault) -> list:
    """Ordered (section, label, value) rows for the debug panel."""
    rows = []

    def add(section, label, value):
        rows.append((section, label, str(value)))

    add("Vault", "path", vault.path)
    add("Vault", "vault_id", vault.vault_id)
    add("Vault", "meta version", vault.meta.get("version"))
    add("Vault", "config.name", vault.config.get("name"))
    add("Vault", "created_at", vault.meta.get("created_at"))
    add("Vault", "updated_at", vault.meta.get("updated_at"))

    kdf = vault.meta.get("kdf", {})
    add("KDF", "algorithm", kdf.get("algorithm"))
    add("KDF", "memory", f"{kdf.get('m_kib')} KiB")
    add("KDF", "iterations t", kdf.get("t"))
    add("KDF", "parallelism p", kdf.get("p"))

    for name, encoded in (vault.meta.get("salts") or {}).items():
        add("Salts (public)", name, _salt_fp(encoded))

    for wk in vault.meta.get("wrapped_keys", []):
        try:
            nonce = crockford32.decode(wk.get("nonce", ""))
            nonce_txt = _fp(nonce, 8)
        except Exception:  # noqa: BLE001
            nonce_txt = "<bad nonce>"
        add("Wrapped keys", wk.get("id"),
            f"wrapper={wk.get('wrapper')} algo={wk.get('algorithm')} nonce={nonce_txt}")

    mac = (vault.meta.get("mac") or {}).get("value", "")
    add("Integrity", "vault.meta mac", f"{mac[:12]}…" if mac else "<missing>")
    add("Integrity", "config.sig", "verified on open (HMAC-SHA256)")
    add("Integrity", "expected schema", f"v{expected_version()}")
    add("Integrity", "db user_version", _user_version(vault))

    add("Keys", "VaultKey fingerprint", key_fingerprint(vault.vault_key))
    add("Keys", "FEK fingerprint", vault.fek_fingerprint)
    try:
        add("Keys", "Device secret fingerprint", vault.device_secret_fingerprint())
    except Exception as exc:  # noqa: BLE001
        add("Keys", "Device secret fingerprint", f"n/a ({exc})")

    return rows


def file_stats(vault) -> list:
    rows = []
    for label, path in (("attachments", vault.attachments_dir),
                        ("thumbnails", vault.thumbnails_dir)):
        count = 0
        total = 0
        if os.path.isdir(path):
            for name in os.listdir(path):
                full = os.path.join(path, name)
                if os.path.isfile(full):
                    count += 1
                    total += os.path.getsize(full)
        rows.append((label, f"{count} files, {_human_size(total)}, dir={path}"))

    for name in ("vault.meta", "vault.db", "config.json", "config.sig",
                 "device_secret"):
        full = os.path.join(vault.path, name)
        if os.path.exists(full):
            mode = oct(os.stat(full).st_mode & 0o777)
            rows.append((name, f"{_human_size(os.path.getsize(full))}, mode={mode}"))
        else:
            rows.append((name, "<absent>"))
    return rows


def table_stats(vault) -> list:
    counts = table_counts(vault.conn)
    return [(name, counts[name]) for name in sorted(counts)]


def _user_version(vault) -> str:
    row = vault.conn.query_one("PRAGMA user_version")
    return str(row[0]) if row else "0"


def _human_size(size: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if size < 1024 or unit == "GB":
            return f"{size:.0f} {unit}" if unit == "B" else f"{size:.1f} {unit}"
        size /= 1024.0
    return f"{size} B"
