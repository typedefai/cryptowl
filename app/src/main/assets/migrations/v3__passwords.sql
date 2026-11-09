PRAGMA foreign_keys = ON;
PRAGMA secure_delete = ON;
CREATE TABLE t_password (
    id                CHAR(36) NOT NULL PRIMARY KEY,
    type              INTEGER  NOT NULL DEFAULT 1,
    classification    CHAR(1)  NOT NULL DEFAULT 'S'
                      CHECK (classification IN ('C', 'S', 'T')),
    title             TEXT     NOT NULL DEFAULT '',
    encrypted_data_id CHAR(36) NOT NULL,
    created_at        INTEGER  NOT NULL,
    updated_at        INTEGER  NOT NULL,
    deleted_at        INTEGER,
    FOREIGN KEY (encrypted_data_id) REFERENCES t_encrypted_data (id)
);
CREATE INDEX index_t_password_deleted_at ON t_password (deleted_at, updated_at DESC);
CREATE INDEX index_t_password_encrypted_data ON t_password (encrypted_data_id);
