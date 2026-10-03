from __future__ import annotations

"""Tools: SQL console (results grid + history) and the dev-only Crypto lab."""

import time

from PyQt6.QtCore import Qt
from PyQt6.QtGui import QKeySequence, QShortcut
from PyQt6.QtWidgets import (QComboBox, QCheckBox, QHBoxLayout, QLabel,
                             QLineEdit, QMessageBox, QPlainTextEdit,
                             QPushButton, QSplitter, QTableWidget,
                             QTableWidgetItem, QVBoxLayout, QWidget)

from ...vault import values as V


class SqlConsole(QWidget):
    """Arbitrary SQL against the open vault; writes need a confirm."""

    WRITE_HINT = "This statement will MODIFY the database. Continue?"

    def __init__(self, vault, on_open=None, parent=None):
        super().__init__(parent)
        self.vault = vault
        self.on_open = on_open
        self.history_items = []

        self.editor = QPlainTextEdit()
        self.editor.setObjectName("MonoView")
        self.editor.setPlaceholderText(
            "SQL against the decrypted vault, e.g.\n"
            "SELECT title, classification, updated_at FROM t_password;\n"
            "PRAGMA user_version;\n"
            "SELECT count(*) FROM t_moment;")
        QShortcut(QKeySequence("Ctrl+Return"), self.editor, self.execute)

        run_btn = QPushButton("Run (Ctrl+Enter)")
        run_btn.clicked.connect(self.execute)
        history_btn = QPushButton("History")
        history_btn.clicked.connect(self._show_history)
        snippets_btn = QPushButton("Snippets")
        snippets_btn.clicked.connect(self._snippets)

        self.results = QTableWidget(0, 0)
        self.results.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.results.setAlternatingRowColors(True)
        self.messages = QPlainTextEdit()
        self.messages.setObjectName("MonoView")
        self.messages.setReadOnly(True)
        self.messages.setMaximumHeight(110)

        bar = QHBoxLayout()
        for widget in (run_btn, history_btn, snippets_btn):
            bar.addWidget(widget)
        bar.addStretch(1)

        top = QSplitter(Qt.Orientation.Vertical)
        top.addWidget(self.editor)
        top.addWidget(self.results)
        top.setStretchFactor(0, 1)
        top.setStretchFactor(1, 2)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(6, 6, 6, 6)
        layout.addLayout(bar)
        layout.addWidget(top, 1)
        layout.addWidget(self.messages)

    # ------------------------------------------------------------------ run

    def execute(self):
        sql = self.editor.toPlainText().strip().rstrip(";")
        if not sql:
            return
        first = sql.split(None, 1)[0].upper() if sql.split() else ""
        verb = first.split("(")[0] if "(" in first else first
        writes = verb not in ("SELECT", "PRAGMA", "EXPLAIN", "WITH")
        if writes and QMessageBox.question(
                self, "Modify vault", self.WRITE_HINT,
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        ) != QMessageBox.StandardButton.Yes:
            return
        self.history_items.append(sql)
        started = time.perf_counter()
        try:
            if writes:
                self.vault.conn.exec_(sql + ";")
                elapsed = (time.perf_counter() - started) * 1000
                self.results.setRowCount(0)
                self.results.setColumnCount(0)
                self.messages.setPlainText(f"OK — {elapsed:.1f} ms (rows affected unknown)")
            else:
                columns, rows = self.vault.conn.query2(sql)
                self._show_results(columns, rows)
                elapsed = (time.perf_counter() - started) * 1000
                self.messages.setPlainText(f"{len(rows)} rows — {elapsed:.1f} ms")
        except Exception as exc:  # noqa: BLE001
            self.results.setRowCount(0)
            self.messages.setPlainText(f"ERROR: {exc}")

    def _show_results(self, columns, rows):
        self.results.clear()
        self.results.setColumnCount(len(columns))
        self.results.setRowCount(len(rows))
        self.results.setHorizontalHeaderLabels(columns)
        self.results.horizontalHeader().setStretchLastSection(True)
        for r, row in enumerate(rows):
            for c, value in enumerate(row):
                item = QTableWidgetItem(V.describe(value))
                item.setData(Qt.ItemDataRole.UserRole, value)
                item.setToolTip(V.describe(value))
                self.results.setItem(r, c, item)

    def _show_history(self):
        text = "\n\n".join(f"-- {i+1}\n{sql}" for i, sql in
                           enumerate(reversed(self.history_items[-25:])))
        self.editor.setPlainText(text or "-- no history yet")

    def _snippets(self):
        menu_text = (
            "-- snippets\n"
            "SELECT name, sql FROM sqlite_master ORDER BY name;\n"
            "PRAGMA user_version;\n"
            "SELECT id, title, classification, updated_at FROM t_password;\n"
            "SELECT id, title, pinned, updated_at FROM t_note WHERE deleted_at IS NULL;\n"
            "SELECT id, classification, storage_name, size_bytes FROM t_file WHERE deleted_at IS NULL;\n"
            "SELECT * FROM t_encrypted_data LIMIT 50;\n"
            "SELECT * FROM t_wrapped_key;\n"
            "SELECT count(*) FROM t_moment WHERE deleted_at IS NULL;")
        self.editor.setPlainText(menu_text)

    def properties(self):
        return [("Editor", "SQL console"),
                ("Statements run", str(len(self.history_items)))]


