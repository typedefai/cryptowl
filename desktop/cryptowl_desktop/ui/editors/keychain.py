from __future__ import annotations

"""Key-derivation chain diagram (docs/design.md), DBeaver-inspectable.

An interactive QGraphicsScene: every node is a key / input / store, every edge
a derivation step (labelled with algorithm + salt). Clicking a node shows its
attributes (fingerprints by default; raw hex only behind the dev reveal).
A master-password field derives P/TMK/SMK live and verifies the unwrap against
the session VaultKey.
"""

import hashlib
import logging
import os

from PyQt6.QtCore import QRectF, QPointF, Qt
from PyQt6.QtGui import (QBrush, QColor, QFont, QPainter, QPainterPath,
                         QPen, QPolygonF)
from PyQt6.QtWidgets import (QCheckBox, QGraphicsObject,
                             QGraphicsPathItem, QGraphicsPolygonItem,
                             QGraphicsScene, QGraphicsSimpleTextItem,
                             QGraphicsView, QHBoxLayout, QLineEdit,
                             QPushButton, QVBoxLayout, QWidget)

from ...vault import crockford32
from ...vault.crypto import (aes_gcm_decrypt, argon2id_raw, hkdf_sha256,
                             hmac_sha256)
from ...vault.vault import _read_device_secret

NODE_W, NODE_H = 176, 48

_STATES = {
    # name: (fill, border, text)
    "known":    ("#EAF4FD", "#007ACC", "#0B3C5D"),
    "derived":  ("#E8F5E9", "#2E7D32", "#1B3B1D"),
    "mind":     ("#F5F5F5", "#9E9E9E", "#555555"),
    "locked":   ("#FDECEA", "#C62828", "#7A1B12"),
    "android":  ("#FFF8E1", "#B26A00", "#5A3A00"),
    "store":    ("#F0F0F0", "#5F6B7A", "#243447"),
}

_MONO = QFont("Menlo")
_MONO.setStyleHint(QFont.StyleHint.Monospace)
_MONO.setPointSize(9)


class NodeItem(QGraphicsObject):

    def __init__(self, title, subtitle, state, on_click=None):
        super().__init__()
        self.title = title
        self.subtitle = subtitle
        self.state = state
        self.on_click = on_click
        self._hover = False
        self.setAcceptHoverEvents(True)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setToolTip(title)

    def boundingRect(self):
        return QRectF(0, 0, NODE_W, NODE_H)

    def paint(self, painter, option, widget=None):
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        fill, border, text = _STATES.get(self.state, _STATES["store"])
        pen = QPen(QColor(border), 2 if (self._hover or self.state == "known") else 1)
        if self.state == "mind":
            pen.setStyle(Qt.PenStyle.DashLine)
        painter.setPen(pen)
        painter.setBrush(QBrush(QColor(fill)))
        painter.drawRoundedRect(QRectF(1, 1, NODE_W - 2, NODE_H - 2), 8, 8)
        painter.setPen(QColor(text))
        font = QFont()
        font.setBold(True)
        font.setPointSize(9)
        painter.setFont(font)
        painter.drawText(QRectF(8, 6, NODE_W - 16, 18), Qt.AlignmentFlag.AlignLeft,
                         self.title)
        small = QFont()
        small.setPointSize(8)
        painter.setFont(small)
        painter.setPen(QColor("#666666"))
        painter.drawText(QRectF(8, 24, NODE_W - 16, 20),
                         Qt.AlignmentFlag.AlignLeft | Qt.TextFlag.TextWordWrap,
                         self.subtitle)

    def hoverEnterEvent(self, event):
        self._hover = True
        self.update()

    def hoverLeaveEvent(self, event):
        self._hover = False
        self.update()

    def mousePressEvent(self, event):
        if self.on_click:
            self.on_click(self)
        event.accept()


