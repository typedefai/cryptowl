# CryptOwl Desktop (PyQt)

A desktop build and debugging tool for CryptOwl vaults. It operates **directly on
the same files as the Android app** — `vault.meta`, the SQLCipher `vault.db`
(opened with the VaultKey as a raw key), `config.json`/`config.sig`, and the
CWO1-encrypted `attachments/` + `thumbnails/` — with no import/export step and
no re-encryption.

It reuses the byte-exact primitives from `wechat_sns_export/vaultlib` (vendored
under `cryptowl_desktop/vault/`) and mirrors the Android implementations:

| Primitive | Python mirror | Android |
| --- | --- | --- |
| Key chain (`P`/TMK/SMK/wrap) | `vault/vault.py` | `vault/UnlockService.kt`, `VaultCreator.kt` |
| Argon2id, HKDF, HMAC, AES-GCM | `vault/crypto.py` | `crypto/KdfService.kt`, `AesGcm.kt` |
| Crockford Base32 | `vault/crockford32.py` | `crypto/CrockfordBase32.kt` |
| SQLCipher raw key (`x'<hex>'`) | `vault/sqlcipher_db.py` | `vault/SqlCipher.kt` |
| Schema migrations v1–v4 | `vault/schema.py` | `docs/migrations/*.sql` (assets mirror) |
| CWO1 media format | `vault/cwo1.py` | `vault/Cwo1.kt` |
| Fast master-password change | `Vault.change_master_password` | `vault/MasterPasswordChange.kt` |

## Feature support

| Feature | Desktop |
| --- | --- |
| Create / open / lock a vault (master password) | ✅ |
| Notes (Confidential tier), markdown preview | ✅ |
| Media vault (Confidential tier): import, preview images, export, delete | ✅ |
| Moments timeline (read-only, imported archive) | ✅ |
| Change master password (rewrap + re-sign, O(1)) | ✅ |
| Backup copy (encrypted folder copy) | ✅ |
| Debug panel: meta, key fingerprints, schema, table counts, file stats | ✅ |
| Fingerprint unlock, Secret tier (passwords), Top-Secret tier | ❌ Android Keystore only — metadata is listed, payloads stay locked |
| Moments writes, AI chat, camera | ❌ not implemented on desktop |

## ⚠️ Vault binding (read this first)

A vault is bound to the device secret that created it:

- A vault **created on the desktop** carries a `device_secret` file (mode 600).
  The desktop can always reopen it. Copy it to Android and the app re-binds it
  on first open — after that, the desktop can no longer open that copy.
- A vault **created on Android** (or already re-bound) lives in the Keystore;
  the desktop will refuse it with a clear message.

The intended workflow is therefore: create/manage the vault on the desktop,
copy it to Android for daily use, and keep a desktop-bound copy (or a backup
taken before the first Android open) if you want desktop access.

> Once the Android app implements the passphrase-protected `.vbp` export
> (roadmap F9), that becomes the clean cross-platform path: export on Android,
> import + re-bind on the desktop. Until then, the `device_secret` rule above
> applies.

## Quick start (uv)

Native dependency first — SQLCipher. Pick one:

1. **No Homebrew needed (macOS).** Build a local dylib from the SQLCipher
   amalgamation that already exists in the sibling `cryptowl-ref` repo; the app
   auto-discovers `native/libsqlcipher.dylib`:
   ```bash
   cd desktop
   ./scripts/build_sqlcipher.sh
   ```
   (override the source with `SQLCIPHER_SRC=/path/to/folder-with-sqlite3.c`.)
2. Install SQLCipher: `brew install sqlcipher` (keg-only) or
   `sudo port install sqlcipher`.
3. Point at an existing library: `export LIBSQLCIPHER=/path/to/libsqlcipher.dylib`.

