from __future__ import annotations

"""Properties view (right dock): detailed vault info, incl. derived keys.

Shows live key material — this is a developer tool; treat screenshots and
logs of this panel as secrets.
"""

import os
import time

from PySide6.QtCore import Qt
from PySide6.QtWidgets import QTreeWidget, QTreeWidgetItem

from ..vault import Vault, crockford32, key_fingerprint

Row = tuple[str, str, str]


def _fmt_time(ms) -> str:
    try:
        return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(int(ms) / 1000))
    except (TypeError, ValueError, OSError):
        return str(ms)


def _fmt_size(size: int) -> str:
    if size < 1024:
        return f"{size} B"
    if size < 1024 * 1024:
        return f"{size / 1024:.1f} KiB"
    return f"{size / (1024 * 1024):.1f} MiB"


def _b32_hex(value: str) -> str:
    try:
        return crockford32.decode(value).hex()
    except (TypeError, ValueError):
        return value


def _sections(vault: Vault) -> list[tuple[str, list[Row]]]:
    meta = vault.meta
    kdf = meta.get("kdf", {})
    salts = meta.get("salts", {})
    files = ("vault.meta", "vault.db", "config.json", "config.sig",
             "device_secret")

    vault_rows: list[Row] = [
        ("Name", vault.name, ""),
        ("Vault id", vault.vault_id, ""),
        ("Path", vault.path, vault.path),
        ("Metadata version", str(meta.get("version", "")), ""),
        ("Created", _fmt_time(meta.get("created_at")), ""),
        ("Updated", _fmt_time(meta.get("updated_at")), ""),
        ("Schema version", str(vault.schema_version), ""),
        ("Tables", str(len(vault.tables)), ", ".join(vault.tables)),
    ]
    file_rows: list[Row] = []
    for name in files:
        path = os.path.join(vault.path, name)
        if os.path.isfile(path):
            file_rows.append((name, _fmt_size(os.path.getsize(path)), path))
        else:
            file_rows.append((name, "missing", path))
    kdf_rows: list[Row] = [
        ("Algorithm", str(kdf.get("algorithm", "")), ""),
        ("Memory cost", f"{kdf.get('m_kib', '')} KiB", ""),
        ("Time cost", str(kdf.get("t", "")), ""),
        ("Parallelism", str(kdf.get("p", "")), ""),
        ("Argon2 salt", _b32_hex(salts.get("argon2", "")), salts.get("argon2", "")),
        ("HKDF salt", _b32_hex(salts.get("hkdf", "")), salts.get("hkdf", "")),
    ]
    key_rows: list[Row] = []
    for name, value, formula in vault.key_chain():
        key_rows.append((name, value.hex(),
                         f"fingerprint {key_fingerprint(value)}\n{formula}"))
    wrapped_rows: list[Row] = []
    for entry in meta.get("wrapped_keys", []):
        prefix = entry.get("id", "wrapped key")
        wrapped_rows.extend([
            (f"{prefix} · role", str(entry.get("role", "")), ""),
            (f"{prefix} · wrapper", str(entry.get("wrapper", "")), ""),
            (f"{prefix} · algorithm", str(entry.get("algorithm", "")), ""),
            (f"{prefix} · nonce", _b32_hex(entry.get("nonce", "")), ""),
            (f"{prefix} · auth tag", _b32_hex(entry.get("auth_tag", "")), ""),
            (f"{prefix} · ciphertext", _b32_hex(entry.get("ciphertext", "")), ""),
        ])
    config_rows: list[Row] = [
        (str(key), str(value), "") for key, value in sorted(vault.config.items())
    ]
    return [
        ("Vault", vault_rows),
        ("Files", file_rows),
        ("Key derivation", kdf_rows),
        ("Derived keys", key_rows),
        ("Wrapped keys", wrapped_rows),
        ("Config", config_rows),
    ]


class PropertiesView(QTreeWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setColumnCount(2)
        self.setHeaderLabels(["Property", "Value"])
        self.setAlternatingRowColors(True)
        self.setUniformRowHeights(True)
        self.setColumnWidth(0, 150)
        self.header().setStretchLastSection(True)
        self.setTextElideMode(Qt.TextElideMode.ElideMiddle)

    def show_vault(self, vault: Vault) -> None:
        self.clear()
        for section, rows in _sections(vault):
            top = QTreeWidgetItem([section, ""])
            font = top.font(0)
            font.setBold(True)
            top.setFont(0, font)
            for name, value, tooltip in rows:
                child = QTreeWidgetItem([name, value])
                if tooltip:
                    child.setToolTip(1, tooltip)
                top.addChild(child)
            self.addTopLevelItem(top)
            top.setExpanded(True)
