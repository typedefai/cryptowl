from __future__ import annotations

"""Backend tests for step 1: folder detection + vault open.

The fixture in `tests/fixtures/personal` was created by the cross-verification
oracle (`wechat_sns_export/vaultlib`, password `devkit-fixture-password`), so a
successful open proves byte-compatibility with the reference implementation and
the Android key chain.
"""

import os
import shutil
import sys

import pytest

from cryptowl_devkit.vault import (AndroidBoundError, NotAVaultError, Vault,
                                   WrongPasswordError, inspect_folder,
                                   key_fingerprint)

FIXTURE = os.path.join(os.path.dirname(__file__), "fixtures", "personal")
PASSWORD = b"devkit-fixture-password"


def test_inspect_folder_detects_vault():
    status = inspect_folder(FIXTURE)
    assert status.ready
    assert status.vault_id == "personal"
    assert status.missing == ()
    assert "Ready" in status.summary


def test_open_fixture_vault():
    with Vault.open(FIXTURE, PASSWORD) as vault:
        assert vault.vault_id == "personal"
        assert vault.name == "DevKit Fixture"
        assert "t_wrapped_key" in vault.tables
        assert len(vault.vault_key) == 32
        assert len(vault.file_encryption_key) == 32
        assert vault.vault_key_fingerprint == key_fingerprint(vault.vault_key)


def test_wrong_password_rejected():
    with pytest.raises(WrongPasswordError):
        Vault.open(FIXTURE, b"not-the-password")


def test_android_bound_vault_rejected(tmp_path):
    copied = tmp_path / "personal"
    shutil.copytree(FIXTURE, copied)
    (copied / "device_secret").unlink()
    status = inspect_folder(str(copied))
    assert status.android_bound
    assert not status.ready
    with pytest.raises(AndroidBoundError):
        Vault.open(str(copied), PASSWORD)


def test_not_a_vault(tmp_path):
    status = inspect_folder(str(tmp_path))
    assert not status.is_vault
    with pytest.raises(NotAVaultError):
        Vault.open(str(tmp_path), PASSWORD)


def test_matches_vaultlib_oracle():
    oracle_root = os.path.abspath(
        os.path.join(os.path.dirname(__file__), "..", "..", "..", "wechat_sns_export"))
    if not os.path.isdir(os.path.join(oracle_root, "vaultlib")):
        pytest.skip("sibling vaultlib oracle not available")
    from cryptowl_devkit.vault import sqlcipher
    lib = next((p for p in sqlcipher._candidate_library_paths()
                if p and os.path.exists(p)), None)
    if lib:
        os.environ.setdefault("LIBSQLCIPHER", lib)
    sys.path.insert(0, oracle_root)
    try:
        oracle = pytest.importorskip("vaultlib.vault")
    finally:
        sys.path.remove(oracle_root)

    with Vault.open(FIXTURE, PASSWORD) as devkit_vault, \
            oracle.Vault.open(FIXTURE, PASSWORD) as oracle_vault:
        assert devkit_vault.vault_key == oracle_vault.vault_key
        assert devkit_vault.file_encryption_key == oracle_vault.file_encryption_key
        assert devkit_vault.schema_version == \
            oracle_vault.conn.query_one("PRAGMA user_version")[0]