Then, with [uv](https://docs.astral.sh/uv/):

```bash
cd desktop
uv sync                       # creates .venv from pyproject.toml (PyQt6, pycryptodome, argon2-cffi)
uv run run.py                 # or: uv run cryptowl-desktop
uv run run.py /path/to/vault  # prefill a vault folder
uv run run.py --debug         # verbose cwl: logging
```

Tests / checks:

```bash
uv run python -m unittest discover -s tests -v     # crypto/format/schema + vault round-trip
QT_QPA_PLATFORM=offscreen uv run python tests/smoke_ui.py   # builds all tabs headlessly
```

<details>
<summary>Alternative: plain venv + pip</summary>

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python run.py
```
</details>

Logs use the same `cwl:` convention as the Android app (`cwl:vault`,
`cwl:schema`, `cwl:ui`), e.g.:

```
14:02:11 INFO  cwl:cwl.vault: opened vault 'personal' at /…/personal (user_version=4, tables=13)
```


## UI

DBeaver-style shell, styled after **Visual Studio 2010**:

- **Vault Navigator** (left tree): Overview, Metadata (vault.meta / config /
  device_secret), **Schema** (every table with live row counts), **Security**
  (key fingerprints + wrap chain), **Files on disk** (attachments/thumbnails
  with CWO1 header info), **Features** (the friendly Notes/Media/Moments/
  Passwords editors), **Tools** (SQL console, Crypto lab).
- **Editor tabs** (centre, one instance per object): every table opens as
  Data (read-only grid with filter, click-to-sort, paging) | DDL | Stats.
  Right-click a cell to inspect it in the value panel, copy hex/base32, or
  **navigate a foreign key** (e.g. t_password → t_encrypted_data →
  t_data_encrypt_key) — the crypto chain is walkable row by row.
- **Value panel** (bottom dock): selected cell as Text / Hex dump / Base32 /
  JSON / Image, with CWO1 header summary and a decrypt-with-FEK preview +
  export for t_file rows.
- **Properties** (right dock): context info for the active editor.
- **Output** (bottom dock): the `cwl:` logs.
- **SQL console**: arbitrary SQL with results grid, timing, history, snippets;
  non-SELECT statements ask for confirmation (writes are manual and visible).
- **Crypto lab** (dev-only): paste a 32-byte key hex to attempt unwrapping a
  wrapped-key copy or decrypting a `t_encrypted_data` payload locally
  (AAD rules respected; results show fingerprints, raw hex on demand) — the
  only way to analyse S/T-tier payloads on the desktop.

Grids are deliberately **read-only** (writes go through the friendly editors
or the SQL console) so a mis-click cannot corrupt FKs, triggers or ciphertext.

Recently opened vaults are remembered (most-recent-first, up to 10) and shown on
the start page and under **File ▸ Recent vaults** — double-click to reopen with
just the password. Stored as plain JSON (paths + display names, no secrets) at
`~/Library/Application Support/CryptOwl Desktop/recent.json` (Linux:
`$XDG_CONFIG_HOME/cryptowl-desktop/`, Windows: `%APPDATA%`), overridable with
`CRYPTOWL_DESKTOP_HOME`.

## Layout

```
desktop/
├── run.py                      entry point
├── requirements.txt
├── cryptowl_desktop/
│   ├── vault/                  byte-exact vault library (no UI deps)
│   │   ├── crypto.py           vendored from vaultlib (unchanged)
│   │   ├── crockford32.py      vendored from vaultlib (unchanged)
│   │   ├── sqlcipher_db.py     vendored + parameter binding + lazy load
│   │   ├── vault.py            vendored + migrations on open/create + change password
│   │   ├── schema.py           generated from docs/migrations/*.sql
│   │   ├── cwo1.py             CWO1 whole-file + chunked
│   │   ├── repositories.py     notes / media / passwords / moments / counts
│   │   └── debugtools.py       fingerprints + introspection report
│   └── ui/                     PyQt6 (main window, dialogs, tabs)
└── tests/test_core.py
```

## Debugging notes

- **Key material is never displayed raw.** The Debug tab and logs show short
  SHA-256 fingerprints (`fp=… len=…`), matching Android's `CryptoLog`, so a key
  can be correlated across tools without leaking it. Salts/nonces/mac values are
  public and shown truncated.
- **`Debug` tab**: vault.meta summary, wrapped keys, integrity status, schema
  `user_version` vs expected, per-table row counts, per-file sizes/permissions,
  and a “Copy report” button for pasting into an issue.
- **CLI access**: `cryptowl_desktop.vault` is importable without PyQt6
  (`from cryptowl_desktop.vault import Vault`) for scripting/debugging.
