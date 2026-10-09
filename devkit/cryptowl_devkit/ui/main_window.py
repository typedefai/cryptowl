from __future__ import annotations

"""Windows Explorer-style window (Win10+): ribbon tabs (File / Home / Manage),
address bar (back, clickable path, name filter, view modes), sidebar (pinned /
root / types) and content pane. Item editors are separate popup windows."""

import json
import logging
import os

from PySide6.QtCore import QSize, Qt
from PySide6.QtGui import QAction, QActionGroup, QKeySequence, QShortcut
from PySide6.QtWidgets import (QInputDialog, QLabel, QLineEdit, QMainWindow,
                               QMenu, QMessageBox, QSizePolicy, QSplitter,
                               QStackedWidget, QTabWidget, QToolBar,
                               QToolButton, QVBoxLayout, QWidget)

from .. import __version__
from ..recent import RecentVaults
from ..vault import (FOLDER_TYPE, ItemDraft, ItemRepository, Vault,
                     VaultError, key_fingerprint)
from ..vault.sqlcipher import SqlCipherError
from . import icons
from .browser import DETAILS, ICONS, LIST, ContentView
from .dialogs import CreateVaultDialog
from .editors import (KeyChainEditor, OverviewEditor, SqlCipherCommandEditor,
                      TextViewer)
from .editor_window import ItemWindow
from .registry import REGISTRY, get_type
from .sidebar import Sidebar
from .unlock import UnlockPage

logger = logging.getLogger("devkit.ui")


