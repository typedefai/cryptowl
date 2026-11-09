from __future__ import annotations

"""C/S/T tier encryption tests (docs/design.md payload routing)."""

import pytest

from cryptowl_devkit.vault import (BadSecondaryPassword, ItemDraft,
                                   ItemRepository, Vault,
                                   SecondaryPasswordNotSet)

CREATE_PASSWORD = b"create-test-password"
TS_PASSWORD = b"top-secret-password"


def _vault(tmp_path) -> Vault:
    return Vault.create(str(tmp_path / "vault"), CREATE_PASSWORD)


def test_c_tier_is_plaintext_inside_sqlcipher(tmp_path):
    with _vault(tmp_path) as vault:
        repo = ItemRepository(vault)
        item_id = repo.create(ItemDraft(type="plain", title="t",
                                        content="hello C"))
        row = repo.db.query_one(
            "SELECT classification, content, encrypted_data_id FROM t_item "
            "WHERE id = ?", (item_id,))
        assert row == ("C", "hello C", None)
        item = repo.get(item_id)
        assert item.content == "hello C" and not item.locked


def test_s_tier_encrypts_with_wrapped_dek(tmp_path):
    with _vault(tmp_path) as vault:
        repo = ItemRepository(vault)
        item_id = repo.create(ItemDraft(type="plain", title="t",
                                        classification="S",
                                        content="secret payload"))
        row = repo.db.query_one(
            "SELECT classification, content, encrypted_data_id FROM t_item "
            "WHERE id = ?", (item_id,))
        assert row[0] == "S" and row[1] is None and row[2]
        # the payload must not appear in the clear anywhere in the DB
        blob = repo.db.query_one(
            "SELECT content FROM t_encrypted_data WHERE id = ?", (row[2],))[0]
        assert b"secret payload" not in bytes(blob or b"")
        # KEK + DEK wrapped rows exist
        assert repo.db.query_one(
            "SELECT COUNT(*) FROM t_wrapped_key "
            "WHERE role = 'kek' AND wrapper = 'smk' AND deleted_at IS NULL")[0] == 1
        assert repo.db.query_one(
            "SELECT COUNT(*) FROM t_data_encrypt_key")[0] == 1

        item = repo.get(item_id)
        assert item.classification == "S"
        assert item.content == "secret payload" and not item.locked


def test_s_edit_reuses_dek_and_keeps_versions_encrypted(tmp_path):
    with _vault(tmp_path) as vault:
        repo = ItemRepository(vault)
        item_id = repo.create(ItemDraft(type="plain", title="t",
                                        classification="S", content="v1"))
        repo.update(item_id, ItemDraft(type="plain", title="t",
                                       classification="S", content="v2"))
        assert repo.db.query_one(
            "SELECT COUNT(*) FROM t_data_encrypt_key")[0] == 1
        versions = repo.db.query(
            "SELECT classification, content, encrypted_data_id "
            "FROM t_item_version WHERE item_id = ? ORDER BY seq", (item_id,))
        assert [v[0] for v in versions] == ["S", "S"]
        assert all(v[1] is None and v[2] for v in versions)
        assert repo.get(item_id).content == "v2"


def test_switch_s_to_c_clears_payload_reference(tmp_path):
    with _vault(tmp_path) as vault:
        repo = ItemRepository(vault)
        item_id = repo.create(ItemDraft(type="plain", title="t",
                                        classification="S", content="s"))
        repo.update(item_id, ItemDraft(type="plain", title="t",
                                       classification="C", content="c"))
        row = repo.db.query_one(
            "SELECT classification, content, encrypted_data_id FROM t_item "
            "WHERE id = ?", (item_id,))
        assert row == ("C", "c", None)
        # old encrypted rows stay for history, but are no longer reachable
        assert repo.get(item_id).content == "c"


def test_t_tier_requires_secondary_password(tmp_path):
    with _vault(tmp_path) as vault:
        repo = ItemRepository(vault)
        with pytest.raises(SecondaryPasswordNotSet):
            repo.create(ItemDraft(type="plain", title="t", classification="T",
                                  content="x"))

        repo.tiers.set_secondary_password(TS_PASSWORD)
        item_id = repo.create(ItemDraft(type="plain", title="t",
                                        classification="T", content="x"),
                              ts_password=TS_PASSWORD)

        locked = repo.get(item_id)
        assert locked.locked and locked.content == ""

        with pytest.raises(BadSecondaryPassword):
            repo.get(item_id, ts_password=b"wrong")

        item = repo.get(item_id, ts_password=TS_PASSWORD)
        assert not item.locked and item.content == "x"


def test_secondary_password_survives_reopen(tmp_path):
    path = tmp_path / "vault"
    with Vault.create(str(path), CREATE_PASSWORD) as vault:
        repo = ItemRepository(vault)
        repo.tiers.set_secondary_password(TS_PASSWORD)
        item_id = repo.create(ItemDraft(type="plain", title="t",
                                        classification="T", content="persist"),
                              ts_password=TS_PASSWORD)

    with Vault.open(str(path), CREATE_PASSWORD) as vault:
        repo = ItemRepository(vault)
        item = repo.get(item_id, ts_password=TS_PASSWORD)
        assert item.content == "persist"


def test_restore_t_version_requires_password(tmp_path):
    with _vault(tmp_path) as vault:
        repo = ItemRepository(vault)
        repo.tiers.set_secondary_password(TS_PASSWORD)
        item_id = repo.create(ItemDraft(type="plain", title="t",
                                        classification="T", content="old"),
                              ts_password=TS_PASSWORD)
        repo.update(item_id, ItemDraft(type="plain", title="t",
                                       classification="T", content="new"),
                    ts_password=TS_PASSWORD)

        first = [v for v in repo.versions(item_id) if v.seq == 1][0]
        assert first.classification == "T"
        with pytest.raises(SecondaryPasswordNotSet):
            repo.restore(first.id)
        repo.restore(first.id, ts_password=TS_PASSWORD)
        assert repo.get(item_id, ts_password=TS_PASSWORD).content == "old"
