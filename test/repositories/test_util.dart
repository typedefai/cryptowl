import 'dart:ffi';
import 'dart:io';

import 'package:convert/convert.dart';
import 'package:cryptowl/src/crypto/crockford_base32.dart';
import 'package:cryptowl/src/crypto/random_util.dart';
import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:native_argon2/native_argon2.dart';
import 'package:sqlite3/open.dart';

void setupArgon2() {
  String testLibPath;

  if (Platform.isMacOS) {
    testLibPath = 'deps/native_argon2/src/build/libnative_argon2.dylib';
  } else if (Platform.isLinux) {
    testLibPath = 'deps/native_argon2/src/build/libnative_argon2.so';
  } else if (Platform.isWindows) {
    testLibPath = 'deps/native_argon2/src/build/libnative_argon2.dll';
  } else {
    throw UnsupportedError(
      'Tests on ${Platform.operatingSystem} not supported',
    );
  }
  Argon2LibraryLoader.instance.configure(libraryPath: testLibPath);
}

DynamicLibrary openTestSqlcipher() {
  String testLibPath;
  if (Platform.isMacOS) {
    testLibPath = 'libnative_sqlcipher.dylib';
  } else if (Platform.isLinux) {
    testLibPath = 'libnative_sqlcipher.so';
  } else if (Platform.isWindows) {
    testLibPath = 'libnative_sqlcipher.dll';
  } else {
    throw UnsupportedError(
      'Tests on ${Platform.operatingSystem} not supported',
    );
  }
  return DynamicLibrary.open("/tmp/sqlcipher-build/$testLibPath");
}

void setupSqlCipher() {
  open.overrideForAll(openTestSqlcipher);
}

// copy assets/dict to this path
final dictPath = "/tmp/dict";

QueryExecutor openMemoryDb() {
  setupSqlCipher();
  return NativeDatabase.memory(setup: (rawDb) {
    print("setting up sqlcipher db...");
    final result = rawDb.select('pragma cipher_version');
    if (result.isEmpty) {
      throw UnsupportedError(
        'This database needs to run with SQLCipher, but that library is '
        'not available!',
      );
    }
    rawDb.execute(
        "pragma key = \"x'2DD29CA851E7B56E4697B0E1F08507293D761A05CE4D1B628663F411A8086D99'\";");
    rawDb.execute('select count(*) from sqlite_master');

    rawDb.execute("select enable_jieba('$dictPath')");
  });
}

QueryExecutor openFileDb(File dbFile) {
  return NativeDatabase.createInBackground(
    dbFile,
    isolateSetup: setupSqlCipher,
    setup: (rawDb) {
      print("setting up sqlcipher db...");
      final result = rawDb.select('pragma cipher_version');
      if (result.isEmpty) {
        throw UnsupportedError(
          'This database needs to run with SQLCipher, but that library is '
          'not available!',
        );
      }
      rawDb.execute(
          "pragma key = \"x'2DD29CA851E7B56E4697B0E1F08507293D761A05CE4D1B628663F411A8086D99'\";");
      rawDb.execute('select count(*) from sqlite_master');

      rawDb.execute("select enable_jieba('$dictPath')");
    },
  );
}

QueryExecutor openRandomFileDb() {
  final tmpFile = File('/tmp/test-${RandomUtil.generateUUID()}.db');
  return openFileDb(tmpFile);
}

QueryExecutor openTestDatabase({String? file, bool? useRandomFile}) {
  return LazyDatabase(() async {
    if (useRandomFile == true) {
      return openRandomFileDb();
    } else if (file == null || file == ":memory:") {
      return openMemoryDb();
    } else {
      return openFileDb(File(file));
    }
  });
}

String crockford32ToHex(String crockford32) {
  return hex.encode(CrockfordBase32.decode(crockford32).binaryValue);
}
