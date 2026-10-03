from __future__ import annotations

"""Main window — Visual Studio 2010-style shell.

Layout: menu bar + standard toolbar on top, a docked Vault Explorer on the
left, document tabs in the centre, a Properties tool window on the right, an
Output tool window at the bottom, and a blue status bar.
"""

import logging
import os
import shutil
import sys
import time

from PyQt6.QtCore import QSize, Qt, pyqtSignal
from PyQt6.QtGui import QAction
from PyQt6.QtWidgets import (QApplication, QDockWidget, QLabel, QListWidget,
                             QListWidgetItem, QMainWindow, QMenu,
                             QPlainTextEdit, QStackedWidget, QStyle,
                             QTabWidget, QToolBar, QTreeWidget, QTreeWidgetItem,
                             QVBoxLayout, QWidget)

from .. import __version__
from ..vault import Vault
from ..vault.schema import expected_version
from .dialogs import ChangePasswordDialog, CreateVaultDialog, OpenVaultDialog
from .media_tab import MediaTab
from .notes_tab import NotesTab
from .readonly_tabs import DebugTab, MomentsTab, PasswordsTab
from .recent import RecentVaults
from .theme import apply_vs2010_theme

# explorer document ids -> (title, factory)
DOCUMENTS = [
    ("notes", "Notes", NotesTab),
    ("media", "Media", MediaTab),
    ("moments", "Moments", MomentsTab),
    ("passwords", "Passwords", PasswordsTab),
    ("debug", "Debug", DebugTab),
]


def _setup_logging(level=logging.INFO) -> None:
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)-5s %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )


class OutputLogHandler(logging.Handler):
    """Forwards log records to the Output tool window via a Qt signal."""

    def __init__(self, emit):
        super().__init__(level=logging.INFO)
        self.setFormatter(logging.Formatter(
            "%(asctime)s  %(levelname)-5s  %(name)s: %(message)s", "%H:%M:%S"))
        self._emit = emit

    def emit(self, record):
        try:
            self._emit(self.format(record))
        except Exception:  # noqa: BLE001 - logging must never raise
            pass


class WelcomePage(QWidget):
    def __init__(self, on_open, on_create, on_open_path, on_forget, parent=None):
        super().__init__(parent)
        self._on_open_path = on_open_path
        self._on_forget = on_forget

        title = QLabel("CryptOwl Desktop")
        title.setObjectName("WelcomeTitle")
        subtitle = QLabel(
            "Desktop manager and debugging tool for CryptOwl vaults.\n"
            "Byte-compatible with the Android app: vault.meta, SQLCipher database "
            "and CWO1 encrypted files.")
        subtitle.setObjectName("WelcomeSubtitle")
        subtitle.setWordWrap(True)

        open_btn = QLabel('<a href="open">Open vault…</a>')
        create_btn = QLabel('<a href="create">Create vault…</a>')
        for label, handler in ((open_btn, on_open), (create_btn, on_create)):
            label.setTextInteractionFlags(Qt.TextInteractionFlag.LinksAccessibleByMouse)
            label.linkActivated.connect(lambda _href, h=handler: h())

        recent_label = QLabel("Recent vaults")
        recent_label.setObjectName("SectionHeader")
        self.empty_label = QLabel("No recent vaults yet.")
        self.empty_label.setObjectName("SectionHint")
        self.recent_list = QListWidget()
        self.recent_list.setObjectName("RecentList")
        self.recent_list.setMaximumHeight(150)
        self.recent_list.setMinimumWidth(460)
        self.recent_list.itemActivated.connect(self._activated)
        self.recent_list.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.recent_list.customContextMenuRequested.connect(self._context_menu)

        note = QLabel(
            "Not supported on desktop: fingerprint/Top-Secret keys (Android Keystore) "
            "and vaults already re-bound to an Android device.")
        note.setObjectName("SectionHint")
        note.setWordWrap(True)

        layout = QVBoxLayout(self)
        layout.addStretch(1)
        layout.addWidget(title)
        layout.addWidget(subtitle)
        layout.addSpacing(6)
        for widget in (open_btn, create_btn):
            layout.addWidget(widget)
        layout.addSpacing(14)
        layout.addWidget(recent_label)
        layout.addWidget(self.empty_label)
        layout.addWidget(self.recent_list)
        layout.addSpacing(10)
        layout.addWidget(note)
        layout.addStretch(2)
        layout.setContentsMargins(32, 20, 32, 20)

    def set_recents(self, entries):
        self.recent_list.clear()
        for entry in entries:
            missing = not os.path.isfile(os.path.join(entry["path"], "vault.meta"))
            label = f"{entry['name']}   —   {entry['path']}"
            if missing:
                label += "    (missing)"
            item = QListWidgetItem(label)
            item.setData(Qt.ItemDataRole.UserRole, entry["path"])
            item.setToolTip("Double-click to unlock")
            self.recent_list.addItem(item)
        self.recent_list.setVisible(bool(entries))
        self.empty_label.setVisible(not entries)

    def _activated(self, item):
        if item is not None:
            self._on_open_path(item.data(Qt.ItemDataRole.UserRole))

    def _context_menu(self, pos):
        item = self.recent_list.itemAt(pos)
        if item is None:
            return
        path = item.data(Qt.ItemDataRole.UserRole)
        menu = QMenu(self)
        menu.addAction("Open", lambda: self._on_open_path(path))
        menu.addAction("Remove from list", lambda: self._on_forget(path))
        menu.exec(self.recent_list.mapToGlobal(pos))


