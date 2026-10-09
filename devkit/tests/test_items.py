from __future__ import annotations

"""Item model tests: generic CRUD, version history, commit messages."""

import os
import shutil

import pytest

from cryptowl_devkit.vault import ItemDraft, ItemRepository, Vault

FIXTURE = os.path.join(os.path.dirname(__file__), "fixtures", "personal")
PASSWORD = b"devkit-fixture-password"
CREATE_PASSWORD = b"create-test-password"


def _open_vault(tmp_path, fresh: bool = False) -> Vault:
    if fresh:
        return Vault.create(str(tmp_path / "vault"), CREATE_PASSWORD)
    path = tmp_path / "personal"
    shutil.copytree(FIXTURE, path)
    return Vault.open(str(path), PASSWORD)


def test_repository_ensures_item_schema(tmp_path):
    with _open_vault(tmp_path, fresh=True) as vault:
        vault.db.exec_("DROP TABLE t_item_version")
        assert "t_item_version" not in vault.tables
        ItemRepository(vault)
        assert {"t_item", "t_item_version", "t_file"} <= set(vault.tables)


def test_plain_item_crud_round_trip(tmp_path):
    with _open_vault(tmp_path, fresh=True) as vault:
        repo = ItemRepository(vault)
        item_id = repo.create(ItemDraft(type="plain", title="Env prod",
                                        content="A=1\nB=2"))

        item = repo.get(item_id)
        assert item is not None
        assert (item.type, item.title, item.classification) == \
            ("plain", "Env prod", "C")
        assert item.content == "A=1\nB=2"
        assert not item.pinned

        assert [s.id for s in repo.list()] == [item_id]
        assert repo.counts() == {"plain": 1}

        repo.soft_delete(item_id)
        assert repo.get(item_id) is None
        assert repo.list() == []
        assert repo.counts() == {}


def test_versions_written_only_when_content_changes(tmp_path):
    with _open_vault(tmp_path, fresh=True) as vault:
        repo = ItemRepository(vault)
        item_id = repo.create(ItemDraft(type="plain", title="t", content="A",
                                        message="initial"))

        changed = repo.update(item_id, ItemDraft(
            type="plain", title="t", content="A", message="no-op edit"))
        assert changed is False
        assert [v.seq for v in repo.versions(item_id)] == [1]

        changed = repo.update(item_id, ItemDraft(
            type="plain", title="t", content="B", message="change B"))
        assert changed is True
        versions = repo.versions(item_id)
        assert [v.seq for v in versions] == [2, 1]
        assert versions[0].message == "change B"
        assert versions[1].message == "initial"


def test_restore_writes_old_snapshot_as_new_version(tmp_path):
    with _open_vault(tmp_path, fresh=True) as vault:
        repo = ItemRepository(vault)
        item_id = repo.create(ItemDraft(type="plain", title="t", content="old"))
        repo.update(item_id, ItemDraft(type="plain", title="t", content="new"))

        first = [v for v in repo.versions(item_id) if v.seq == 1][0]
        assert repo.restore(first.id) == item_id

        item = repo.get(item_id)
        assert item.content == "old"
        versions = repo.versions(item_id)
        assert versions[0].seq == 3
        assert versions[0].message == "restored from seq 1"


def test_meta_round_trip(tmp_path):
    with _open_vault(tmp_path, fresh=True) as vault:
        repo = ItemRepository(vault)
        item_id = repo.create(ItemDraft(type="plain", title="t", content="x",
                                        meta={"env": "prod", "host": "db1"}))
        assert repo.get(item_id).meta == {"env": "prod", "host": "db1"}


def test_pinned_first_and_type_filter(tmp_path):
    with _open_vault(tmp_path, fresh=True) as vault:
        repo = ItemRepository(vault)
        first = repo.create(ItemDraft(type="plain", title="a"))
        second = repo.create(ItemDraft(type="other", title="b"))
        repo.set_pinned(second, True)
        assert [s.id for s in repo.list()] == [second, first]
        assert [s.id for s in repo.list("plain")] == [first]
        assert repo.counts() == {"plain": 1, "other": 1}