class MainWindow(QMainWindow):
    def __init__(self, start_dir: str | None = None):
        super().__init__()
        self.setWindowTitle(f"CryptOwl DevKit {__version__}")
        self.resize(940, 620)
        self.setMinimumSize(680, 460)
        self.vault: Vault | None = None
        self.items_repo: ItemRepository | None = None
        self.recent = RecentVaults()
        self._editor_windows: set[ItemWindow] = set()
        self._viewer_windows: set[QMainWindow] = set()
        self._selected = None
        self._state = ("folder", None)
        self._last_browse = ("folder", None)

        self._build_actions()
        self._build_menus()
        self._build_ribbon()
        self._build_address_bar()
        self._build_workspace()
        self._build_statusbar()

        self.unlock_page.unlock_requested.connect(self._unlock)
        self.unlock_page.create_requested.connect(self._create_vault)

        self._refresh_recents()
        prefill = start_dir or self.recent.latest_path() or ""
        if prefill:
            self.unlock_page.set_path(prefill)
        self._set_locked_ui(True)
        logger.info("CryptOwl DevKit %s ready", __version__)

    # -- actions ------------------------------------------------------------

    def _build_actions(self) -> None:
        self.act_back = self._action(
            "&Back", icons.icon("arrow_left"), "Alt+Up", self._go_up,
            "Go to the parent folder")
        self.act_new_vault = self._action(
            "&New vault…", icons.icon("add", icons.ACCENT), "Ctrl+N",
            self._create_vault, "Create a desktop-bound vault")
        self.act_open_vault = self._action(
            "&Open vault…", icons.icon("folder_open_filled", icons.FOLDER), "Ctrl+O",
            self._open_from_menu, "Open an existing vault folder")
        self.act_lock = self._action(
            "&Lock", icons.icon("lock"), "Ctrl+L", self.lock,
            "Close the vault and wipe session keys")
        self.act_refresh = self._action(
            "&Refresh", icons.icon("arrow_clockwise"), "F5", self._rebuild,
            "Reload the sidebar and content")
        self.act_find = self._action(
            "&Find", icons.icon("search"), "Ctrl+F",
            self._focus_search, "Filter the current folder by name")
        self.act_rename = self._action(
            "&Rename", icons.icon("rename"), "F2",
            self._rename_selected, "Rename the selected item")
        self.act_delete = self._action(
            "&Delete", icons.icon("delete"), QKeySequence.StandardKey.Delete,
            self._delete_selected, "Delete the selection (cascades for folders)")
        self.act_pin = self._action(
            "Pin", icons.icon("pin"), None,
            self._pin_selected, "Pin the selection (folders show under Pinned)")
        self.act_history = self._action(
            "&History…", icons.icon("history"), "Ctrl+H",
            self._history_selected, "Show versions of the selected item")
        self.act_new_folder = self._action(
            "New &folder", icons.icon("folder_add", icons.FOLDER), "Ctrl+Shift+N",
            self._new_folder, "Create a folder in the current location")
        self.act_exit = self._action(
            "E&xit", icons.icon("arrow_exit"), "Ctrl+Q", self.close)
        self.act_about = self._action(
            "&About", icons.icon("info"), None, self._about)

        self.view_group = QActionGroup(self)
        self.act_view_icons = self._view_action(
            "&Icons", icons.icon("grid"), ICONS)
        self.act_view_list = self._view_action(
            "&List", icons.icon("list"), LIST)
        self.act_view_details = self._view_action(
            "&Details", icons.icon("table"), DETAILS)
        self.act_view_icons.setChecked(True)

        self.act_keychain = self._action(
            "&Key chain…", icons.icon("key"), None,
            lambda: self._viewer("Key chain", KeyChainEditor(self.vault)))
        self.act_sqlcipher = self._action(
            "SQLCipher &command…", icons.icon("code"), "Ctrl+K",
            lambda: self._viewer("SQLCipher command",
                                 SqlCipherCommandEditor(self.vault)))
        self.act_overview = self._action(
            "&Overview…", icons.icon("home"), None,
            lambda: self._viewer("Overview", OverviewEditor(self.vault)))
        self.act_meta = self._action(
            "vault.&meta…", icons.icon("document"), None,
            lambda: self._viewer("vault.meta",
                                 TextViewer(_pretty_json(self.vault.meta))))
        self.act_config = self._action(
            "&config.json…", icons.icon("document"), None,
            lambda: self._viewer("config.json",
                                 TextViewer(_pretty_json(self.vault.config))))
        self.act_secret = self._action(
            "&device_secret…", icons.icon("key"), None,
            self._show_device_secret)

        self._tool_actions = (self.act_keychain, self.act_sqlcipher,
                              self.act_overview, self.act_meta,
                              self.act_config, self.act_secret)

    def _action(self, text, icon, shortcut, slot, tip=None) -> QAction:
        action = QAction(icon, text, self)
        action.triggered.connect(slot)
        if shortcut:
            action.setShortcut(shortcut)
        if tip:
            action.setStatusTip(tip)
            action.setToolTip(tip)
        return action

    def _view_action(self, text, icon, mode: str) -> QAction:
        action = QAction(icon, text, self)
        action.setCheckable(True)
        action.triggered.connect(lambda: self.content.set_view_mode(mode))
        self.view_group.addAction(action)
        return action

    def _build_menus(self) -> None:
        file_menu = self.menuBar().addMenu("&File")
        file_menu.addAction(self.act_new_vault)
        file_menu.addAction(self.act_open_vault)
        self.recent_menu = file_menu.addMenu("Recent &vaults")
        file_menu.addSeparator()
        file_menu.addAction(self.act_lock)
        file_menu.addSeparator()
        file_menu.addAction(self.act_exit)

        view_menu = self.menuBar().addMenu("&View")
        for action in (self.act_view_icons, self.act_view_list,
                       self.act_view_details):
            view_menu.addAction(action)
        view_menu.addSeparator()
        view_menu.addAction(self.act_find)
        view_menu.addAction(self.act_refresh)

        item_menu = self.menuBar().addMenu("&Item")
        new_menu = item_menu.addMenu("&New")
        for item_type in REGISTRY:
            action = new_menu.addAction(item_type.display)
            action.triggered.connect(
                lambda _checked=False, t=item_type.type_id: self._new_item(t))
        new_menu.addSeparator()
        new_menu.addAction(self.act_new_folder)
        item_menu.addSeparator()
        item_menu.addAction(self.act_rename)
        item_menu.addAction(self.act_pin)
        item_menu.addAction(self.act_delete)
        item_menu.addSeparator()
        item_menu.addAction(self.act_history)

        tools_menu = self.menuBar().addMenu("&Tools")
        for action in (self.act_overview, self.act_keychain,
                       self.act_sqlcipher):
            tools_menu.addAction(action)
        tools_menu.addSeparator()
        for action in (self.act_meta, self.act_config, self.act_secret):
            tools_menu.addAction(action)

        help_menu = self.menuBar().addMenu("&Help")
        help_menu.addAction(self.act_about)

    # -- ribbon (File / Home / Manage) ---------------------------------------

    def _build_ribbon(self) -> None:
        self.ribbon = QTabWidget()
        self.ribbon.setDocumentMode(True)
        self.ribbon.setSizePolicy(QSizePolicy.Policy.Preferred,
                                  QSizePolicy.Policy.Fixed)
        self.ribbon.tabBar().setExpanding(False)

        self._recent_button = QToolButton()
        self._recent_button.setText("Recent ▾")
        self._recent_button.setPopupMode(
            QToolButton.ToolButtonPopupMode.InstantPopup)
        self._recent_button.setMenu(QMenu(self))

        self.ribbon.addTab(self._ribbon_page([
            self.act_new_vault, self.act_open_vault, self._recent_button,
            self.act_lock, self.act_exit]), "File")
        self.ribbon.addTab(self._ribbon_page([
            self._new_button(), self.act_rename, self.act_pin,
            self.act_delete, self.act_history, self.act_refresh]), "Home")
        self.ribbon.addTab(self._ribbon_page([
            self.act_overview, self.act_keychain, self.act_sqlcipher,
            self.act_meta, self.act_config, self.act_secret,
            self.act_about]), "Manage")

    def _ribbon_page(self, items) -> QToolBar:
        bar = QToolBar()
        bar.setMovable(False)
        bar.setIconSize(QSize(16, 16))
        bar.setToolButtonStyle(Qt.ToolButtonStyle.ToolButtonTextBesideIcon)
        for item in items:
            if isinstance(item, QAction):
                bar.addAction(item)
            else:
                bar.addWidget(item)
        return bar

    def _new_button(self) -> QToolButton:
        button = QToolButton()
        button.setText("New ▾")
        button.setPopupMode(QToolButton.ToolButtonPopupMode.InstantPopup)
        button.setMenu(self._build_new_menu())
        return button

    def _build_new_menu(self) -> QMenu:
        menu = QMenu(self)
        for item_type in REGISTRY:
            action = menu.addAction(item_type.display)
            action.triggered.connect(
                lambda _checked=False, t=item_type.type_id: self._new_item(t))
        menu.addSeparator()
        menu.addAction(self.act_new_folder)
        return menu

    # -- address bar ---------------------------------------------------------

    def _build_address_bar(self) -> None:
        self.address_bar = QToolBar("Address", self)
        self.address_bar.setObjectName("AddressBar")
        self.address_bar.setMovable(False)
        self.address_bar.setIconSize(QSize(16, 16))
        self.address_bar.addAction(self.act_back)

        self.address_edit = QLineEdit()
        self.address_edit.setPlaceholderText("/ or /folder/item.txt or @Plain")
        self.address_edit.setSizePolicy(QSizePolicy.Policy.Expanding,
                                        QSizePolicy.Policy.Preferred)
        self.address_edit.returnPressed.connect(self._address_entered)
        QShortcut(QKeySequence("Escape"), self.address_edit,
                  activated=self._address_escape)
        QShortcut(QKeySequence("Alt+D"), self,
                  activated=self._focus_address)
        self.address_bar.addWidget(self.address_edit)

        self.search_edit = QLineEdit()
        self.search_edit.setPlaceholderText("Filter by name")
        self.search_edit.setClearButtonEnabled(True)
        self.search_edit.setMaximumWidth(190)
        self.search_edit.textChanged.connect(self._apply_filter)
        self.address_bar.addWidget(self.search_edit)

        for action in (self.act_view_icons, self.act_view_list,
                       self.act_view_details):
            self.address_bar.addAction(action)

    # -- layout --------------------------------------------------------------

    def _build_workspace(self) -> None:
        self.unlock_page = UnlockPage()

        self.sidebar = Sidebar()
        self.sidebar.folder_selected.connect(self._show_folder)
        self.sidebar.type_selected.connect(self._show_type)

        self.content = ContentView()
        self.content.open_requested.connect(self._open_item)
        self.content.selection_changed.connect(self._on_content_selection)

        self.splitter = QSplitter(Qt.Orientation.Horizontal)
        self.splitter.addWidget(self.sidebar)
        self.splitter.addWidget(self.content)
        self.splitter.setSizes([220, 720])
        self.splitter.setStretchFactor(1, 1)
        self.splitter.setCollapsible(0, False)

        self.stack = QStackedWidget()
        self.stack.addWidget(self.unlock_page)
        self.stack.addWidget(self.splitter)

        container = QWidget()
        layout = QVBoxLayout(container)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)
        layout.addWidget(self.ribbon)
        layout.addWidget(self.address_bar)
        layout.addWidget(self.stack, 1)
        self.setCentralWidget(container)

    def _build_statusbar(self) -> None:
        self.status_left = QLabel("Ready")
        self.statusBar().addWidget(self.status_left, 1)
        self.status_count = QLabel("")
        self.status_vault = QLabel("")
        self.status_schema = QLabel("")
        self.statusBar().addPermanentWidget(self.status_count)
        self.statusBar().addPermanentWidget(self.status_vault)
        self.statusBar().addPermanentWidget(self.status_schema)

    # -- recents / vault lifecycle ------------------------------------------

    def _refresh_recents(self) -> None:
        entries = self.recent.entries()
        self.unlock_page.set_recents(entries)
        self.recent_menu.clear()
        recent_button_menu = self._recent_button.menu()
        recent_button_menu.clear()
        if not entries:
            for menu in (self.recent_menu, recent_button_menu):
                placeholder = menu.addAction("(no recent vaults)")
                placeholder.setEnabled(False)
            return
        for entry in entries:
            for menu in (self.recent_menu, recent_button_menu):
                action = menu.addAction(entry["name"])
                action.setToolTip(entry["path"])
                action.triggered.connect(
                    lambda _checked=False, p=entry["path"]: self._open_recent(p))

    def _open_recent(self, path: str) -> None:
        if self.vault is not None:
            return
        self.unlock_page.set_path(path, focus=True)

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
        if not self._adopt_vault(vault):
            return
        self.status_left.setText(f"Unlocked {vault.path}")
        logger.info("opened vault '%s' at %s (format v%d)",
                    vault.vault_id, vault.path, vault.format_version)

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
        if not self._adopt_vault(vault):
            return
        self.status_left.setText(f"Created vault at {vault.path}")
        logger.info("created vault '%s' at %s", vault.vault_id, vault.path)

    def _adopt_vault(self, vault: Vault) -> bool:
        try:
            repo = ItemRepository(vault)  # ensures the item schema
        except SqlCipherError as exc:
            logger.exception("schema mismatch for %s", vault.path)
            vault.close()
            QMessageBox.critical(
                self, "Vault",
                "This vault uses an older schema and cannot be opened by this "
                "build. Schema changes recreate vaults (no migrations), so "
                f"create a new vault instead.\n\nDetails: {exc}")
            return False
        except Exception as exc:  # noqa: BLE001 - native errors can be Error
            logger.exception("failed to prepare the workspace for %s",
                             vault.path)
            vault.close()
            QMessageBox.critical(
                self, "Vault",
                f"Could not prepare the vault workspace:\n{exc}")
            return False
        self.vault = vault
        self.items_repo = repo
        self.recent.add(vault.path, vault.name)
        self._refresh_recents()
        self._selected = None
        self._state = ("folder", None)
        self._last_browse = self._state
        self.stack.setCurrentWidget(self.splitter)
        self._set_locked_ui(False)
        self._rebuild()
        self.status_vault.setText(f"  {vault.vault_id}  ")
        self.status_schema.setText(f"  format v{vault.format_version}  ")
        return True

    def lock(self) -> None:
        for window in list(self._editor_windows):
            if not window.close():
                return  # unsaved changes; user cancelled
        if self.vault is not None:
            self.vault.close()
            self.vault = None
        self.items_repo = None
        self._selected = None
        self.sidebar.clear()
        self.content.clear()
        self.address_edit.clear()
        self.search_edit.clear()
        self.status_vault.setText("")
        self.status_schema.setText("")
        self.status_count.setText("")
        self.stack.setCurrentWidget(self.unlock_page)
        self.unlock_page.refresh_detection()
        self.unlock_page.password_edit.setFocus()
        self._set_locked_ui(True)
        self.status_left.setText("Locked")
        logger.info("vault locked")

    def _set_locked_ui(self, locked: bool) -> None:
        for action in (self.act_lock, self.act_refresh, self.act_back,
                       self.act_find, self.act_new_folder,
                       self.act_view_icons, self.act_view_list,
                       self.act_view_details, *self._tool_actions):
            action.setEnabled(not locked)
        for action in (self.act_new_vault, self.act_open_vault):
            action.setEnabled(locked)
        self.ribbon.setVisible(not locked)
        self.address_bar.setVisible(not locked)
        self.address_edit.setEnabled(not locked)
        self.search_edit.setEnabled(not locked)
        self._update_selection_actions()

    def _update_selection_actions(self) -> None:
        enabled = self.vault is not None and self._selected is not None
        for action in (self.act_rename, self.act_delete, self.act_pin,
                       self.act_history):
            action.setEnabled(enabled)

    def _start_dir(self) -> str:
        current = self.unlock_page.current_path()
        if current:
            return os.path.dirname(current)
        return os.path.expanduser("~")

    def _open_from_menu(self) -> None:
        if self.vault is None:
            self.unlock_page.browse()

    # -- navigation ----------------------------------------------------------

    def _navigate(self, state: tuple) -> None:
        self._state = state
        self._last_browse = state
        self._clear_filter()
        self._reload()
        self._sync_sidebar()

    def _show_folder(self, folder_id) -> None:
        self._navigate(("folder", folder_id))

    def _show_type(self, type_id: str) -> None:
        self._navigate(("type", type_id))

    def _go_up(self) -> None:
        if self.items_repo is None or self._state[0] != "folder":
            return
        folder_id = self._state[1]
        if folder_id is None:
            return
        item = self.items_repo.get(folder_id)
        if item is not None:
            self._show_folder(item.parent_id)

    def _apply_filter(self, text: str) -> None:
        self.content.set_filter(text)

    def _clear_filter(self) -> None:
        self.search_edit.blockSignals(True)
        self.search_edit.clear()
        self.search_edit.blockSignals(False)
        self.content.set_filter("")

    def _focus_search(self) -> None:
        self.search_edit.setFocus()
        self.search_edit.selectAll()

    def _reload(self) -> None:
        if self.items_repo is None:
            return
        kind, key = self._state
        if kind == "folder":
            summaries = self.items_repo.children(key)
            self.act_back.setEnabled(key is not None)
        else:
            summaries = self.items_repo.list(key)
            self.act_back.setEnabled(False)

        self.content.set_items(summaries)
        self._update_address()
        count = len(summaries)
        self.status_count.setText(
            f"  {count} item{'s' if count != 1 else ''}  ")
        self._selected = None
        self._update_selection_actions()

    def _update_address(self) -> None:
        if self.vault is None or self.items_repo is None:
            self.address_edit.clear()
            return
        kind, key = self._state
        if kind == "type":
            item_type = get_type(key)
            self.address_edit.setText(
                "@" + (item_type.display if item_type else key))
            return
        if key is None:
            self.address_edit.setText("/")
            return
        trail = [f.title or "(untitled)"
                 for f in self.items_repo.breadcrumb(key)]
        self.address_edit.setText("/" + "/".join(trail))

    def _address_entered(self) -> None:
        self._navigate_to_path(self.address_edit.text())

    def _address_escape(self) -> None:
        self._update_address()
        self.content.setFocus()

    def _focus_address(self) -> None:
        self.address_edit.setFocus()
        self.address_edit.selectAll()

    def _navigate_to_path(self, text: str) -> None:
        if self.items_repo is None:
            return
        text = text.strip()
        if text.startswith("@"):
            wanted = text[1:].strip().lower()
            for item_type in REGISTRY:
                if wanted in (item_type.type_id.lower(),
                              item_type.display.lower()):
                    self._show_type(item_type.type_id)
                    return
            self._address_error(f"Unknown type: {text}")
            return
        segments = [s for s in text.strip("/").split("/") if s]
        parent_id: str | None = None
        for index, segment in enumerate(segments):
            child = self._match_child(parent_id, segment)
            if child is None:
                self._address_error(f"Not found: {segment}")
                return
            if child.type == FOLDER_TYPE:
                parent_id = child.id
                continue
            if index != len(segments) - 1:
                self._address_error(f"Not a folder: {segment}")
                return
            self._show_folder(parent_id)
            self._open_editor(child.id)
            return
        self._show_folder(parent_id)

    def _match_child(self, parent_id: str | None, segment: str):
        wanted = segment.lower()
        for child in self.items_repo.children(parent_id):
            if (child.title or "").lower() == wanted:
                return child
            item_type = get_type(child.type)
            if item_type and item_type.extension and \
                    ((child.title or "") + item_type.extension).lower() == wanted:
                return child
        return None

    def _address_error(self, message: str) -> None:
        self.status_left.setText(message)
        self._update_address()

    def _sync_sidebar(self) -> None:
        kind, key = self._state
        if kind == "folder":
            self.sidebar.select_folder(key)
        elif kind == "type":
            self.sidebar.select_type(key)

    def _rebuild(self) -> None:
        """Rebuild sidebar + content (after structural changes)."""
        if self.items_repo is None:
            return
        self.sidebar.set_repo(self.items_repo)
        self._sync_sidebar()
        self._reload()
        self._apply_filter(self.search_edit.text())

    def _on_content_selection(self, summary) -> None:
        self._selected = summary
        self._update_selection_actions()
        if summary is not None:
            self.status_left.setText(
                f"{summary.title or '(untitled)'} · {summary.type}")

    def _open_item(self, item_id: str) -> None:
        if self.items_repo is None:
            return
        item = self.items_repo.get(item_id)
        if item is None:
            return
        if item.type == FOLDER_TYPE:
            self._show_folder(item_id)
            return
        self._open_editor(item_id)

    def _open_editor(self, item_id: str) -> ItemWindow | None:
        item = self.items_repo.get(item_id)
        if item is None:
            return None
        item_type = get_type(item.type)
        if item_type is None:
            return None
        window = ItemWindow(self.vault, self.items_repo, item_type,
                            item_id=item_id, parent=self)
        self._track_window(window)
        return window

    # -- item actions ---------------------------------------------------------

    def _selected_id(self) -> str | None:
        return self._selected.id if self._selected else None

    def _new_item(self, type_id: str) -> None:
        if self.items_repo is None:
            return
        item_type = get_type(type_id)
        if item_type is None:
            return
        parent_id = self._state[1] if self._state[0] == "folder" else None
        window = ItemWindow(self.vault, self.items_repo, item_type,
                            parent_id=parent_id, parent=self)
        self._track_window(window)

    def _new_folder(self) -> None:
        if self.items_repo is None:
            return
        parent_id = self._state[1] if self._state[0] == "folder" else None
        title, ok = QInputDialog.getText(self, "New Folder", "Name:")
        if not ok or not title.strip():
            return
        self.items_repo.create_folder(title.strip(), parent_id)
        self._rebuild()

    def _rename_selected(self) -> None:
        item_id = self._selected_id()
        if item_id is None or self.items_repo is None:
            return
        item = self.items_repo.get(item_id)
        if item is None:
            return
        title, ok = QInputDialog.getText(self, "Rename", "Title:",
                                         text=item.title)
        if not ok or title == item.title:
            return
        self.items_repo.update(item_id, ItemDraft(
            type=item.type, title=title, content=item.content,
            parent_id=item.parent_id, meta=item.meta, pinned=item.pinned,
            message="renamed"))
        self._rebuild()

    def _delete_selected(self) -> None:
        item_id = self._selected_id()
        if item_id is None or self.items_repo is None:
            return
        item = self.items_repo.get(item_id)
        if item is None:
            return
        choice = QMessageBox.question(
            self, "Delete",
            f"Delete '{item.title or '(untitled)'}'?"
            + ("\nThe folder and everything inside it will be deleted."
               if item.type == FOLDER_TYPE else ""),
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.Cancel)
        if choice != QMessageBox.StandardButton.Yes:
            return
        self.items_repo.soft_delete(item_id)
        self._rebuild()

    def _pin_selected(self) -> None:
        item_id = self._selected_id()
        if item_id is None or self.items_repo is None:
            return
        item = self.items_repo.get(item_id)
        if item is None:
            return
        self.items_repo.set_pinned(item_id, not item.pinned)
        self._rebuild()

    def _history_selected(self) -> None:
        item_id = self._selected_id()
        if item_id is None:
            return
        window = self._open_editor(item_id)
        if window is not None:
            window.show_history()

    # -- editor windows ------------------------------------------------------

    def _track_window(self, window: ItemWindow) -> None:
        self._editor_windows.add(window)
        window.destroyed.connect(
            lambda _=None, w=window: self._editor_windows.discard(w))
        window.changed.connect(self._rebuild)
        window.show()

    def _viewer(self, title: str, widget) -> None:
        if self.vault is None or widget is None:
            return
        window = QMainWindow(self)
        window.setAttribute(Qt.WidgetAttribute.WA_DeleteOnClose, True)
        window.setWindowTitle(title)
        window.setCentralWidget(widget)
        window.resize(680, 480)
        window.show()
        self._viewer_windows.add(window)
        window.destroyed.connect(
            lambda _=None, w=window: self._viewer_windows.discard(w))

    def _show_device_secret(self) -> None:
        if self.vault is None or self.vault.device_secret is None:
            return
        text = (f"# this vault's Device Secret (Keystore analog)\n"
                f"hex:         {self.vault.device_secret.hex()}\n"
                f"fingerprint: {key_fingerprint(self.vault.device_secret)}")
        self._viewer("device_secret", TextViewer(text))

    # -- misc ----------------------------------------------------------------

    def _about(self) -> None:
        QMessageBox.about(
            self, "About CryptOwl DevKit",
            f"<b>CryptOwl DevKit {__version__}</b><br><br>"
            "Developer workbench and reference implementation for CryptOwl "
            "vaults: unified item model (format v3), folders, version "
            "history, and pluggable item types.")

    def closeEvent(self, event) -> None:
        for window in list(self._editor_windows):
            if not window.close():
                event.ignore()
                return
        if self.vault is not None:
            self.vault.close()
            self.vault = None
        super().closeEvent(event)


def _pretty_json(obj: dict) -> str:
    return json.dumps(obj, indent=2, ensure_ascii=False, sort_keys=True)
