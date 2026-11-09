#include <jni.h>
#include <sqlite3.h>

#include <cstring>
#include <string>
#include <vector>

extern "C" char *sqlcipher_version(void);

namespace {

struct DbHandle {
    sqlite3 *db = nullptr;
};

struct StmtHandle {
    sqlite3_stmt *stmt = nullptr;
};

void throwIllegalState(JNIEnv *env, const std::string &message)
{
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message.c_str());
        env->DeleteLocalRef(cls);
    }
}

std::string toString(JNIEnv *env, jstring value)
{
    if (value == nullptr)
        return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr)
        return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

DbHandle *handleOf(jlong handle)
{
    return reinterpret_cast<DbHandle *>(static_cast<intptr_t>(handle));
}

StmtHandle *stmtOf(jlong handle)
{
    return reinterpret_cast<StmtHandle *>(static_cast<intptr_t>(handle));
}

std::string databaseError(sqlite3 *db)
{
    return db != nullptr ? sqlite3_errmsg(db) : "database is not open";
}

bool execRaw(sqlite3 *db, const std::string &sql, std::string *error)
{
    char *message = nullptr;
    const int rc = sqlite3_exec(db, sql.c_str(), nullptr, nullptr, &message);
    if (rc != SQLITE_OK) {
        if (error != nullptr)
            *error = message != nullptr ? message : databaseError(db);
        sqlite3_free(message);
        return false;
    }
    return true;
}