class EdgeItem(QGraphicsPathItem):

    def __init__(self, src: NodeItem, dst: NodeItem, label="", dashed=False):
        super().__init__()
        sx, sy = src.pos().x() + NODE_W, src.pos().y() + NODE_H / 2
        dx, dy = dst.pos().x(), dst.pos().y() + NODE_H / 2
        if dst.pos().y() > src.pos().y() + NODE_H + 4:
            sx, sy = src.pos().x() + NODE_W / 2, src.pos().y() + NODE_H
            dx, dy = dst.pos().x() + NODE_W / 2, dst.pos().y()
        elif dst.pos().y() < src.pos().y() - NODE_H - 4:
            sx, sy = src.pos().x() + NODE_W / 2, src.pos().y()
            dx, dy = dst.pos().x() + NODE_W / 2, dst.pos().y() + NODE_H
        path = QPainterPath(QPointF(sx, sy))
        mid_x = (sx + dx) / 2
        path.cubicTo(QPointF(mid_x, sy), QPointF(mid_x, dy), QPointF(dx, dy))
        self.setPath(path)
        self.setPen(QPen(QColor("#7A8BA0"), 1.4,
                         Qt.PenStyle.DashLine if dashed else Qt.PenStyle.SolidLine))
        # arrow head
        head = QPolygonF([QPointF(dx, dy), QPointF(dx - 9, dy - 4),
                          QPointF(dx - 9, dy + 4)])
        head.translate(0, 0)
        arrow = QGraphicsPolygonItem(head, self)
        arrow.setBrush(QBrush(QColor("#7A8BA0")))
        arrow.setPen(QPen(QColor("#7A8BA0")))
        if label:
            text = QGraphicsSimpleTextItem(label, self)
            text.setFont(_MONO)
            text.setBrush(QBrush(QColor("#5A6B7A")))
            text.setPos((sx + dx) / 2 - text.boundingRect().width() / 2,
                        (sy + dy) / 2 - text.boundingRect().height() - 6)
            text.setZValue(5)


def _salt_label(encoded) -> str:
    """meta salts are Crockford Base32 strings; show the decoded hex prefix."""
    if not encoded:
        return "<none>"
    try:
        return crockford32.decode(encoded).hex()[:12] + "…"
    except Exception:  # noqa: BLE001
        return "<bad salt>"


