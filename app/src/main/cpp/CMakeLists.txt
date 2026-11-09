# Native build: Argon2 (JNI) + vendored SQLCipher 5 (OpenSSL provider) + JNI.
#
# SQLCipher 5.0.0-beta.1 is the same amalgamation the desktop client vendors
# (docs/items.md / cryptowl-desktop third_party/sqlcipher), built with the
# same compile-time defines and the OpenSSL crypto provider, so vault.db
# files are byte-compatible (AEAD AES-256-GCM, 8 KiB pages).
cmake_minimum_required(VERSION 3.22.1)

project("cryptowl")

# ---------------------------------------------------------------- Argon2
# Official Argon2 reference implementation (PHC winner, v20190702).
# Vendored under argon2/ (Apache-2.0 / CC0, see argon2/LICENSE).
add_library(argon2 STATIC
        argon2/src/argon2.c
        argon2/src/core.c
        argon2/src/encoding.c
        argon2/src/ref.c
        argon2/src/thread.c
        argon2/src/blake2/blake2b.c)
target_include_directories(argon2 PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/argon2/include)
target_compile_definitions(argon2 PRIVATE _GNU_SOURCE)
set_target_properties(argon2 PROPERTIES C_STANDARD 99 C_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------- OpenSSL
# Pinned source build of the OpenSSL 3.5.x LTS crypto library for the NDK.
# SQLCipher's AEAD/GCM provider is OpenSSL (same as the desktop client).
include(ExternalProject)

set(OPENSSL_VERSION "3.5.8")
set(OPENSSL_SHA256 "a8f84a39918ec6415ce765d9b429d313ba97b8143169c172e734b9514464f5b2")

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(OPENSSL_TARGET android-arm64)
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(OPENSSL_TARGET android-x86_64)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(OPENSSL_TARGET android-arm)
elseif(ANDROID_ABI STREQUAL "x86")
    set(OPENSSL_TARGET android-x86)
else()
    message(FATAL_ERROR "Unsupported Android ABI for OpenSSL: ${ANDROID_ABI}")
endif()

string(REGEX MATCH "[0-9]+$" ANDROID_API_LEVEL "${ANDROID_PLATFORM}")
if(NOT ANDROID_API_LEVEL)
    set(ANDROID_API_LEVEL 35)
endif()
# NDK r29 ships versioned clang wrappers only up to API 35, and OpenSSL's
# android-* Configure targets build the compiler name from this number.
# A library built for API 35 runs on API 36 devices (minSdk 36 app).
if(ANDROID_API_LEVEL GREATER 35)
    set(ANDROID_API_LEVEL 35)
endif()

if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
    set(NDK_HOST_TAG darwin-x86_64)
elseif(CMAKE_HOST_SYSTEM_NAME STREQUAL "Linux")
    set(NDK_HOST_TAG linux-x86_64)
elseif(CMAKE_HOST_SYSTEM_NAME STREQUAL "Windows")
    set(NDK_HOST_TAG windows-x86_64)
else()
    message(FATAL_ERROR "Unsupported build host: ${CMAKE_HOST_SYSTEM_NAME}")
endif()

set(OPENSSL_INSTALL_DIR "${CMAKE_CURRENT_BINARY_DIR}/openssl-${ANDROID_ABI}")
set(OPENSSL_NDK_BIN "${ANDROID_NDK}/toolchains/llvm/prebuilt/${NDK_HOST_TAG}/bin")

ExternalProject_Add(openssl
    URL https://github.com/openssl/openssl/releases/download/openssl-${OPENSSL_VERSION}/openssl-${OPENSSL_VERSION}.tar.gz
    URL_HASH SHA256=${OPENSSL_SHA256}
    CONFIGURE_COMMAND ${CMAKE_COMMAND} -E env
        "ANDROID_NDK_ROOT=${ANDROID_NDK}"
        "PATH=${OPENSSL_NDK_BIN}:$ENV{PATH}"
        <SOURCE_DIR>/Configure ${OPENSSL_TARGET}
        -D__ANDROID_API__=${ANDROID_API_LEVEL}
        no-shared no-tests no-apps no-docs no-legacy
        --prefix=${OPENSSL_INSTALL_DIR}
        --openssldir=${OPENSSL_INSTALL_DIR}/ssl
    BUILD_COMMAND ${CMAKE_COMMAND} -E env
        "ANDROID_NDK_ROOT=${ANDROID_NDK}"
        "PATH=${OPENSSL_NDK_BIN}:$ENV{PATH}"
        make -j4 build_libs
    BUILD_IN_SOURCE TRUE
    BUILD_BYPRODUCTS ${OPENSSL_INSTALL_DIR}/lib/libcrypto.a
    INSTALL_COMMAND make install_dev
    UPDATE_COMMAND ""
    LOG_CONFIGURE TRUE
    LOG_BUILD TRUE
    LOG_INSTALL TRUE
)

# CMake validates IMPORTED include directories at generate time, before
# ExternalProject runs — create them now.
file(MAKE_DIRECTORY ${OPENSSL_INSTALL_DIR}/include ${OPENSSL_INSTALL_DIR}/lib)

add_library(openssl_crypto STATIC IMPORTED)
set_target_properties(openssl_crypto PROPERTIES
    IMPORTED_LOCATION ${OPENSSL_INSTALL_DIR}/lib/libcrypto.a
    INTERFACE_INCLUDE_DIRECTORIES ${OPENSSL_INSTALL_DIR}/include
)
add_dependencies(openssl_crypto openssl)

# ---------------------------------------------------------------- SQLCipher
# Vendored SQLCipher 5.0.0-beta.1 amalgamation (upstream code, do not modify).
# Defines mirror cryptowl-desktop/third_party/CMakeLists.txt exactly.
add_library(sqlcipher STATIC sqlcipher/sqlite3.c)
target_include_directories(sqlcipher PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/sqlcipher)
target_compile_definitions(sqlcipher PRIVATE
    SQLITE_TEMP_STORE=2
    SQLITE_THREADSAFE=1
    SQLITE_USE_URI=1
    SQLITE_DIRECT_OVERFLOW_READ=0
    SQLITE_EXTRA_INIT=sqlcipher_extra_init
    SQLITE_EXTRA_SHUTDOWN=sqlcipher_extra_shutdown
    SQLITE_OMIT_LOAD_EXTENSION=1
    SQLCIPHER_CRYPTO_OPENSSL=1
)
target_link_libraries(sqlcipher PUBLIC openssl_crypto)
set_target_properties(sqlcipher PROPERTIES
    C_STANDARD 99
    C_STANDARD_REQUIRED ON
)

# ---------------------------------------------------------------- JNI library
add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp
        argon2-jni.cpp
        sqlcipher-jni.cpp)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        argon2
        sqlcipher)
