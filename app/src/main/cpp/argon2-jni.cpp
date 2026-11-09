#include <jni.h>

#include <cstdint>
#include <cstring>
#include <vector>

extern "C" {
#include "argon2.h"
}

namespace {

void throwIllegalArgument(JNIEnv* env, const char* message) {
    jclass cls = env->FindClass("java/lang/IllegalArgumentException");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    env->ThrowNew(cls, message);
}

std::vector<uint8_t> toBytes(JNIEnv* env, jbyteArray array) {
    jsize length = env->GetArrayLength(array);
    std::vector<uint8_t> bytes(length);
    if (length > 0) {
        env->GetByteArrayRegion(array, 0, length, reinterpret_cast<jbyte*>(bytes.data()));
    }
    return bytes;
}

}  // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_typedefai_cryptowl_crypto_Argon2_nativeHash(JNIEnv* env, jobject /* thiz */, jbyteArray password,
                                    jbyteArray salt, jint mCost, jint tCost, jint parallelism,
                                    jint hashLen, jint type) {
    std::vector<uint8_t> pwd = toBytes(env, password);
    std::vector<uint8_t> s = toBytes(env, salt);
    std::vector<uint8_t> hash(hashLen);

    int err = argon2_hash(tCost, mCost, parallelism, pwd.data(), pwd.size(), s.data(), s.size(),
                          hash.data(), hash.size(), nullptr, 0, static_cast<Argon2_type>(type),
                          ARGON2_VERSION_13);
    if (err != ARGON2_OK) {
        throwIllegalArgument(env, argon2_error_message(err));
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(hash.size());
    if (result != nullptr) {
        env->SetByteArrayRegion(result, 0, hash.size(), reinterpret_cast<jbyte*>(hash.data()));
    }
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_typedefai_cryptowl_crypto_Argon2_nativeHashEncoded(JNIEnv* env, jobject /* thiz */,
                                                 jbyteArray password, jbyteArray salt, jint mCost,
                                                 jint tCost, jint parallelism, jint hashLen,
                                                 jint type) {
    std::vector<uint8_t> pwd = toBytes(env, password);
    std::vector<uint8_t> s = toBytes(env, salt);

    size_t encodedLen = argon2_encodedlen(tCost, mCost, parallelism, s.size(), hashLen,
                                          static_cast<Argon2_type>(type));
    std::vector<char> encoded(encodedLen + 1);

    int err = argon2_hash(tCost, mCost, parallelism, pwd.data(), pwd.size(), s.data(), s.size(),
                          nullptr, hashLen, encoded.data(), encoded.size(),
                          static_cast<Argon2_type>(type), ARGON2_VERSION_13);
    if (err != ARGON2_OK) {
        throwIllegalArgument(env, argon2_error_message(err));
        return nullptr;
    }
    return env->NewStringUTF(encoded.data());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_typedefai_cryptowl_crypto_Argon2_nativeVerify(JNIEnv* env, jobject /* thiz */, jstring encoded,
                                      jbyteArray password, jint type) {
    const char* enc = env->GetStringUTFChars(encoded, nullptr);
    if (enc == nullptr) {
        return JNI_FALSE;
    }
    std::vector<uint8_t> pwd = toBytes(env, password);

    int err = argon2_verify(enc, pwd.data(), pwd.size(), static_cast<Argon2_type>(type));

    env->ReleaseStringUTFChars(encoded, enc);

    if (err == ARGON2_VERIFY_MISMATCH) {
        return JNI_FALSE;
    }
    if (err != ARGON2_OK) {
        throwIllegalArgument(env, argon2_error_message(err));
        return JNI_FALSE;
    }
    return JNI_TRUE;
}
