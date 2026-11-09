PRAGMA foreign_keys = ON;
PRAGMA secure_delete = ON;
CREATE TABLE t_wrapped_key (
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
CREATE TABLE t_data_encrypt_key (
    id         CHAR(36) NOT NULL PRIMARY KEY,
    algorithm  TEXT     NOT NULL DEFAULT 'AES-256-GCM',
    ciphertext BLOB     NOT NULL,
    nonce      BLOB     NOT NULL,
    auth_tag   BLOB     NOT NULL,
    created_at INTEGER  NOT NULL,
    updated_at INTEGER  NOT NULL
);
CREATE TABLE t_encrypted_data (
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
CREATE INDEX index_t_encrypted_data_dek_id ON t_encrypted_data (dek_id);
CREATE TABLE t_file (
    id             CHAR(36) NOT NULL PRIMARY KEY,
    dek_id         CHAR(36) REFERENCES t_data_encrypt_key (id),
    classification CHAR(1)  NOT NULL CHECK (classification IN ('C', 'S', 'T')),
    storage_name   TEXT     NOT NULL,
    original_name  TEXT,
    mime_type      TEXT,
    size_bytes     INTEGER  NOT NULL,
    created_at     INTEGER  NOT NULL,
    updated_at     INTEGER  NOT NULL,
    deleted_at     INTEGER
);
CREATE INDEX index_t_file_dek_id ON t_file (dek_id);
CREATE INDEX index_t_file_classification ON t_file (classification);