jbyteArray toByteArray(JNIEnv *env, const void *data, int size)
{
    if (data == nullptr || size <= 0)
        return env->NewByteArray(0);
    jbyteArray result = env->NewByteArray(size);
    if (result != nullptr)
        env->SetByteArrayRegion(result, 0, size, static_cast<const jbyte *>(data));
    return result;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeVersion(JNIEnv *env, jobject)
{
    const char *version = sqlcipher_version();
    return env->NewStringUTF(version != nullptr ? version : "");
}

/**
 * Opens a database with the SQLCipher raw-key expression (x'<hex>').
 * Returns a handle; throws IllegalStateException on failure.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeOpen(
    JNIEnv *env, jobject, jstring pathValue, jstring rawKeyValue, jboolean create,
    jboolean readOnly)
{
    const std::string path = toString(env, pathValue);
    const std::string rawKey = toString(env, rawKeyValue);
    if (path.empty() || rawKey.empty()) {
        throwIllegalState(env, "database path and key are required");
        return 0;
    }

    int flags = SQLITE_OPEN_READWRITE;
    if (readOnly)
        flags = SQLITE_OPEN_READONLY;
    else if (create)
        flags |= SQLITE_OPEN_CREATE;

    auto *handle = new DbHandle();
    const int rc = sqlite3_open_v2(path.c_str(), &handle->db, flags, nullptr);
    if (rc != SQLITE_OK) {
        const std::string message = databaseError(handle->db);
        sqlite3_close(handle->db);
        delete handle;
        throwIllegalState(env, "could not open the vault database: " + message);
        return 0;
    }
    sqlite3_busy_timeout(handle->db, 5000);

    std::string error;
    const std::string keyPragma = "PRAGMA key = \"" + rawKey + "\";";
    if (!execRaw(handle->db, keyPragma, &error)) {
        sqlite3_close(handle->db);
        delete handle;
        throwIllegalState(env, "could not key the vault database: " + error);
        return 0;
    }
    if (!readOnly) {
        if (!execRaw(handle->db, "PRAGMA foreign_keys = ON;", &error)
            || !execRaw(handle->db, "PRAGMA secure_delete = ON;", &error)) {
            sqlite3_close(handle->db);
            delete handle;
            throwIllegalState(env, "could not configure the vault database: " + error);
            return 0;
        }
    }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeClose(JNIEnv *, jobject, jlong handle)
{
    DbHandle *db = handleOf(handle);
    if (db == nullptr)
        return;
    if (db->db != nullptr)
        sqlite3_close(db->db);
    delete db;
}

extern "C" JNIEXPORT void JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeExec(
    JNIEnv *env, jobject, jlong handle, jstring sqlValue)
{
    DbHandle *db = handleOf(handle);
    if (db == nullptr || db->db == nullptr) {
        throwIllegalState(env, "database is not open");
        return;
    }
    const std::string sql = toString(env, sqlValue);
    std::string error;
    if (!execRaw(db->db, sql, &error))
        throwIllegalState(env, error);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativePrepare(
    JNIEnv *env, jobject, jlong handle, jstring sqlValue)
{
    DbHandle *db = handleOf(handle);
    if (db == nullptr || db->db == nullptr) {
        throwIllegalState(env, "database is not open");
        return 0;
    }
    auto *stmt = new StmtHandle();
    const std::string sql = toString(env, sqlValue);
    if (sqlite3_prepare_v2(db->db, sql.c_str(), -1, &stmt->stmt, nullptr) != SQLITE_OK) {
        const std::string message = databaseError(db->db);
        sqlite3_finalize(stmt->stmt);
        delete stmt;
        throwIllegalState(env, message);
        return 0;
    }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(stmt));
}

extern "C" JNIEXPORT void JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeBindText(
    JNIEnv *env, jobject, jlong stmtHandle, jint index, jstring value)
{
    StmtHandle *stmt = stmtOf(stmtHandle);
    if (stmt == nullptr || stmt->stmt == nullptr)
        return;
    const std::string text = toString(env, value);
    sqlite3_bind_text(stmt->stmt, index, text.data(), static_cast<int>(text.size()),
                      SQLITE_TRANSIENT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeBindBlob(
    JNIEnv *env, jobject, jlong stmtHandle, jint index, jbyteArray value)
{
    StmtHandle *stmt = stmtOf(stmtHandle);
    if (stmt == nullptr || stmt->stmt == nullptr)
        return;
    const jsize size = value != nullptr ? env->GetArrayLength(value) : 0;
    std::vector<uint8_t> bytes(static_cast<size_t>(size));
    if (size > 0)
        env->GetByteArrayRegion(value, 0, size, reinterpret_cast<jbyte *>(bytes.data()));
    const void *data = bytes.empty() ? static_cast<const void *>("")
                                     : static_cast<const void *>(bytes.data());
    sqlite3_bind_blob(stmt->stmt, index, data, size, SQLITE_TRANSIENT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeBindLong(
    JNIEnv *, jobject, jlong stmtHandle, jint index, jlong value)
{
    StmtHandle *stmt = stmtOf(stmtHandle);
    if (stmt != nullptr && stmt->stmt != nullptr)
        sqlite3_bind_int64(stmt->stmt, index, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeBindNull(
    JNIEnv *, jobject, jlong stmtHandle, jint index)
{
    StmtHandle *stmt = stmtOf(stmtHandle);
    if (stmt != nullptr && stmt->stmt != nullptr)
        sqlite3_bind_null(stmt->stmt, index);
}

/** Returns SQLITE_ROW (100), SQLITE_DONE (101), or a negative error code. */
extern "C" JNIEXPORT jint JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeStep(
    JNIEnv *env, jobject, jlong stmtHandle)
{
    StmtHandle *stmt = stmtOf(stmtHandle);
    if (stmt == nullptr || stmt->stmt == nullptr) {
        throwIllegalState(env, "statement is not open");
        return -1;
    }
    const int rc = sqlite3_step(stmt->stmt);
    if (rc != SQLITE_ROW && rc != SQLITE_DONE) {
        throwIllegalState(env, databaseError(sqlite3_db_handle(stmt->stmt)));
    }
    return rc;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeColumnCount(
    JNIEnv *, jobject, jlong stmtHandle)
{
    StmtHandle *stmt = stmtOf(stmtHandle);
    return stmt != nullptr && stmt->stmt != nullptr
        ? sqlite3_column_count(stmt->stmt) : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeColumnType(
    JNIEnv *, jobject, jlong stmtHandle, jint index)
{
    StmtHandle *stmt = stmtOf(stmtHandle);
    return stmt != nullptr && stmt->stmt != nullptr
        ? sqlite3_column_type(stmt->stmt, index) : SQLITE_NULL;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeColumnText(
    JNIEnv *env, jobject, jlong stmtHandle, jint index)
{
    StmtHandle *stmt = stmtOf(stmtHandle);
    if (stmt == nullptr || stmt->stmt == nullptr)
        return env->NewByteArray(0);
    const unsigned char *text = sqlite3_column_text(stmt->stmt, index);
    const int size = sqlite3_column_bytes(stmt->stmt, index);
    return toByteArray(env, text, size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeColumnBlob(
    JNIEnv *env, jobject, jlong stmtHandle, jint index)
{
    StmtHandle *stmt = stmtOf(stmtHandle);
    if (stmt == nullptr || stmt->stmt == nullptr)
        return env->NewByteArray(0);
    const void *blob = sqlite3_column_blob(stmt->stmt, index);
    const int size = sqlite3_column_bytes(stmt->stmt, index);
    return toByteArray(env, blob, size);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeColumnLong(
    JNIEnv *, jobject, jlong stmtHandle, jint index)
{
    StmtHandle *stmt = stmtOf(stmtHandle);
    return stmt != nullptr && stmt->stmt != nullptr
        ? sqlite3_column_int64(stmt->stmt, index) : 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeFinalize(
    JNIEnv *, jobject, jlong stmtHandle)
{
    StmtHandle *stmt = stmtOf(stmtHandle);
    if (stmt == nullptr)
        return;
    if (stmt->stmt != nullptr)
        sqlite3_finalize(stmt->stmt);
    delete stmt;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeChanges(
    JNIEnv *, jobject, jlong handle)
{
    DbHandle *db = handleOf(handle);
    return db != nullptr && db->db != nullptr ? sqlite3_changes(db->db) : 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_typedefai_cryptowl_vault_SqlCipherNative_nativeLastInsertRowId(
    JNIEnv *, jobject, jlong handle)
{
    DbHandle *db = handleOf(handle);
    return db != nullptr && db->db != nullptr ? sqlite3_last_insert_rowid(db->db) : 0;
}
