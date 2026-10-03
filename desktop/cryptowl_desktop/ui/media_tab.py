from __future__ import annotations

"""Media vault tab — Confidential-tier files (CWO1, FEK-encrypted)."""

import mimetypes
import os

from PyQt6.QtCore import QBuffer, QByteArray, QIODevice, QSize, Qt
from PyQt6.QtGui import QIcon, QImage, QImageReader, QPixmap
from PyQt6.QtWidgets import (QDialog, QFileDialog, QHBoxLayout, QLabel,
                             QListWidget, QListWidgetItem, QMessageBox,
                             QPushButton, QStyle, QVBoxLayout, QWidget)

from ..vault import MediaRepository, new_uuid
from ..vault import cwo1


class MediaTab(QWidget):
    """List / import / export / delete C-tier files; S/T rows are shown locked."""

    def __init__(self, vault, parent=None):
        super().__init__(parent)
        self.vault = vault
        self.repo = MediaRepository(vault.conn)
        self.fek = vault.file_encryption_key
        self._items = {}

        self.list_widget = QListWidget()
        self.list_widget.setViewMode(QListWidget.ViewMode.IconMode)
        self.list_widget.setIconSize(QSize(112, 112))
        self.list_widget.setResizeMode(QListWidget.ResizeMode.Adjust)
        self.list_widget.setMovement(QListWidget.Movement.Static)
        self.list_widget.setGridSize(QSize(148, 168))
        self.list_widget.setWordWrap(True)
        self.list_widget.itemDoubleClicked.connect(lambda _: self._preview())
        self.list_widget.itemSelectionChanged.connect(self._notify_properties)

        import_btn = QPushButton("Import files…")
        export_btn = QPushButton("Export selected…")
        delete_btn = QPushButton("Delete")
        refresh_btn = QPushButton("Refresh")
        import_btn.clicked.connect(self._import)
        export_btn.clicked.connect(self._export)
        delete_btn.clicked.connect(self._delete)
        refresh_btn.clicked.connect(self.refresh)

        toolbar = QHBoxLayout()
        for button in (import_btn, export_btn, delete_btn, refresh_btn):
            toolbar.addWidget(button)
        toolbar.addStretch(1)

        layout = QVBoxLayout(self)
        layout.addWidget(QLabel(
            "Media is Confidential tier: each file is CWO1-encrypted with the "
            "vault FEK before touching disk. Videos/audio are chunked; the "
            "desktop tool exports them decrypted rather than playing them."))
        layout.addLayout(toolbar)
        layout.addWidget(self.list_widget, 1)

        self.refresh()

    def _notify_properties(self):
        callback = getattr(self, "on_properties_changed", None)
        if callback is not None:
            callback()

    def properties(self):
        file_id = self._selected_id()
        if not file_id:
            return [("State", "no item selected")]
        item = self._items[file_id]
        return [
            ("File id", item["id"]),
            ("Class", item["classification"]),
            ("Original name", item["original_name"] or ""),
            ("MIME", item["mime_type"] or ""),
            ("Size", self._human(item["size_bytes"])),
            ("Storage", item["storage_name"]),
            ("DEK id", item["dek_id"] or "(none, C tier)"),
        ]

    # ------------------------------------------------------------------ list

    def refresh(self) -> None:
        self.list_widget.clear()
        self._items = {}
        for item in self.repo.list():
            entry = QListWidgetItem()
            icon = self._thumbnail_icon(item["id"])
            entry.setIcon(icon if icon else self.style().standardIcon(
                QStyle.StandardPixmap.SP_FileIcon))
            label = item["original_name"] or item["id"]
            size = self._human(item["size_bytes"])
            lock = "" if item["classification"] == "C" else f" [{item['classification']} locked]"
            entry.setText(f"{label}\n{size}{lock}")
            entry.setToolTip(f"id={item['id']}\nstorage={item['storage_name']}\n"
                             f"mime={item['mime_type']}")
            entry.setData(Qt.ItemDataRole.UserRole, item["id"])
            self._items[item["id"]] = item
            self.list_widget.addItem(entry)

    def _thumbnail_icon(self, file_id: str) -> QIcon | None:
        path = os.path.join(self.vault.thumbnails_dir, f"{file_id}_t.cwo")
        if not os.path.isfile(path):
            return None
        try:
            with open(path, "rb") as f:
                raw = f.read()
            plain = cwo1.decrypt_whole_file(self.fek, file_id.encode("utf-8"), raw)
        except Exception:  # noqa: BLE001 - a missing/bad thumbnail must not break the list
            return None
        pixmap = QPixmap()
        if not pixmap.loadFromData(plain):
            return None
        return QIcon(pixmap)

    # ---------------------------------------------------------------- actions

    def _selected_id(self) -> str | None:
        entry = self.list_widget.currentItem()
        return entry.data(Qt.ItemDataRole.UserRole) if entry else None

    def _import(self):
        paths, _ = QFileDialog.getOpenFileNames(self, "Import into vault")
        if not paths:
            return
        os.makedirs(self.vault.attachments_dir, exist_ok=True)
        os.makedirs(self.vault.thumbnails_dir, exist_ok=True)
        imported = 0
        errors = []
        for path in paths:
            try:
                self._import_one(path)
                imported += 1
            except Exception as exc:  # noqa: BLE001 - report per file
                errors.append(f"{os.path.basename(path)}: {exc}")
        self.refresh()
        if errors:
            QMessageBox.warning(self, "Import",
                                f"Imported {imported}; failures:\n" + "\n".join(errors))
        elif imported:
            QMessageBox.information(self, "Import", f"Imported {imported} file(s).")

    def _import_one(self, path: str):
        file_id = new_uuid()
        aad = file_id.encode("utf-8")
        mime = mimetypes.guess_type(path)[0] or "application/octet-stream"
        original = os.path.basename(path)
        size = os.path.getsize(path)
        attachment = os.path.join(self.vault.attachments_dir, f"{file_id}.cwo")

        if mime.startswith("image/"):
            with open(path, "rb") as f:
                plain = f.read()
            with open(attachment, "wb") as f:
                f.write(cwo1.encrypt_whole_file(self.fek, aad, plain))
            thumb = self._jpeg_thumbnail(plain)
            if thumb:
                with open(os.path.join(self.vault.thumbnails_dir, f"{file_id}_t.cwo"), "wb") as f:
                    f.write(cwo1.encrypt_whole_file(self.fek, aad, thumb))
        else:
            cwo1.encrypt_file_chunked(self.fek, aad, path, attachment)

        self.repo.insert(file_id=file_id, storage_name=f"{file_id}.cwo",
                         original_name=original, mime_type=mime,
                         size_bytes=size)

    def _export(self):
        file_id = self._selected_id()
        if not file_id:
            QMessageBox.information(self, "Export", "Select a file first.")
            return
        item = self._items[file_id]
        attachment = os.path.join(self.vault.attachments_dir, item["storage_name"])
        if not os.path.isfile(attachment):
            QMessageBox.warning(self, "Export", f"Missing attachment: {attachment}")
            return
        if item["classification"] != "C":
            QMessageBox.warning(
                self, "Export",
                "S/T-tier files need the per-item DEK (fingerprint-gated on "
                "Android) and cannot be decrypted on the desktop.")
            return
        target, _ = QFileDialog.getSaveFileName(
            self, "Export decrypted file", item["original_name"] or file_id)
        if not target:
            return
        try:
            aad = file_id.encode("utf-8")
            with open(attachment, "rb") as f:
                head = f.read(cwo1.HEADER_LEN)
            header = cwo1.parse_header(head)
            if header.is_chunked:
                cwo1.decrypt_chunked_to_file(self.fek, aad, attachment, target)
            else:
                with open(attachment, "rb") as f:
                    plain = cwo1.decrypt_whole_file(self.fek, aad, f.read())
                with open(target, "wb") as f:
                    f.write(plain)
        except Exception as exc:  # noqa: BLE001
            QMessageBox.critical(self, "Export", str(exc))
            return
        QMessageBox.information(self, "Export", f"Exported to {target}")

    def _preview(self):
        file_id = self._selected_id()
        if not file_id:
            return
        item = self._items[file_id]
        if (item["mime_type"] or "").startswith("video/") or \
                (item["mime_type"] or "").startswith("audio/"):
            QMessageBox.information(self, "Preview",
                                    "Only images are previewed; use Export for video/audio.")
            return
        attachment = os.path.join(self.vault.attachments_dir, item["storage_name"])
        try:
            with open(attachment, "rb") as f:
                plain = cwo1.decrypt_whole_file(self.fek, file_id.encode("utf-8"), f.read())
        except Exception as exc:  # noqa: BLE001
            QMessageBox.critical(self, "Preview", str(exc))
            return
        image = QImage()
        if not image.loadFromData(plain):
            QMessageBox.warning(self, "Preview", "Cannot decode the decrypted image.")
            return
        PreviewDialog(image, item["original_name"] or file_id, self).exec()

    def _delete(self):
        file_id = self._selected_id()
        if not file_id:
            return
        if QMessageBox.question(
                self, "Delete media", "Delete this file from the vault?",
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        ) != QMessageBox.StandardButton.Yes:
            return
        item = self._items[file_id]
        self.repo.soft_delete(file_id)
        for path in (os.path.join(self.vault.attachments_dir, item["storage_name"]),
                     os.path.join(self.vault.thumbnails_dir, f"{file_id}_t.cwo")):
            if os.path.isfile(path):
                os.remove(path)
        self.refresh()

    # ---------------------------------------------------------------- helpers

    @staticmethod
    def _jpeg_thumbnail(plain: bytes) -> bytes | None:
        data = QByteArray(plain)
        buffer = QBuffer(data)
        if not buffer.open(QIODevice.OpenModeFlag.ReadOnly):
            return None
        reader = QImageReader(buffer)
        reader.setAutoTransform(True)  # honor EXIF orientation
        image = reader.read()
        buffer.close()
        if image.isNull():
            return None
        image = image.scaled(512, 512, Qt.AspectRatioMode.KeepAspectRatio,
                             Qt.TransformationMode.SmoothTransformation)
        out = QBuffer()
        out.open(QIODevice.OpenModeFlag.WriteOnly)
        try:
            ok = image.save(out, "JPEG", 85)
        except TypeError:
            # some PyQt6 builds require bytes for the const char* format arg
            ok = image.save(out, b"JPEG", 85)
        result = bytes(out.data()) if ok else None
        out.close()
        return result

    @staticmethod
    def _human(size: int) -> str:
        for unit in ("B", "KB", "MB", "GB"):
            if size < 1024 or unit == "GB":
                return f"{size:.0f} {unit}" if unit == "B" else f"{size:.1f} {unit}"
            size /= 1024.0
        return f"{size} B"


class PreviewDialog(QDialog):
    def __init__(self, image: QImage, title: str, parent=None):
        super().__init__(parent)
        self.setWindowTitle(f"Preview — {title}")
        label = QLabel()
        pixmap = QPixmap.fromImage(image)
        screen = self.screen().availableGeometry() if self.screen() else None
        if screen:
            pixmap = pixmap.scaled(int(screen.width() * 0.8),
                                   int(screen.height() * 0.8),
                                   Qt.AspectRatioMode.KeepAspectRatio,
                                   Qt.TransformationMode.SmoothTransformation)
        label.setPixmap(pixmap)
        layout = QVBoxLayout(self)
        layout.addWidget(label)