class KeyChainEditor(QWidget):

    def __init__(self, vault, on_notify=None, parent=None):
        super().__init__(parent)
        self.vault = vault
        self._notify = on_notify
        self._derived = None
        self._selected = None

        bar = QHBoxLayout()
        self.password_edit = QLineEdit()
        self.password_edit.setEchoMode(QLineEdit.EchoMode.Password)
        self.password_edit.setPlaceholderText("master password (optional — enables live derivation)")
        derive_btn = QPushButton("Derive chain")
        derive_btn.clicked.connect(self._derive)
        self.reveal = QCheckBox("Show raw hex (dev)")
        self.reveal.toggled.connect(self._notify_properties)
        bar.addWidget(self.password_edit, 1)
        bar.addWidget(derive_btn)
        bar.addWidget(self.reveal)

        self.scene = QGraphicsScene(self)
        self.view = QGraphicsView(self.scene)
        self.view.setRenderHint(QPainter.RenderHint.Antialiasing)
        self.view.setBackgroundBrush(QBrush(QColor("#FAFAFC")))

        layout = QVBoxLayout(self)
        layout.setContentsMargins(6, 6, 6, 6)
        layout.addLayout(bar)
        layout.addWidget(self.view, 1)

        self._draw()
        self.view.showEvent = lambda event: self._fit()

    # ------------------------------------------------------------- diagram

    def _draw(self):
        self.scene.clear()
        self._selected = None
        self._nodes = {}
        vault = self.vault

        def node(key, x, y, title, subtitle, state):
            item = NodeItem(title, subtitle, state, on_click=self._select)
            item.setPos(x, y)
            self.scene.addItem(item)
            self._nodes[key] = item
            return item

        def edge2(src_key, dst_key, label="", dashed=False):
            item = EdgeItem(self._nodes[src_key], self._nodes[dst_key], label, dashed)
            self.scene.addItem(item)

        meta_salt = vault.meta.get("salts", {})
        kdf = vault.meta.get("kdf", {})

        node("mp", 10, 10, "Master Password", "not stored (user's mind)", "mind")
        node("ds", 10, 200, "Device Secret", "desktop file / Android Keystore",
             "android" if not os.path.isfile(os.path.join(vault.path, "device_secret"))
             else "known")
        node("p", 226, 105, "P", "HMAC-SHA256(DeviceSecret, MP)", "derived")
        node("tmk", 442, 105, "TMK", f"Argon2id m={kdf.get('m_kib')} t={kdf.get('t')} p={kdf.get('p')}", "derived")
        node("smk", 658, 105, "SMK", "HKDF-SHA256 → 64 B", "derived")
        node("vault_key", 874, 105, "VaultKey", "SQLCipher raw key (32 B)", "known")
        node("mac", 874, 10, "MAC key", "SMK[32:64]", "derived")
        node("config", 1090, 10, "config.sig", "+ vault.meta mac", "store")
        node("db", 1090, 105, "vault.db", "SQLCipher (AES-256)", "store")
        node("fek", 1090, 200, "FEK", "HKDF(VaultKey, 'file') → CWO1 files", "known")

        node("biokey", 10, 300, "BioKey", "Android Keystore + fingerprint", "android")
        node("bio_copies", 226, 300, "BioKey copies", "vault_key / kek / ts_kek :biokey", "android")
        node("kek", 442, 300, "KEK", "wraps S-tier DEKs (fingerprint per access)", "locked")
        node("sp", 10, 395, "Secondary Password", "not stored (user's mind)", "mind")
        node("tskek", 442, 395, "TS-KEK", "Argon2id(secondarySalt)", "locked")
        node("tskek2", 658, 395, "TopSecretKEK", "wraps T-tier DEKs", "locked")

        edge2("mp", "p", "msg")
        edge2("ds", "p", "key")
        edge2("p", "tmk", "argon2id(salt=" + _salt_label(meta_salt.get("argon2")) + ")")
        edge2("tmk", "smk", "hkdf(salt=" + _salt_label(meta_salt.get("hkdf")) + ", info=" + vault.vault_id + ")")
        edge2("smk", "vault_key", "unwrap vault_key:smk")
        edge2("smk", "mac", "SMK[32:64]")
        edge2("mac", "config", "HMAC-SHA256")
        edge2("vault_key", "db", "raw key x'…'")
        edge2("vault_key", "fek", "hkdf(info='file')")
        edge2("biokey", "bio_copies", "fingerprint")
        edge2("bio_copies", "vault_key", "vault_key:biokey", dashed=True)
        edge2("bio_copies", "kek", "kek:biokey")
        edge2("sp", "tskek", "argon2id(salt=" + _salt_label(meta_salt.get("secondary")) + ")")
        edge2("bio_copies", "tskek", "ts_kek:biokey", dashed=True)
        edge2("tskek", "tskek2", "unwrap top_secret_kek:ts_kek")

        self.scene.setSceneRect(self.scene.itemsBoundingRect().adjusted(-20, -20, 20, 20))
        self._fit()

    def _fit(self):
        self.view.fitInView(self.scene.sceneRect(), Qt.AspectRatioMode.KeepAspectRatio)

    def resizeEvent(self, event):
        super().resizeEvent(event)
        self._fit()

    # ------------------------------------------------------------ selection

    def _select(self, node_item):
        self._selected = node_item
        self._notify_properties()

    def _notify_properties(self, *_):
        if self._notify is not None:
            self._notify()

    def _attributes_for(self, node_item) -> list:
        title = node_item.title
        reveal = self.reveal.isChecked()
        vault = self.vault
        wrapped_by_id = {entry["id"]: entry for entry in vault.meta.get("wrapped_keys", [])}
        if title == "Master Password":
            return [("Value", "<not stored — only in the user's mind>"),
                    ("Used by", "P = HMAC-SHA256(key=DeviceSecret, msg=MP)")]
        if title == "Device Secret":
            path = os.path.join(vault.path, "device_secret")
            if not os.path.isfile(path):
                return [("Value", "<in Android Keystore — not extractable>")]
            return [("Path", path),
                    ("Size", "32 B"),
                    ("Mode", oct(os.stat(path).st_mode & 0o777)),
                    ("Fingerprint", vault.device_secret_fingerprint()),
                    ("Raw hex (dev)", open(path, encoding="utf-8").read() if reveal else "<hidden>")]
        if title == "VaultKey":
            rows = [("Fingerprint", vault.vault_key_fingerprint),
                    ("Size", "32 B"),
                    ("Role", "SQLCipher raw key; unwrapped from vault_key:smk")]
            rows.append(("Raw hex (dev)",
                         vault.vault_key.hex() if reveal else "<hidden>"))
            if self._derived:
                rows.append(("Live check", self._derived["vault_key_check"]))
            return rows
        if title == "FEK":
            return [("Fingerprint", vault.fek_fingerprint),
                    ("Derivation", "HKDF-SHA256(ikm=VaultKey, salt='', info='file', 32 B)"),
                    ("Used by", "CWO1 whole-file/chunked (AAD = row id)")]
        if title == "config.sig":
            sig_path = os.path.join(vault.path, "config.sig")
            sig = open(sig_path, encoding="ascii").read().strip() if os.path.isfile(sig_path) else "?"
            return [("config.json", open(os.path.join(vault.path, "config.json"), "rb").read().decode()),
                    ("config.sig", sig),
                    ("Algorithm", "HMAC-SHA256, key = SMK[32:64]"),
                    ("Verified", "at every open")]
        if title == "vault.db":
            return [("Cipher", "SQLCipher AES-256 page encryption"),
                    ("Key", "VaultKey as raw key (x'<hex>')"),
                    ("user_version", str(vault.conn.query_one('PRAGMA user_version')[0]))]
        if title == "Secondary Password":
            return [("Value", "<not stored>"),
                    ("Derives", "TS-KEK = Argon2id(P_secondary, salt=secondarySalt)")]
        # derived chain nodes
        live = self._derived or {}
        mapping = {"P": ("P", "HMAC-SHA256(key=DeviceSecret, msg=MP)"),
                   "TMK": ("TMK", "Argon2id(P, salt=argon2Salt)"),
                   "SMK": ("SMK", "HKDF-SHA256(TMK, salt=hkdfSalt, info=vaultId, 64 B)"),
                   "MAC key": ("mac_key", "SMK[32:64] — config.sig / vault.meta mac")}
        if title in mapping:
            key, derivation = mapping[title]
            value = live.get(key)
            return [("Fingerprint", value or "<enter the master password and click Derive>"),
                    ("Derivation", derivation)]
        if title == "BioKey":
            return [("Location", "Android Keystore (non-exportable)"),
                    ("Usage", "every use needs a fresh BiometricPrompt"),
                    ("On desktop", "unavailable")]
        if title == "BioKey copies":
            rows = []
            for entry in wrapped_by_id.values():
                rows.append((entry["id"],
                             f"nonce={entry['nonce'][:12]}… · ct={len(crockford32.decode(entry['ciphertext']))}B"))
            for (wk_id, role, wrapper, nonce) in vault.conn.query(
                    "SELECT id, role, wrapper, nonce FROM t_wrapped_key WHERE deleted_at IS NULL"):
                rows.append((wk_id, f"role={role} wrapper={wrapper} nonce={bytes(nonce).hex()[:12]}…"))
            return rows or [("—", "no biokey copies in this vault")]
        if title == "KEK":
            entry = wrapped_by_id.get("kek:biokey")
            return [("Wrapped copy", "kek:biokey" if entry else "<none yet>"),
                    ("Access", "Android only: BiometricPrompt → BioKey → KEK"),
                    ("Note", "created on first password use in the Android app")]
        if title == "TS-KEK":
            return [("Wrapped copy", "ts_kek:biokey" if wrapped_by_id.get("ts_kek:biokey") else "<none>"),
                    ("Access", "Android: fingerprint; the secondary password derives it too")]
        if title == "TopSecretKEK":
            return [("Wrapped copy", "top_secret_kek:ts_kek (in t_wrapped_key)"),
                    ("Access", "Android: fingerprint + secondary password")]
        return [("Node", title)]

    # ------------------------------------------------------------- derive

    def _derive(self):
        master_password = self.password_edit.text()
        if not master_password:
            self._derived = None
            self._refresh_attributes()
            return
        vault = self.vault
        try:
            device_secret = _read_device_secret(vault.path)
        except FileNotFoundError:
            self._derived = None
            self._derived_error = (
                "no device_secret — Android-bound vault; the password chain "
                "cannot be derived here")
            self._notify_properties()
            return
        try:
            p = hmac_sha256(device_secret, master_password.encode("utf-8"))
            kdf = vault.meta.get("kdf", {})
            tmk = argon2id_raw(
                p, crockford32.decode(vault.meta["salts"]["argon2"]),
                m_kib=kdf.get("m_kib", 19456), t=kdf.get("t", 2),
                p=kdf.get("p", 1), hash_len=32)
            smk = hkdf_sha256(tmk, crockford32.decode(vault.meta["salts"]["hkdf"]),
                              vault.vault_id.encode("utf-8"), 64)
            entry = next((e for e in vault.meta.get("wrapped_keys", [])
                          if e["id"] == "vault_key:smk"), None)
            check = "vault_key:smk not present"
            if entry:
                try:
                    unwrapped = aes_gcm_decrypt(
                        smk[:32], crockford32.decode(entry["nonce"]),
                        b"vault_key:smk",
                        crockford32.decode(entry["ciphertext"]),
                        crockford32.decode(entry["auth_tag"]))
                    match = hashlib.sha256(unwrapped).hexdigest()[:16] == vault.vault_key_fingerprint
                    check = "✓ unwrap matches the session VaultKey" if match \
                        else "✗ unwrap does NOT match the session VaultKey"
                except Exception as exc:  # noqa: BLE001
                    check = f"✗ unwrap failed ({exc})"
            self._derived = {
                "p": hashlib.sha256(p).hexdigest()[:16],
                "tmk": hashlib.sha256(tmk).hexdigest()[:16],
                "smk": hashlib.sha256(smk).hexdigest()[:16],
                "mac_key": hashlib.sha256(smk[32:64]).hexdigest()[:16],
                "vault_key_check": check,
            }
            self._derived_error = None
            logging.getLogger("cwl.keychain").info(
                "derived chain: p/tmk/smk/mac fingerprints computed; %s", check)
        finally:
            device_secret = None
        self._notify_properties()

    # --------------------------------------------------------- editor hooks

    def refresh(self):
        self._draw()
        self._notify_properties()

    def properties(self):
        rows = [("Editor", "Key chain")]
        if self._selected is None:
            rows.append(("Hint", "click a diagram node to inspect its attributes"))
        else:
            rows.append(("Node", self._selected.title))
            rows.extend(self._attributes_for(self._selected))
        error = getattr(self, "_derived_error", None)
        if error:
            rows.append(("Live derivation", error))
        return rows