class CryptoLab(QWidget):
    """Dev-only: unwrap a wrapped key / decrypt a payload with a pasted key hex.

    Nothing is stored; results show fingerprints by default, raw hex only on
    explicit request.
    """

    def __init__(self, vault, parent=None):
        super().__init__(parent)
        self.vault = vault
        self._last_unwrapped = None

        layout = QVBoxLayout(self)
        layout.setContentsMargins(6, 6, 6, 6)

        # --- section 1: unwrap a wrapped key
        layout.addWidget(QLabel("1) Unwrap a wrapped key copy (paste the wrapping key hex)"))
        self.key_pick = QComboBox()
        for entry in vault.meta.get("wrapped_keys", []):
            self.key_pick.addItem(f"meta: {entry['id']} (wrapper={entry['wrapper']})", entry)
        for (wk_id, role, wrapper, ct, nonce, tag) in vault.conn.query(
                "SELECT id, role, wrapper, ciphertext, nonce, auth_tag "
                "FROM t_wrapped_key WHERE deleted_at IS NULL"):
            self.key_pick.addItem(
                f"db: {wk_id} (role={role}, wrapper={wrapper})",
                dict(id=wk_id, ciphertext=bytes(ct).hex(),
                     nonce=bytes(nonce).hex(), auth_tag=bytes(tag).hex()))
        self.wrapping_key_edit = QLineEdit()
        self.wrapping_key_edit.setPlaceholderText("wrapping key, 32-byte hex (e.g. SMK[0:32] or TS-KEK)")
        self.use_aad = QCheckBox("AAD = wrapped-key id (keeps legacy vault_key:biokey unwrappable when off)")
        self.use_aad.setChecked(True)
        unwrap_btn = QPushButton("Unwrap")
        unwrap_btn.clicked.connect(self._unwrap)
        self.result1 = QLabel("")
        self.result1.setObjectName("SectionHint")
        self.result1.setWordWrap(True)
        row1 = QHBoxLayout()
        row1.addWidget(self.key_pick, 1)
        row1.addWidget(unwrap_btn)
        layout.addLayout(row1)
        layout.addWidget(self.wrapping_key_edit)
        layout.addWidget(self.use_aad)
        layout.addWidget(self.result1)

        # --- section 2: decrypt a payload
        layout.addWidget(QLabel("2) Decrypt a t_encrypted_data payload (paste the KEK / TopSecretKEK hex)"))
        self.dek_pick = QComboBox()
        _, rows = vault.conn.query2(
            "SELECT e.id, e.dek_id, k.ciphertext, k.nonce, k.auth_tag, e.content, e.nonce, e.auth_tag "
            "FROM t_encrypted_data e JOIN t_data_encrypt_key k ON k.id = e.dek_id LIMIT 500")
        for (eid, dek_id, kct, knonce, ktag, content, enonce, etag) in rows:
            self.dek_pick.addItem(
                f"{eid}  (dek {dek_id[:8]}…)",
                dict(eid=eid, dek_id=dek_id, dek_ct=bytes(kct).hex(),
                     dek_nonce=bytes(knonce).hex(), dek_tag=bytes(ktag).hex(),
                     content=bytes(content).hex(), nonce=bytes(enonce).hex(),
                     tag=bytes(etag).hex()))
        self.kek_edit = QLineEdit()
        self.kek_edit.setPlaceholderText("KEK / TopSecretKEK, 32-byte hex")
        decrypt_btn = QPushButton("Decrypt payload")
        decrypt_btn.clicked.connect(self._decrypt_payload)
        self.result2 = QPlainTextEdit()
        self.result2.setObjectName("MonoView")
        self.result2.setReadOnly(True)
        row2 = QHBoxLayout()
        row2.addWidget(self.dek_pick, 1)
        row2.addWidget(decrypt_btn)
        layout.addLayout(row2)
        layout.addWidget(self.kek_edit)
        layout.addWidget(self.result2, 1)

        if self.key_pick.count() == 0 and self.dek_pick.count() == 0:
            self.result2.setPlainText("No wrapped keys / payloads in this vault yet.")
        self.vault = vault

    # --------------------------------------------------------------- unwrap

    def _picked_wrapped(self):
        return self.key_pick.currentData() or {}

    def _unwrap(self):
        import hashlib
        from ...vault.crypto import aes_gcm_decrypt
        entry = self._picked_wrapped()
        if not entry:
            return
        try:
            key = bytes.fromhex(self.wrapping_key_edit.text().strip())
            if len(key) != 32:
                raise ValueError("wrapping key must be 32 bytes hex")
            aad = entry["id"].encode("utf-8") if self.use_aad.isChecked() else b""
            plain = aes_gcm_decrypt(key, bytes.fromhex(entry["nonce"]), aad,
                                    bytes.fromhex(entry["ciphertext"]),
                                    bytes.fromhex(entry["auth_tag"]))
            self.result1.setText(
                f"unwrapped {len(plain)} B · fingerprint "
                f"{hashlib.sha256(plain).hexdigest()[:16]} "
                "(raw hex not shown; paste it as the payload key below if needed)")
            self._last_unwrapped = plain.hex()
        except Exception as exc:  # noqa: BLE001
            self.result1.setText(f"unwrap FAILED: {exc}")

    # ------------------------------------------------------------- decrypt

    def _decrypt_payload(self):
        from ...vault.crypto import aes_gcm_decrypt
        import hashlib
        entry = self.dek_pick.currentData() or {}
        if not entry:
            return
        try:
            kek = bytes.fromhex(self.kek_edit.text().strip())
            if len(kek) != 32:
                raise ValueError("KEK must be 32 bytes hex")
            dek = aes_gcm_decrypt(
                kek, bytes.fromhex(entry["dek_nonce"]),
                entry["dek_id"].encode("utf-8"),
                bytes.fromhex(entry["dek_ct"]), bytes.fromhex(entry["dek_tag"]))
            plain = aes_gcm_decrypt(
                dek, bytes.fromhex(entry["nonce"]),
                entry["eid"].encode("utf-8"),          # AAD = t_encrypted_data id
                bytes.fromhex(entry["content"]), bytes.fromhex(entry["tag"]))
            self._last_payload = plain
            self.result2.setPlainText(
                f"{len(plain)} B · fingerprint {hashlib.sha256(plain).hexdigest()[:16]}\n\n"
                + V.hex_dump(plain[:4096]))
        except Exception as exc:  # noqa: BLE001
            self.result2.setPlainText(f"decrypt FAILED: {exc}")

    def properties(self):
        return [("Editor", "Crypto lab (dev)"),
                ("Wrapped keys", str(self.key_pick.count())),
                ("Payloads", str(self.dek_pick.count()))]
