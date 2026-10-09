PRAGMA foreign_keys = ON;
PRAGMA secure_delete = ON;
CREATE TABLE IF NOT EXISTS t_wrapped_key (
    id         TEXT    NOT NULL PRIMARY KEY,
    role       TEXT    NOT NULL CHECK (role IN ('kek', 'ts_kek', 'top_secret_kek')),
    wrapper    TEXT    NOT NULL CHECK (wrapper IN ('smk', 'biokey', 'ts_kek', 'kek')),
    algorithm  TEXT    NOT NULL DEFAULT 'AES-256-GCM',
    ciphertext BLOB    NOT NULL,
    nonce      BLOB    NOT NULL,
    auth_tag   BLOB    NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted_at INTEGER,
    UNIQUE (role, wrapper)
);
CREATE TABLE IF NOT EXISTS t_data_encrypt_key (
    id         CHAR(36) NOT NULL PRIMARY KEY,
    algorithm  TEXT     NOT NULL DEFAULT 'AES-256-GCM',
    ciphertext BLOB     NOT NULL,
    nonce      BLOB     NOT NULL,
    auth_tag   BLOB     NOT NULL,
    created_at INTEGER  NOT NULL,
    updated_at INTEGER  NOT NULL
);
CREATE TABLE IF NOT EXISTS t_encrypted_data (
    id         CHAR(36) NOT NULL PRIMARY KEY,
    dek_id     CHAR(36) NOT NULL REFERENCES t_data_encrypt_key (id),
    algorithm  TEXT     NOT NULL DEFAULT 'AES-256-GCM',
    content    BLOB     NOT NULL,
    nonce      BLOB     NOT NULL,
    auth_tag   BLOB     NOT NULL,
    created_at INTEGER  NOT NULL,
    updated_at INTEGER  NOT NULL,
    deleted_at INTEGER
);
CREATE INDEX IF NOT EXISTS index_t_encrypted_data_dek_id
    ON t_encrypted_data (dek_id);
CREATE TABLE IF NOT EXISTS t_item (
    id                TEXT    NOT NULL PRIMARY KEY,
    type              TEXT    NOT NULL,
    title             TEXT    NOT NULL DEFAULT '',
    classification    CHAR(1) NOT NULL DEFAULT 'C'
                      CHECK (classification IN ('C', 'S', 'T')),
    parent_id         TEXT    REFERENCES t_item (id),
    pinned            INTEGER NOT NULL DEFAULT 0,
    content           TEXT,
    encrypted_data_id TEXT    REFERENCES t_encrypted_data (id),
    meta              TEXT,
    created_at        INTEGER NOT NULL,
    updated_at        INTEGER NOT NULL,
    deleted_at        INTEGER
);
CREATE INDEX IF NOT EXISTS index_t_item_list
    ON t_item (type, deleted_at, pinned DESC, updated_at DESC);
CREATE INDEX IF NOT EXISTS index_t_item_parent
    ON t_item (parent_id, deleted_at);
CREATE TABLE IF NOT EXISTS t_file (
    id            TEXT    NOT NULL PRIMARY KEY,
    item_id       TEXT    NOT NULL REFERENCES t_item (id) ON DELETE CASCADE,
    name          TEXT,
    mime_type     TEXT,
    size_bytes    INTEGER NOT NULL,
    storage       TEXT    NOT NULL DEFAULT 'embedded'
                  CHECK (storage IN ('embedded', 'external')),
    content       BLOB,
    thumbnail     BLOB,
    external_name TEXT,
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL,
    deleted_at    INTEGER
);
CREATE INDEX IF NOT EXISTS index_t_file_item ON t_file (item_id);
CREATE TABLE IF NOT EXISTS t_item_version (
    id                TEXT    NOT NULL PRIMARY KEY,
    item_id           TEXT    NOT NULL REFERENCES t_item (id) ON DELETE CASCADE,
    seq               INTEGER NOT NULL,
    title             TEXT,
    content           TEXT,
    encrypted_data_id TEXT    REFERENCES t_encrypted_data (id),
    meta              TEXT,
    message           TEXT,
    fingerprint       TEXT    NOT NULL,
    created_at        INTEGER NOT NULL,
    UNIQUE (item_id, seq)
);
CREATE INDEX IF NOT EXISTS index_t_item_version_item
    ON t_item_version (item_id, seq DESC);