def test_history_retention(tmp_path):
    with _open_vault(tmp_path, fresh=True) as vault:
        repo = ItemRepository(vault, history_keep=2)
        item_id = repo.create(ItemDraft(type="plain", title="t", content="v1"))
        for n in range(2, 6):
            repo.update(item_id, ItemDraft(type="plain", title="t",
                                           content=f"v{n}"))
        assert [v.seq for v in repo.versions(item_id)] == [5, 4]


def test_get_uses_bound_parameters(tmp_path):
    with _open_vault(tmp_path, fresh=True) as vault:
        repo = ItemRepository(vault)
        repo.create(ItemDraft(type="plain", title="safe", content="x"))
        assert repo.get("' OR 1=1 --") is None


# -- folders ----------------------------------------------------------------

def test_folder_tree_and_ordering(tmp_path):
    with _open_vault(tmp_path, fresh=True) as vault:
        repo = ItemRepository(vault)
        root_item = repo.create(ItemDraft(type="plain", title="z item"))
        folder = repo.create_folder("Projects")
        note = repo.create(ItemDraft(type="plain", title="note",
                                     parent_id=folder))

        assert [c.id for c in repo.children()] == [folder, root_item]
        assert [c.id for c in repo.children(folder)] == [note]
        assert repo.children(note) == []
        assert [c.id for c in repo.children(None, item_type="plain")] == \
            [root_item]


def test_move_and_cycle_rejection(tmp_path):
    with _open_vault(tmp_path, fresh=True) as vault:
        repo = ItemRepository(vault)
        a = repo.create_folder("a")
        b = repo.create_folder("b", parent_id=a)
        item = repo.create(ItemDraft(type="plain", title="x"))
        repo.move(item, b)
        assert [c.id for c in repo.children(b)] == [item]
        with pytest.raises(ValueError):
            repo.move(a, b)
        with pytest.raises(ValueError):
            repo.move(a, a)


def test_cascade_soft_delete_batch_and_restore(tmp_path):
    with _open_vault(tmp_path, fresh=True) as vault:
        repo = ItemRepository(vault)
        folder = repo.create_folder("f")
        child = repo.create(ItemDraft(type="plain", title="c",
                                      parent_id=folder))
        other = repo.create(ItemDraft(type="plain", title="o"))
        repo.soft_delete(folder)
        assert repo.get(folder) is None
        assert repo.get(child) is None
        assert repo.get(other) is not None
        assert repo.restore_deleted(folder) == 2
        assert repo.get(folder) is not None
        assert repo.get(child) is not None


def test_breadcrumb(tmp_path):
    with _open_vault(tmp_path, fresh=True) as vault:
        repo = ItemRepository(vault)
        a = repo.create_folder("a")
        b = repo.create_folder("b", parent_id=a)
        item = repo.create(ItemDraft(type="plain", title="x", parent_id=b))
        assert [f.title for f in repo.breadcrumb(item)] == ["a", "b", "x"]
        assert [f.title for f in repo.breadcrumb(b)] == ["a", "b"]


def test_pinned_folders_and_search(tmp_path):
    with _open_vault(tmp_path, fresh=True) as vault:
        repo = ItemRepository(vault)
        folder = repo.create_folder("Secrets")
        repo.set_pinned(folder, True)
        repo.create(ItemDraft(type="plain", title="db url",
                              content="postgres://x"))
        repo.create(ItemDraft(type="plain", title="100% done"))

        assert [f.id for f in repo.pinned_folders()] == [folder]
        assert len(repo.search("db url")) == 1
        assert len(repo.search("postgres")) == 1
        assert len(repo.search("100%")) == 1
        assert repo.search("100_") == []