class MainWindow(QMainWindow):
    output_line = pyqtSignal(str)

    def __init__(self, start_dir: str | None = None):
        super().__init__()
        self.setWindowTitle(f"CryptOwl Desktop {__version__}")
        self.resize(1000, 660)
        self.setMinimumSize(760, 520)
        self.vault = None
        self._documents = {}
        self.recent = RecentVaults()
        self._start_dir = (start_dir or self.recent.latest_path()
                           or os.path.expanduser("~"))

        self._create_actions()
        self._create_toolbar()
        self._create_workspace()
        self._create_menus()
        self._create_statusbar()

        self.output_line.connect(self._append_output)
        root = logging.getLogger()
        for handler in list(root.handlers):
            if isinstance(handler, OutputLogHandler):
                root.removeHandler(handler)
        root.addHandler(OutputLogHandler(self.output_line.emit))
        logging.getLogger("ui").info("CryptOwl Desktop %s ready", __version__)

        self._refresh_recents()
        self._set_locked_ui(True)

    # -------------------------------------------------------------- actions

    def _create_actions(self):
        self.act_open = self._action(
            "&Open vault…", QStyle.StandardPixmap.SP_DialogOpenButton,
            self.open_vault_dialog, "Open an existing vault folder")
        self.act_create = self._action(
            "&New vault…", QStyle.StandardPixmap.SP_FileDialogNewFolder,
            self.create_vault_dialog, "Create a desktop-bound vault")
        self.act_lock = self._action(
            "&Lock", QStyle.StandardPixmap.SP_DialogCloseButton,
            self.lock, "Close the vault and wipe session keys")
        self.act_backup = self._action(
            "&Backup copy…", QStyle.StandardPixmap.SP_DialogSaveButton,
            self.backup_copy, "Copy the encrypted vault folder")
        self.act_change_password = self._action(
            "Change master &password…", QStyle.StandardPixmap.SP_DialogApplyButton,
            self.change_password, "Rewrap the vault key with a new password")
        self.act_refresh = self._action(
            "&Refresh", QStyle.StandardPixmap.SP_BrowserReload,
            self.refresh_document, "Reload the current document")
        self.act_about = self._action(
            "&About", QStyle.StandardPixmap.SP_MessageBoxInformation, self.about)
        self.act_exit = self._action(
            "E&xit", QStyle.StandardPixmap.SP_DialogCancelButton, self.close)

    def _action(self, text, icon, slot, tip=None):
        action = QAction(self.style().standardIcon(icon), text, self)
        action.triggered.connect(slot)
        if tip:
            action.setStatusTip(tip)
            action.setToolTip(tip)
        return action

    def _create_menus(self):
        file_menu = self.menuBar().addMenu("&File")
        file_menu.addAction(self.act_open)
        file_menu.addAction(self.act_create)
        self.recent_menu = file_menu.addMenu("Recent &vaults")
        file_menu.addSeparator()
        file_menu.addAction(self.act_lock)
        file_menu.addSeparator()
        file_menu.addAction(self.act_backup)
        file_menu.addSeparator()
        file_menu.addAction(self.act_exit)

        view_menu = self.menuBar().addMenu("&View")
        view_menu.addAction(self.explorer_dock.toggleViewAction())
        view_menu.addAction(self.properties_dock.toggleViewAction())
        view_menu.addAction(self.output_dock.toggleViewAction())
        view_menu.addSeparator()
        view_menu.addAction(self.toolbar.toggleViewAction())

        tools_menu = self.menuBar().addMenu("&Tools")
        tools_menu.addAction(self.act_change_password)
        tools_menu.addAction(self.act_refresh)

        help_menu = self.menuBar().addMenu("&Help")
        help_menu.addAction(self.act_about)

    def _create_toolbar(self):
        self.toolbar = QToolBar("Standard", self)
        self.toolbar.setObjectName("StandardToolbar")
        self.toolbar.setMovable(False)
        self.toolbar.setIconSize(QSize(16, 16))
        self.toolbar.setToolButtonStyle(Qt.ToolButtonStyle.ToolButtonIconOnly)
        self.addToolBar(Qt.ToolBarArea.TopToolBarArea, self.toolbar)
        for action in (self.act_open, self.act_create):
            self.toolbar.addAction(action)
        self.toolbar.addSeparator()
        for action in (self.act_lock, self.act_backup, self.act_change_password):
            self.toolbar.addAction(action)
        self.toolbar.addSeparator()
        self.toolbar.addAction(self.act_refresh)

    # ------------------------------------------------------------ workspace

    def _create_workspace(self):
        # central document area (welcome page until a vault is open)
        self.welcome = WelcomePage(
            self.open_vault_dialog, self.create_vault_dialog,
            self.open_recent, self.forget_recent)
        self.documents = QTabWidget()
        self.documents.setTabsClosable(True)
        self.documents.setMovable(True)
        self.documents.tabCloseRequested.connect(self._close_document)
        self.documents.currentChanged.connect(lambda _: self._refresh_properties())
        self.central_stack = QStackedWidget()
        self.central_stack.addWidget(self.welcome)
        self.central_stack.addWidget(self.documents)
        self.setCentralWidget(self.central_stack)

        # left: vault explorer
        self.explorer = QTreeWidget()
        self.explorer.setHeaderHidden(True)
        self.explorer.setRootIsDecorated(True)
        self.explorer.itemClicked.connect(self._explorer_activated)
        self.explorer.itemActivated.connect(self._explorer_activated)
        self.explorer_dock = QDockWidget("Vault Explorer", self)
        self.explorer_dock.setObjectName("ExplorerDock")
        self.explorer_dock.setWidget(self.explorer)
        self.explorer_dock.setFeatures(
            QDockWidget.DockWidgetFeature.DockWidgetMovable |
            QDockWidget.DockWidgetFeature.DockWidgetFloatable)
        self.addDockWidget(Qt.DockWidgetArea.LeftDockWidgetArea, self.explorer_dock)

        # right: properties
        self.properties = QTreeWidget()
        self.properties.setColumnCount(2)
        self.properties.setHeaderLabels(["Property", "Value"])
        self.properties.setRootIsDecorated(True)
        self.properties.setAlternatingRowColors(True)
        self.properties.header().setStretchLastSection(True)
        self.properties.setColumnWidth(0, 150)
        self.properties_dock = QDockWidget("Properties", self)
        self.properties_dock.setObjectName("PropertiesDock")
        self.properties_dock.setWidget(self.properties)
        self.properties_dock.setFeatures(
            QDockWidget.DockWidgetFeature.DockWidgetMovable |
            QDockWidget.DockWidgetFeature.DockWidgetFloatable)
        self.addDockWidget(Qt.DockWidgetArea.RightDockWidgetArea, self.properties_dock)

        # bottom: output
        self.output = QPlainTextEdit()
        self.output.setObjectName("OutputView")
        self.output.setReadOnly(True)
        self.output.setMaximumBlockCount(2000)
        self.output_dock = QDockWidget("Output", self)
        self.output_dock.setObjectName("OutputDock")
        self.output_dock.setWidget(self.output)
        self.output_dock.setFeatures(
            QDockWidget.DockWidgetFeature.DockWidgetMovable |
            QDockWidget.DockWidgetFeature.DockWidgetFloatable)
        self.addDockWidget(Qt.DockWidgetArea.BottomDockWidgetArea, self.output_dock)

        self.resizeDocks([self.explorer_dock], [230], Qt.Orientation.Horizontal)
        self.resizeDocks([self.properties_dock], [260], Qt.Orientation.Horizontal)
        self.resizeDocks([self.output_dock], [140], Qt.Orientation.Vertical)

    def _create_statusbar(self):
        self.status_left = QLabel("Ready")
        self.statusBar().addWidget(self.status_left, 1)
        self.status_vault = QLabel("No vault")
        self.status_schema = QLabel("")
        self.statusBar().addPermanentWidget(self.status_vault)
        self.statusBar().addPermanentWidget(self.status_schema)

    # -------------------------------------------------------------- recents

    def _refresh_recents(self):
        self.welcome.set_recents(self.recent.entries())
        self.recent_menu.clear()
        entries = self.recent.entries()
        if not entries:
            placeholder = self.recent_menu.addAction("(no recent vaults)")
            placeholder.setEnabled(False)
            return
        for entry in entries:
            action = self.recent_menu.addAction(entry["name"])
            action.setToolTip(entry["path"])
            action.setStatusTip(entry["path"])
            action.triggered.connect(
                lambda _checked=False, p=entry["path"]: self.open_recent(p))
        self.recent_menu.addSeparator()
        self.recent_menu.addAction("Clear list", self.clear_recents)

    # ----------------------------------------------------------- documents

    def open_document(self, key: str):
        if self.vault is None:
            return
        existing = self._documents.get(key)
        if existing is not None:
            self.documents.setCurrentWidget(existing)
            return
        entry = next((e for e in DOCUMENTS if e[0] == key), None)
        if entry is None:
            return
        _, title, factory = entry
        widget = factory(self.vault)
        widget.on_properties_changed = self._refresh_properties
        index = self.documents.addTab(widget, title)
        self._documents[key] = widget
        self.documents.setCurrentIndex(index)
        self._refresh_properties()

    def _close_document(self, index: int):
        widget = self.documents.widget(index)
        for key, known in list(self._documents.items()):
            if known is widget:
                del self._documents[key]
                break
        self.documents.removeTab(index)
        widget.deleteLater()
        self._refresh_properties()

    def refresh_document(self):
        current = self.documents.currentWidget()
        if current is not None and hasattr(current, "refresh"):
            current.refresh()
            self._refresh_properties()

    def _explorer_activated(self, item: QTreeWidgetItem, _column: int = 0):
        key = item.data(0, Qt.ItemDataRole.UserRole)
        if key:
            self.open_document(key)

    def _populate_explorer(self):
        self.explorer.clear()
        root = QTreeWidgetItem([f"Vault ({self.vault.vault_id})"])
        root.setIcon(0, self.style().standardIcon(QStyle.StandardPixmap.SP_DirOpenIcon))
        for key, title, _ in DOCUMENTS:
            child = QTreeWidgetItem([title])
            child.setData(0, Qt.ItemDataRole.UserRole, key)
            child.setIcon(0, self.style().standardIcon(
                QStyle.StandardPixmap.SP_FileIcon))
            root.addChild(child)
        self.explorer.addTopLevelItem(root)
        root.setExpanded(True)

    # ---------------------------------------------------------- properties

    def _refresh_properties(self):
        self.properties.clear()
        if self.vault is None:
            return
        user_version = "?"
        row = self.vault.conn.query_one("PRAGMA user_version")
        if row:
            user_version = str(row[0])
        vault_rows = [
            ("Name", self.vault.config.get("name") or self.vault.vault_id),
            ("Vault id", self.vault.vault_id),
            ("Path", self.vault.path),
            ("Schema", f"v{user_version} (expected v{expected_version()})"),
            ("VaultKey fp", self.vault.vault_key_fingerprint),
            ("FEK fp", self.vault.fek_fingerprint),
        ]
        self._add_property_section("Vault", vault_rows)

        current = self.documents.currentWidget()
        if current is not None and hasattr(current, "properties"):
            index = self.documents.currentIndex()
            self._add_property_section(self.documents.tabText(index), current.properties())

    def _add_property_section(self, name, rows):
        top = QTreeWidgetItem([name, ""])
        top.setExpanded(True)
        for key, value in rows:
            top.addChild(QTreeWidgetItem([str(key), str(value)]))
        self.properties.addTopLevelItem(top)

    # ------------------------------------------------------------- output

    def _append_output(self, line: str):
        self.output.appendPlainText(line)

    def append_output(self, line: str):
        self._append_output(line)

    # ------------------------------------------------------------ lifecycle

    def _set_locked_ui(self, locked: bool):
        self.act_lock.setEnabled(not locked)
        self.act_backup.setEnabled(not locked)
        self.act_change_password.setEnabled(not locked)
        self.act_refresh.setEnabled(not locked)
        for dock in (self.explorer_dock, self.properties_dock, self.output_dock):
            dock.setVisible(not locked)
        self.toolbar.setVisible(not locked)
        if locked:
            self.central_stack.setCurrentWidget(self.welcome)
            self.status_vault.setText("No vault")
            self.status_schema.setText("")
            self.status_left.setText("Ready")
        else:
            self.central_stack.setCurrentWidget(self.documents)

    def open_vault_dialog(self, *_):
        self._open_vault_dialog(prefill=None)

    def open_recent(self, path: str):
        self._open_vault_dialog(prefill=path)

    def _open_vault_dialog(self, prefill=None):
        dialog = OpenVaultDialog(self, prefill or self._start_dir)
        if dialog.exec():
            path, password = dialog.values()
            self._start_dir = os.path.dirname(path)
            self._unlock(path, password)

    def forget_recent(self, path: str):
        self.recent.remove(path)
        self._refresh_recents()

    def clear_recents(self):
        self.recent.clear()
        self._refresh_recents()

    def create_vault_dialog(self, *_):
        dialog = CreateVaultDialog(self, self._start_dir)
        if not dialog.exec():
            return
        path, vault_id, name, password = dialog.values()
        self._start_dir = os.path.dirname(path)
        try:
            self.vault = Vault.create(path, password.encode("utf-8"),
                                      vault_id=vault_id, name=name)
        except Exception as exc:  # noqa: BLE001 - surfaced to the user
            logging.getLogger("ui").exception("create failed")
            from PyQt6.QtWidgets import QMessageBox
            QMessageBox.critical(self, "Create vault", str(exc))
            return
        self._enter_workspace()
        self.append_output(f"Created vault at {path}")

    def _unlock(self, path: str, password: str):
        from PyQt6.QtWidgets import QMessageBox
        try:
            self.vault = Vault.open(path, password.encode("utf-8"))
        except FileNotFoundError as exc:
            QMessageBox.warning(self, "Open vault", str(exc))
            return
        except Exception as exc:  # noqa: BLE001
            logging.getLogger("ui").exception("open failed")
            QMessageBox.critical(self, "Open vault",
                                 f"Could not open the vault:\n{exc}")
            return
        self._enter_workspace()
        self.append_output(f"Unlocked {path}")

    def _enter_workspace(self):
        for index in reversed(range(self.documents.count())):
            self._close_document(index)
        self._populate_explorer()
        self.recent.add(self.vault.path,
                        self.vault.config.get("name") or self.vault.vault_id)
        self._refresh_recents()
        self._set_locked_ui(False)
        self.status_vault.setText(f"  {self.vault.vault_id}  ")
        row = self.vault.conn.query_one("PRAGMA user_version")
        self.status_schema.setText(f"  schema v{row[0] if row else 0}  ")
        self.status_left.setText(f"Unlocked {self.vault.path}")
        self.open_document("notes")
        self._refresh_properties()

    def lock(self):
        if self.vault is not None:
            self.vault.close()
            self.vault = None
        for index in reversed(range(self.documents.count())):
            self._close_document(index)
        self.explorer.clear()
        self.properties.clear()
        self._set_locked_ui(True)
        logging.getLogger("ui").info("vault locked")

    # --------------------------------------------------------------- tools

    def change_password(self):
        if self.vault is None:
            return
        ChangePasswordDialog(self.vault, self).exec()

    def backup_copy(self):
        from PyQt6.QtWidgets import QFileDialog, QMessageBox
        if self.vault is None:
            return
        target_root = QFileDialog.getExistingDirectory(
            self, "Choose backup folder", self._start_dir)
        if not target_root:
            return
        stamp = time.strftime("%Y%m%d-%H%M%S")
        target = os.path.join(target_root, f"cryptowl-backup-{stamp}")
        try:
            shutil.copytree(self.vault.path, target)
        except Exception as exc:  # noqa: BLE001
            QMessageBox.critical(self, "Backup", str(exc))
            return
        self.append_output(f"Backup written to {target}")
        QMessageBox.information(self, "Backup", f"Backup written to {target}")

    def about(self):
        from PyQt6.QtWidgets import QMessageBox
        QMessageBox.about(
            self, "About CryptOwl Desktop",
            f"<b>CryptOwl Desktop {__version__}</b><br><br>"
            "Desktop manager and debugging tool for CryptOwl vaults.<br>"
            "Byte-compatible with the Android app: vault.meta, SQLCipher raw-key "
            "database and CWO1 encrypted files.<br><br>"
            "Fingerprint-protected keys (Secret/Top-Secret tiers) are Android-only.")

    def closeEvent(self, event):
        if self.vault is not None:
            self.vault.close()
        super().closeEvent(event)


def main():
    import argparse

    parser = argparse.ArgumentParser(description="CryptOwl desktop vault tool")
    parser.add_argument("vault", nargs="?", help="vault folder to prefill")
    parser.add_argument("--debug", action="store_true", help="verbose logging")
    args = parser.parse_args()

    _setup_logging(logging.DEBUG if args.debug else logging.INFO)

    app = QApplication(sys.argv)
    app.setApplicationName("CryptOwl Desktop")
    apply_vs2010_theme(app)
    window = MainWindow(start_dir=args.vault)
    window.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
