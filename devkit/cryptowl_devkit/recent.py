from __future__ import annotations

"""Recently opened vault folders (UI preference, never part of the vault).

Stored as a small JSON file in the per-user config dir so it is easy to
inspect/delete; contains only paths and display names, never secrets.

    macOS  : ~/Library/Application Support/CryptOwl DevKit/recent.json
    Linux  : $XDG_CONFIG_HOME/cryptowl-devkit/recent.json
    Windows: %APPDATA%/CryptOwl DevKit/recent.json
"""

import json
import os
import sys
import tempfile
import time


def config_dir() -> str:
    override = os.environ.get("CRYPTOWL_DEVKIT_HOME")
    if override:
        return override
    if sys.platform == "darwin":
        return os.path.join(os.path.expanduser("~"), "Library",
                            "Application Support", "CryptOwl DevKit")
    if os.name == "nt":
        base = os.environ.get("APPDATA") or os.path.expanduser("~")
        return os.path.join(base, "CryptOwl DevKit")
    base = os.environ.get("XDG_CONFIG_HOME") or os.path.join(
        os.path.expanduser("~"), ".config")
    return os.path.join(base, "cryptowl-devkit")


class RecentVaults:
    MAX = 10

    def __init__(self, path: str | None = None):
        self.path = path or os.path.join(config_dir(), "recent.json")
        self._entries = self._load()

    def entries(self) -> list:
        """Most-recent-first list of {path, name, last_opened}."""
        return [dict(entry) for entry in self._entries]

    def latest_path(self) -> str | None:
        return self._entries[0]["path"] if self._entries else None

    def add(self, path: str, name: str | None = None) -> None:
        path = _normalize(path)
        self._entries = [e for e in self._entries if e["path"] != path]
        self._entries.insert(0, {
            "path": path,
            "name": name or os.path.basename(path.rstrip(os.sep)) or path,
            "last_opened": int(time.time() * 1000),
        })
        del self._entries[self.MAX:]
        self._save()

    def remove(self, path: str) -> None:
        path = _normalize(path)
        self._entries = [e for e in self._entries if e["path"] != path]
        self._save()

    def clear(self) -> None:
        self._entries = []
        self._save()

    def _load(self) -> list:
        try:
            with open(self.path, encoding="utf-8") as f:
                data = json.load(f)
        except (OSError, ValueError):
            return []
        entries = data.get("vaults") if isinstance(data, dict) else None
        if not isinstance(entries, list):
            return []
        cleaned = []
        for entry in entries:
            if (isinstance(entry, dict) and isinstance(entry.get("path"), str)
                    and entry["path"]):
                cleaned.append({
                    "path": entry["path"],
                    "name": entry.get("name") or os.path.basename(entry["path"]),
                    "last_opened": int(entry.get("last_opened") or 0),
                })
        return cleaned[:self.MAX]

    def _save(self) -> None:
        directory = os.path.dirname(self.path)
        os.makedirs(directory, exist_ok=True)
        payload = json.dumps({"version": 1, "vaults": self._entries},
                             indent=2, sort_keys=True).encode("utf-8")
        fd, tmp = tempfile.mkstemp(dir=directory, prefix=".recent-")
        try:
            with os.fdopen(fd, "wb") as f:
                f.write(payload)
            os.replace(tmp, self.path)
        finally:
            if os.path.exists(tmp):
                os.remove(tmp)


def _normalize(path: str) -> str:
    return os.path.abspath(os.path.expanduser(path.strip()))
