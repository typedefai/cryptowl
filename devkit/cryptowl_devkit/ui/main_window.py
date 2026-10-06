from __future__ import annotations

"""VS2010-style workspace: menu bar + toolbar, docked Vault Navigator (left),
Properties (right) and Output (bottom), plus one tab per opened editor.
Locked state shows the login-style unlock panel instead.
"""

import json
import logging
import os

from PySide6.QtCore import QSize, Qt, Signal
from PySide6.QtGui import QAction
from PySide6.QtWidgets import (QLabel, QMainWindow, QMessageBox,
                               QPlainTextEdit, QSplitter, QStackedWidget,
                               QStyle, QTabWidget, QToolBar, QWidget)

from .. import __version__
from ..recent import RecentVaults
from ..vault import Vault, VaultError, key_fingerprint
from .dialogs import CreateVaultDialog
from .editors import (KeyChainEditor, OverviewEditor, SqlCipherCommandEditor,
                      TableViewer, TextViewer)
from .navigator import (CONFIG, DEVICE_SECRET, KEYS, META, OVERVIEW, SQLCIPHER,
                        TABLE, NavigatorTree)
from .properties import PropertiesView
from .unlock import UnlockPage

logger = logging.getLogger("devkit.ui")

OUTPUT_FORMAT = "%(asctime)s  %(levelname)-5s  %(name)s: %(message)s"


class OutputLogHandler(logging.Handler):
    def __init__(self, emit):
        super().__init__(level=logging.INFO)
        self.setFormatter(logging.Formatter(OUTPUT_FORMAT, "%H:%M:%S"))
        self._emit = emit

    def emit(self, record):
        try:
            self._emit(self.format(record))
        except Exception:  # noqa: BLE001 - logging must never raise
            pass


