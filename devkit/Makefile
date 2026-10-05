# CryptOwl DevKit — native SQLCipher library.
#
#   make native   build native/libsqlcipher.*, or reuse an existing library
#   make clean    remove build output
#
# Source: official SQLCipher, pinned as a git submodule:
#   git submodule update --init devkit/third_party/sqlcipher
#
# An existing library is reused when present (LIBSQLCIPHER, native/, or a
# system install); set FORCE=1 to build from the submodule regardless.

UNAME_S := $(shell uname -s)
SRC_DIR := third_party/sqlcipher
BUILD_DIR := build/sqlcipher
NATIVE_DIR := native

ifeq ($(UNAME_S),Darwin)
  LIB_NAME := libsqlcipher.dylib
  CC := clang
  SHARED := -dynamiclib
  CRYPTO := -DSQLCIPHER_CRYPTO_CC
  LINK := -framework Security -framework Foundation
else ifeq ($(UNAME_S),Linux)
  LIB_NAME := libsqlcipher.so
  CC := cc
  SHARED := -shared
  CRYPTO := -DSQLCIPHER_CRYPTO_OPENSSL
  LINK := -lcrypto -lpthread -ldl -lm
else
  $(error unsupported platform "$(UNAME_S)": build SQLCipher manually and set LIBSQLCIPHER)
endif

LIB := $(NATIVE_DIR)/$(LIB_NAME)
AMALGAMATION := $(BUILD_DIR)/sqlite3.c

SYSTEM_LIBS := \
  /opt/homebrew/opt/sqlcipher/lib/$(LIB_NAME) \
  /usr/local/opt/sqlcipher/lib/$(LIB_NAME) \
  /opt/local/lib/$(LIB_NAME) \
  /usr/local/lib/$(LIB_NAME)
SYSTEM_LIB := $(firstword $(wildcard $(SYSTEM_LIBS)))

CFLAGS := -O2 -DNDEBUG -fPIC \
  -DSQLITE_HAS_CODEC \
  -DSQLITE_TEMP_STORE=3 \
  -DSQLITE_THREADSAFE=1 \
  -DSQLITE_ENABLE_FTS5 \
  -DSQLITE_OMIT_LOAD_EXTENSION \
  -DSQLITE_OMIT_DEPRECATED \
  -DSQLITE_EXTRA_INIT=sqlcipher_extra_init \
  -DSQLITE_EXTRA_SHUTDOWN=sqlcipher_extra_shutdown \
  -I$(BUILD_DIR) -I$(SRC_DIR)/src

.PHONY: native clean

native:
	@if [ -n "$(LIBSQLCIPHER)" ]; then \
	  if [ ! -f "$(LIBSQLCIPHER)" ]; then \
	    echo "LIBSQLCIPHER=$(LIBSQLCIPHER) not found" >&2; exit 1; \
	  fi; \
	  echo "using existing SQLCipher (LIBSQLCIPHER=$(LIBSQLCIPHER))"; exit 0; \
	fi
	@if [ -z "$(FORCE)" ] && [ -f "$(LIB)" ]; then \
	  echo "using existing $(LIB) (FORCE=1 to rebuild)"; exit 0; \
	fi
	@if [ -z "$(FORCE)" ] && [ -f "$(SYSTEM_LIB)" ]; then \
	  echo "using existing system SQLCipher $(SYSTEM_LIB) (FORCE=1 to build from source)"; exit 0; \
	fi
	@if [ -n "$(FORCE)" ]; then rm -f "$(LIB)"; fi
	@$(MAKE) $(LIB)

$(AMALGAMATION): $(BUILD_DIR)/Makefile
	$(MAKE) -C $(BUILD_DIR) sqlite3.c

$(BUILD_DIR)/Makefile: $(SRC_DIR)/configure
	@mkdir -p $(BUILD_DIR)
	cd $(BUILD_DIR) && $(abspath $(SRC_DIR))/configure

$(LIB): $(AMALGAMATION)
	@mkdir -p $(NATIVE_DIR)
	$(CC) $(SHARED) $(CFLAGS) $(CRYPTO) -o $@ $(AMALGAMATION) $(LINK)
	@echo "built $@"

clean:
	rm -rf $(BUILD_DIR) $(NATIVE_DIR)
