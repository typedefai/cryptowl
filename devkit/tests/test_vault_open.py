from __future__ import annotations

"""Backend tests for step 1: folder detection + vault open, and initializing
new vaults.

The committed fixture in `tests/fixtures/personal` was created by the
cross-verification oracle (`wechat_sns_export/vaultlib`, password
`devkit-fixture-password`), so a successful open proves byte-compatibility with
the reference implementation and the Android key chain. The tests themselves
stay self-contained (no files outside this repo).
"""

import os
import shutil
from importlib.resources import files as resource_files
from pathlib import Path

import pytest

from cryptowl_devkit.vault import (AndroidBoundError, NotAVaultError, Vault,
                                   VaultError, VaultExistsError,
                                   WrongPasswordError, expected_version,
                                   inspect_folder, key_fingerprint)
from cryptowl_devkit.vault.vault import MIGRATIONS

FIXTURE = os.path.join(os.path.dirname(__file__), "fixtures", "personal")
PASSWORD = b"devkit-fixture-password"
CREATE_PASSWORD = b"create-test-password"


# -- open -------------------------------------------------------------------

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


def test_key_chain_exposes_derived_keys():
    from cryptowl_devkit.vault.crypto import hmac_sha256

    with Vault.open(FIXTURE, PASSWORD) as vault:
        chain = {name: (value, formula) for name, value, formula in vault.key_chain()}

    assert list(chain) == [
        "Device Secret", "P", "TMK", "SMK", "SMK[0:32] (encryption key)",
        "SMK[32:64] (MAC key)", "VaultKey", "FEK"]
    secret = bytes.fromhex(
        (Path(FIXTURE) / "device_secret").read_text(encoding="ascii").strip())
    assert chain["Device Secret"][0] == secret
    assert chain["P"][0] == hmac_sha256(secret, PASSWORD)
    assert len(chain["TMK"][0]) == 32
    assert len(chain["SMK"][0]) == 64
    assert chain["SMK"][0][:32] == chain["SMK[0:32] (encryption key)"][0]
    assert chain["SMK"][0][32:] == chain["SMK[32:64] (MAC key)"][0]
    assert len(chain["VaultKey"][0]) == 32
    assert len(chain["FEK"][0]) == 32
    assert chain["VaultKey"][1] == "SQLCipher raw key of vault.db"


def test_loaded_library_is_sqlcipher():
    """Guard that the loaded library is SQLCipher 4.x, not plain SQLite."""
    with Vault.open(FIXTURE, PASSWORD) as vault:
        row = vault.db.query_one("PRAGMA cipher_version")
    assert row and row[0].startswith("4."), row


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


# -- create -----------------------------------------------------------------

def test_create_and_reopen(tmp_path):
    path = tmp_path / "personal"
    with Vault.create(str(path), CREATE_PASSWORD,
                      vault_id="personal", name="New Vault") as vault:
        assert vault.vault_id == "personal"
        assert vault.name == "New Vault"
        assert set(vault.tables) == {"t_wrapped_key", "t_data_encrypt_key",
                                     "t_encrypted_data", "t_file"}
        assert vault.schema_version == expected_version() == 1

    for name in ("vault.meta", "vault.db", "config.json", "config.sig",
                 "device_secret"):
        assert (path / name).is_file(), name
    assert (path / "device_secret").stat().st_mode & 0o777 == 0o600

    assert inspect_folder(str(path)).ready
    with pytest.raises(WrongPasswordError):
        Vault.open(str(path), b"wrong-password")


def test_create_defaults_id_and_name(tmp_path):
    path = tmp_path / "personal"
    with Vault.create(str(path), CREATE_PASSWORD) as vault:
        assert vault.vault_id == "personal"
        assert vault.name == "personal"


def test_create_refuses_existing_vault(tmp_path):
    path = tmp_path / "personal"
    Vault.create(str(path), CREATE_PASSWORD).close()
    with pytest.raises(VaultExistsError):
        Vault.create(str(path), CREATE_PASSWORD)


def test_create_validates_id_and_password(tmp_path):
    with pytest.raises(VaultError):
        Vault.create(str(tmp_path / "a"), CREATE_PASSWORD, vault_id="../escape")
    with pytest.raises(VaultError):
        Vault.create(str(tmp_path / "b"), b"")


# -- schema cross-checks ----------------------------------------------------

def test_schema_files_match_android_migrations():
    """Vendored scripts must stay byte-identical to the canonical chain."""
    repo = Path(__file__).resolve().parents[2]
    canonical = repo / "docs" / "migrations"
    android_assets = repo / "app" / "src" / "main" / "assets" / "migrations"
    assert (canonical / MIGRATIONS[0][1]).is_file(), \
        "canonical migration chain (docs/migrations) not found"
    for _version, filename in MIGRATIONS:
        ours = resource_files("cryptowl_devkit.vault") \
            .joinpath("migrations", filename).read_bytes()
        for mirror in (canonical / filename, android_assets / filename):
            if mirror.is_file():
                assert ours == mirror.read_bytes(), \
                    f"schema drift vs {mirror.relative_to(repo)}"