class MainWindow(QMainWindow):
    output_line = Signal(str)

    def __init__(self, start_dir: str | None = None):
        super().__init__()
        self.setWindowTitle(f"CryptOwl DevKit {__version__}")
        self.resize(800, 540)
        self.setMinimumSize(620, 420)
        self.vault: Vault | None = None
        self.recent = RecentVaults()
        self._editors = {}

        self._build_workspace()
        self._build_actions()
        self._build_activity_bar()
        self._build_menus()
        self._build_toolbar()
        self._build_statusbar()

        self.unlock_page.unlock_requested.connect(self._unlock)
        self.unlock_page.create_requested.connect(self._create_vault)
        self.navigator.open_requested.connect(self.open_editor)

        self._setup_output_logging()
        self._refresh_recents()
        prefill = start_dir or self.recent.latest_path() or ""
        if prefill:
            self.unlock_page.set_path(prefill)
        self._set_locked_ui(True)
        logger.info("CryptOwl DevKit %s ready", __version__)

    # -- layout -------------------------------------------------------------

    def _build_workspace(self) -> None:
        """VS Code-style layout: sidebar | (editor tabs above panel) | sidebar."""
        self.unlock_page = UnlockPage()

        self.tabs = QTabWidget()
        self.tabs.setTabsClosable(True)
        self.tabs.setMovable(True)
        self.tabs.setDocumentMode(True)
        self.tabs.tabCloseRequested.connect(self._close_editor)

        self.output = QPlainTextEdit()
        self.output.setObjectName("OutputView")
        self.output.setReadOnly(True)
        self.output.setMaximumBlockCount(2000)
        self.panel = QTabWidget()
        self.panel.setDocumentMode(True)
        self.panel.addTab(self.output, "Output")

        self.navigator = NavigatorTree()
        self.properties = PropertiesView()

        self.editors_split = QSplitter(Qt.Orientation.Vertical)
        self.editors_split.addWidget(self.tabs)
        self.editors_split.addWidget(self.panel)
        self.editors_split.setSizes([340, 120])
        self.editors_split.setCollapsible(0, False)

        self.workspace = QSplitter(Qt.Orientation.Horizontal)
        self.workspace.addWidget(self.navigator)
        self.workspace.addWidget(self.editors_split)
        self.workspace.addWidget(self.properties)
        self.workspace.setSizes([200, 400, 220])
        self.workspace.setStretchFactor(1, 1)
        self.workspace.setCollapsible(1, False)

        self.stack = QStackedWidget()
        self.stack.addWidget(self.unlock_page)
        self.stack.addWidget(self.workspace)
        self.setCentralWidget(self.stack)

    def _build_activity_bar(self) -> None:
        sp = QStyle.StandardPixmap
        self.act_navigator = self._panel_action(
            "Vault Navigator", sp.SP_DirIcon, self.navigator)
        self.act_properties = self._panel_action(
            "Properties", sp.SP_FileDialogDetailedView, self.properties)
        self.act_output = self._panel_action(
            "Output", sp.SP_FileDialogContentsView, self.panel)

        self.activity_bar = QToolBar("Activity", self)
        self.activity_bar.setObjectName("ActivityBar")
        self.activity_bar.setMovable(False)
        self.activity_bar.setIconSize(QSize(18, 18))
        self.activity_bar.setToolButtonStyle(
            Qt.ToolButtonStyle.ToolButtonIconOnly)
        for action in (self.act_navigator, self.act_properties,
                       self.act_output):
            self.activity_bar.addAction(action)
        self.addToolBar(Qt.ToolBarArea.LeftToolBarArea, self.activity_bar)

    def _panel_action(self, text: str, icon, widget: QWidget) -> QAction:
        action = QAction(self.style().standardIcon(icon), text, self)
        action.setCheckable(True)
        action.setChecked(True)
        action.setToolTip(text)
        action.setStatusTip(f"Show/hide {text}")
        action.toggled.connect(widget.setVisible)
        return action

    def _build_actions(self) -> None:
        sp = QStyle.StandardPixmap
        self.act_new = self._action(
            "&New vault…", sp.SP_FileDialogNewFolder, "Ctrl+N",
            self._create_vault, "Create a desktop-bound vault")
        self.act_open = self._action(
            "&Open vault…", sp.SP_DialogOpenButton, "Ctrl+O",
            self._open_from_menu, "Open an existing vault folder")
        self.act_lock = self._action(
            "&Lock", sp.SP_DialogCloseButton, "Ctrl+L",
            self.lock, "Close the vault and wipe session keys")
        self.act_sqlcipher = self._action(
            "SQLCipher &command…", sp.SP_FileDialogContentsView, "Ctrl+K",
            lambda: self.open_editor(SQLCIPHER, ""),
            "Commands to open vault.db directly with the SQLCipher CLI")
        self.act_refresh = self._action(
            "&Refresh", sp.SP_BrowserReload, "F5",
            self._refresh_views, "Reload navigator and properties")
        self.act_exit = self._action(
            "E&xit", sp.SP_DialogCancelButton, "Ctrl+Q", self.close)
        self.act_about = self._action(
            "&About", sp.SP_MessageBoxInformation, None, self._about)

    def _action(self, text, icon, shortcut, slot, tip=None) -> QAction:
        action = QAction(self.style().standardIcon(icon), text, self)
        action.triggered.connect(slot)
        if shortcut:
            action.setShortcut(shortcut)
        if tip:
            action.setStatusTip(tip)
            action.setToolTip(tip)
        return action

    def _build_menus(self) -> None:
        file_menu = self.menuBar().addMenu("&File")
        file_menu.addAction(self.act_new)
        file_menu.addAction(self.act_open)
        self.recent_menu = file_menu.addMenu("Recent &vaults")
        file_menu.addSeparator()
        file_menu.addAction(self.act_lock)
        file_menu.addSeparator()
        file_menu.addAction(self.act_exit)

        self.view_menu = self.menuBar().addMenu("&View")
        self.view_menu.addAction(self.act_navigator)
        self.view_menu.addAction(self.act_properties)
        self.view_menu.addAction(self.act_output)

        tools_menu = self.menuBar().addMenu("&Tools")
        tools_menu.addAction(self.act_sqlcipher)
        tools_menu.addSeparator()
        tools_menu.addAction(self.act_refresh)

        help_menu = self.menuBar().addMenu("&Help")
        help_menu.addAction(self.act_about)

    def _build_toolbar(self) -> None:
        self.toolbar = QToolBar("Standard", self)
        self.toolbar.setObjectName("StandardToolbar")
        self.toolbar.setMovable(False)
        self.toolbar.setIconSize(QSize(16, 16))
        self.addToolBar(Qt.ToolBarArea.TopToolBarArea, self.toolbar)
        for action in (self.act_new, self.act_open, self.act_lock,
                       self.act_refresh):
            self.toolbar.addAction(action)
        self.view_menu.addSeparator()
        self.view_menu.addAction(self.toolbar.toggleViewAction())

    def _build_statusbar(self) -> None:
        self.status_left = QLabel("Ready")
        self.statusBar().addWidget(self.status_left, 1)
        self.status_vault = QLabel("")
        self.status_schema = QLabel("")
        self.statusBar().addPermanentWidget(self.status_vault)
        self.statusBar().addPermanentWidget(self.status_schema)

    # -- logging ------------------------------------------------------------

    def _setup_output_logging(self) -> None:
        self.output_line.connect(self._append_output)
        root = logging.getLogger()
        if root.level > logging.INFO:
            root.setLevel(logging.INFO)
        for handler in list(root.handlers):
            if isinstance(handler, OutputLogHandler):
                root.removeHandler(handler)
        root.addHandler(OutputLogHandler(self.output_line.emit))

    def _append_output(self, line: str) -> None:
        self.output.appendPlainText(line)

    # -- recents ------------------------------------------------------------

    def _refresh_recents(self) -> None:
        entries = self.recent.entries()
        self.unlock_page.set_recents(entries)
        self.recent_menu.clear()
        if not entries:
            placeholder = self.recent_menu.addAction("(no recent vaults)")
            placeholder.setEnabled(False)
            return
        for entry in entries:
            action = self.recent_menu.addAction(entry["name"])
            action.setToolTip(entry["path"])
            action.triggered.connect(
                lambda _checked=False, p=entry["path"]: self._open_recent(p))

    def _open_recent(self, path: str) -> None:
        if self.vault is not None:
            return
        self.unlock_page.set_path(path, focus=True)

    # -- unlock / lock ------------------------------------------------------

    def _unlock(self, path: str, password: str) -> None:
        try:
            vault = Vault.open(path, password.encode("utf-8"))
        except VaultError as exc:
            logger.warning("open failed for %s: %s", path, exc)
            QMessageBox.warning(self, "Unlock", str(exc))
            return
        except Exception as exc:  # noqa: BLE001 - native errors can be Error
            logger.exception("unexpected open failure for %s", path)
            QMessageBox.critical(self, "Unlock",
                                 f"Could not open the vault:\n{exc}")
            return
        self._adopt_vault(vault)
        self.status_left.setText(f"Unlocked {vault.path}")
        logger.info("opened vault '%s' at %s (schema v%d, %d tables)",
                    vault.vault_id, vault.path, vault.schema_version,
                    len(vault.tables))

    def _create_vault(self) -> None:
        if self.vault is not None:
            return
        dialog = CreateVaultDialog(self, start_dir=self._start_dir())
        if not dialog.exec():
            return
        path, vault_id, name, password = dialog.values()
        try:
            vault = Vault.create(path, password.encode("utf-8"),
                                 vault_id=vault_id, name=name)
        except VaultError as exc:
            logger.warning("create failed for %s: %s", path, exc)
            QMessageBox.warning(self, "Create vault", str(exc))
            return
        except Exception as exc:  # noqa: BLE001 - native errors can be Error
            logger.exception("unexpected create failure for %s", path)
            QMessageBox.critical(self, "Create vault",
                                 f"Could not create the vault:\n{exc}")
            return
        self._adopt_vault(vault)
        self.status_left.setText(f"Created vault at {vault.path}")
        logger.info("created vault '%s' at %s", vault.vault_id, vault.path)

    def _adopt_vault(self, vault: Vault) -> None:
        self.vault = vault
        self.recent.add(vault.path, vault.name)
        self._refresh_recents()
        self._close_all_editors()
        self.navigator.set_vault(vault)
        self.properties.show_vault(vault)
        self.status_vault.setText(f"  {vault.vault_id}  ")
        self.status_schema.setText(f"  schema v{vault.schema_version}  ")
        self.stack.setCurrentWidget(self.workspace)
        self._set_locked_ui(False)
        self.open_editor(OVERVIEW, "")

    def lock(self) -> None:
        path = self.vault.path if self.vault is not None else None
        if self.vault is not None:
            self.vault.close()
            self.vault = None
        self._close_all_editors()
        self.navigator.clear_vault()
        self.properties.clear()
        if path:
            self.unlock_page.set_path(path)
        self.stack.setCurrentWidget(self.unlock_page)
        self.unlock_page.refresh_detection()
        self.unlock_page.password_edit.setFocus()
        self._set_locked_ui(True)
        self.status_left.setText("Locked")
        logger.info("vault locked")

    def _set_locked_ui(self, locked: bool) -> None:
        for action in (self.act_lock, self.act_refresh, self.act_sqlcipher):
            action.setEnabled(not locked)
        for action in (self.act_new, self.act_open):
            action.setEnabled(locked)
        self.activity_bar.setVisible(not locked)
        self.toolbar.setVisible(not locked)
        if locked:
            self.status_vault.setText("")
            self.status_schema.setText("")

    def _start_dir(self) -> str:
        current = self.unlock_page.current_path()
        if current:
            return os.path.dirname(current)
        return os.path.expanduser("~")

    def _open_from_menu(self) -> None:
        if self.vault is None:
            self.unlock_page.browse()

    def _refresh_views(self) -> None:
        if self.vault is None:
            return
        self.navigator.set_vault(self.vault)
        self.properties.show_vault(self.vault)
        self.status_schema.setText(f"  schema v{self.vault.schema_version}  ")
        logger.info("views refreshed")

    # -- editors ------------------------------------------------------------

    def open_editor(self, kind: str, key: str = "") -> None:
        if self.vault is None:
            return
        editor_id = f"{kind}:{key}"
        existing = self._editors.get(editor_id)
        if existing is not None:
            self.tabs.setCurrentWidget(existing)
            return
        widget = self._build_editor(kind, key)
        if widget is None:
            return
        index = self.tabs.addTab(widget, self._title_for(kind, key))
        self._editors[editor_id] = widget
        self.tabs.setCurrentIndex(index)

    def _build_editor(self, kind: str, key: str):
        vault = self.vault
        if kind == OVERVIEW:
            return OverviewEditor(vault)
        if kind == KEYS:
            return KeyChainEditor(vault)
        if kind == SQLCIPHER:
            return SqlCipherCommandEditor(vault)
        if kind == META:
            return TextViewer(_pretty_json(vault.meta))
        if kind == CONFIG:
            return TextViewer(_pretty_json(vault.config))
        if kind == DEVICE_SECRET:
            if vault.device_secret is None:
                return TextViewer("device_secret is not available")
            return TextViewer(
                f"# this vault's Device Secret (Keystore analog)\n"
                f"hex:         {vault.device_secret.hex()}\n"
                f"fingerprint: {key_fingerprint(vault.device_secret)}")
        if kind == TABLE:
            return TableViewer(vault, key)
        return None

    def _title_for(self, kind: str, key: str) -> str:
        return {OVERVIEW: "Overview", KEYS: "Key chain",
                SQLCIPHER: "SQLCipher", META: "vault.meta",
                CONFIG: "config.json", DEVICE_SECRET: "device_secret",
                TABLE: key}.get(kind, key or kind)

    def _close_editor(self, index: int) -> None:
        widget = self.tabs.widget(index)
        for editor_id, known in list(self._editors.items()):
            if known is widget:
                del self._editors[editor_id]
                break
        self.tabs.removeTab(index)
        widget.deleteLater()

    def _close_all_editors(self) -> None:
        for index in reversed(range(self.tabs.count())):
            self._close_editor(index)

    # -- misc ---------------------------------------------------------------

    def _about(self) -> None:
        QMessageBox.about(
            self, "About CryptOwl DevKit",
            f"<b>CryptOwl DevKit {__version__}</b><br><br>"
            "Developer workbench for CryptOwl vaults — byte-compatible with "
            "the Android app (SQLCipher raw key, no re-encryption).<br><br>"
            "The Properties panel and Key chain tab show live derived key "
            "material; treat them as secrets.")

    def closeEvent(self, event) -> None:
        if self.vault is not None:
            self.vault.close()
            self.vault = None
        super().closeEvent(event)


def _pretty_json(obj: dict) -> str:
    return json.dumps(obj, indent=2, ensure_ascii=False, sort_keys=True)
