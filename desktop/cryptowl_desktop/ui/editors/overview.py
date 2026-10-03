from __future__ import annotations

"""Overview dashboard + Security (keys & chain) editors."""

from PyQt6.QtWidgets import (QPushButton, QTableWidget, QTableWidgetItem,
                             QTextBrowser, QVBoxLayout, QWidget)

from ...vault import catalog, files as filesv, schema


def _fill(table: QTableWidget, rows):
    table.setRowCount(len(rows))
    for i, (key, value) in enumerate(rows):
        table.setItem(i, 0, QTableWidgetItem(str(key)))
        item = QTableWidgetItem(str(value))
        item.setToolTip(str(value))
        table.setItem(i, 1, item)


class OverviewEditor(QWidget):
    """Start-page dashboard: versions, counts, files, fingerprints, integrity."""

    def __init__(self, vault, on_open=None, parent=None):
        super().__init__(parent)
        self.vault = vault
        self.on_open = on_open

        self.info = QTableWidget(0, 2)
        self.info.setHorizontalHeaderLabels(["Item", "Value"])
        self.info.horizontalHeader().setStretchLastSection(True)
        self.info.verticalHeader().setVisible(False)
        self.info.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)

        buttons = QPushButton("Open SQL console")
        buttons.clicked.connect(lambda: self.on_open("sql") if self.on_open else None)
        lab = QPushButton("Crypto lab")
        lab.clicked.connect(lambda: self.on_open("lab") if self.on_open else None)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(6, 6, 6, 6)
        layout.addWidget(buttons)
        layout.addWidget(self.info, 1)
        layout.addWidget(lab)
        self.refresh()

    def refresh(self):
        vault = self.vault
        version = catalog.user_version(vault.conn)
        counts = ", ".join(
            f"{t['name']}={t['row_count']}"
            for t in catalog.tables(vault.conn)
            if t["type"] == "table" and (t["row_count"] or 0) > 0) or "(empty)"
        attach = filesv.list_files(vault, "attachments")
        thumbs = filesv.list_files(vault, "thumbnails")
        rows = [
            ("Vault", f"{vault.vault_id} — {vault.config.get('name') or ''}".strip()),
            ("Path", vault.path),
            ("Schema", f"v{version} (expected v{schema.expected_version()})"),
            ("Tables", counts),
            ("Files", f"{len(attach)} attachments / {len(thumbs)} thumbnails"),
            ("Meta version", vault.meta.get("version")),
            ("KDF", "{algorithm} m={m_kib} KiB t={t} p={p}".format(
                **{**vault.meta.get("kdf", {})})),
            ("VaultKey fp", vault.vault_key_fingerprint),
            ("FEK fp", vault.fek_fingerprint),
            ("Integrity", "config.sig + vault.meta mac verified at open"),
        ]
        try:
            rows.append(("Device secret fp", vault.device_secret_fingerprint()))
        except Exception as exc:  # noqa: BLE001
            rows.append(("Device secret", f"n/a ({exc})"))
        _fill(self.info, rows)

    def properties(self):
        return [("Editor", "Overview"), ("Vault", self.vault.vault_id)]


class KeysEditor(QWidget):
    """Key material summary + wrapped-key copies + chain diagram."""

    def __init__(self, vault, parent=None):
        super().__init__(parent)
        self.vault = vault
        self.table = QTableWidget(0, 3)
        self.table.setHorizontalHeaderLabels(["Wrapped key", "Role/Wrapper", "Nonce (hex)"])
        self.table.horizontalHeader().setStretchLastSection(True)
        self.table.verticalHeader().setVisible(False)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)

        self.chain = QTextBrowser()
        self.chain.setObjectName("MonoView")
        self.chain.setPlainText(
            "P    = HMAC-SHA256(key=DeviceSecret, msg=MasterPassword)\n"
            "TMK  = Argon2id(P, salt=argon2Salt)\n"
            "SMK  = HKDF-SHA256(TMK, salt=hkdfSalt, info=vaultId, 64 B)\n"
            "SMK[0:32]  -> unwraps vault_key:smk -> VaultKey (SQLCipher raw key)\n"
            "SMK[32:64] -> MAC key (config.sig, vault.meta mac)\n"
            "FEK        = HKDF-SHA256(VaultKey, '', 'file', 32 B)  (C-tier files)\n"
            "KEK        = BioKey-wrapped copy only (Android)  -> S-tier DEKs\n"
            "TS-KEK     = Argon2id(secondary password)        -> TopSecretKEK -> T-tier DEKs")
        self.chain.setFixedHeight(170)

        self.keys = QTableWidget(0, 2)
        self.keys.setHorizontalHeaderLabels(["Key", "Fingerprint"])
        self.keys.horizontalHeader().setStretchLastSection(True)
        self.keys.verticalHeader().setVisible(False)
        self.keys.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(6, 6, 6, 6)
        layout.addWidget(self.keys)
        layout.addWidget(self.chain)
        layout.addWidget(self.table, 1)
        self.refresh()

    def refresh(self):
        vault = self.vault
        rows = [("VaultKey", vault.vault_key_fingerprint),
                ("FEK", vault.fek_fingerprint)]
        try:
            rows.append(("Device secret", vault.device_secret_fingerprint()))
        except Exception as exc:  # noqa: BLE001
            rows.append(("Device secret", f"n/a ({exc})"))
        _fill(self.keys, rows)

        wrapped = []
        for entry in vault.meta.get("wrapped_keys", []):
            wrapped.append((entry.get("id"), f"meta/{entry.get('wrapper')}",
                            entry.get("nonce", "")[:16]))
        for (wk_id, role, wrapper, ct, nonce, tag) in vault.conn.query(
                "SELECT id, role, wrapper, ciphertext, nonce, auth_tag "
                "FROM t_wrapped_key WHERE deleted_at IS NULL"):
            hint = "unwrappable on Android only" if wrapper == "biokey" else f"wrapper={wrapper}"
            wrapped.append((wk_id, f"{role}/{hint}", bytes(nonce).hex()))
        self.table.setRowCount(len(wrapped))
        for i, (a, b, c) in enumerate(wrapped):
            self.table.setItem(i, 0, QTableWidgetItem(str(a)))
            self.table.setItem(i, 1, QTableWidgetItem(str(b)))
            self.table.setItem(i, 2, QTableWidgetItem(str(c)))

    def properties(self):
        return [("Editor", "Keys & chain"),
                ("Wrapped keys", str(self.table.rowCount()))]
