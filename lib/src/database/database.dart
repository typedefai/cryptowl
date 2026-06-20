import 'dart:io';

import 'package:convert/convert.dart';
import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:native_sqlcipher/native_sqlcipher.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqlite3/open.dart';

import '../../main.dart';
import '../common/path_util.dart';
import '../config/version.dart';
import '../crypto/protected_value.dart';

// run `dart run build_runner build` to generate
part 'database.g.dart';

@DriftDatabase(include: {
  'common.drift',
  'encrypted_data.drift',
  'note.drift',
  'password.drift'
})
class SqliteDb extends _$SqliteDb {
  SqliteDb.from(QueryExecutor e) : super(e);
  SqliteDb.open(String file, ProtectedValue key)
      : super(_openDatabase(file, key));
  SqliteDb.openFile(File file, ProtectedValue key)
      : super(_openDatabaseFile(file, key));

  @override
  int get schemaVersion => 1;

  @override
  MigrationStrategy get migration {
    final now = DateTime.now().toIso8601String();
    return MigrationStrategy(
      beforeOpen: (details) async {
        if (details.wasCreated) {
          await into(categories).insert(CategoryEntity(
            id: 1,
            name: "default",
            accessLevel: 1,
            createTime: now,
            lastUpdateTime: now,
          ));
        }
      },
      onCreate: (Migrator m) async {
        await m.createAll();
        // FTS5 virtual table and triggers are not supported by Drift's code generator,
        // so we create them manually.
        await transaction(() async {
          await customStatement('''
            CREATE VIRTUAL TABLE IF NOT EXISTS t_note_idx USING FTS5 (
              title,
              content_plain,
              content="t_note",
              tokenize='jieba'
            )
          ''');
          await customStatement('''
            CREATE TRIGGER IF NOT EXISTS tri_on_note_inserted AFTER INSERT ON t_note WHEN new.deleted_at IS NULL BEGIN
              INSERT INTO t_note_idx (rowid, title, content_plain) VALUES (new.rowid, new.title, new.content_plain);
            END
          ''');
          await customStatement('''
            CREATE TRIGGER IF NOT EXISTS tri_on_note_updated AFTER UPDATE ON t_note WHEN new.deleted_at IS NULL BEGIN
              INSERT INTO t_note_idx(t_note_idx, rowid, title, content_plain) VALUES('delete', old.rowid, old.title, old.content_plain);
              INSERT INTO t_note_idx (rowid, title, content_plain) VALUES (new.rowid, new.title, new.content_plain);
            END
          ''');
          await customStatement('''
            CREATE TRIGGER IF NOT EXISTS tri_on_note_deleted AFTER UPDATE ON t_note WHEN new.deleted_at IS NOT NULL BEGIN
              INSERT INTO t_note_idx(t_note_idx, rowid, title, content_plain) VALUES('delete', old.rowid, old.title, old.content_plain);
            END
          ''');
        });
      },
    );
  }
}

void setupSqlCipher() {
  open.overrideForAll(openSqlcipher);
}

QueryExecutor _openDatabaseFile(File realFile, ProtectedValue key) {
  if (key.binaryValue.length != 32) {
    throw ArgumentError("SQL Cipher must use a key of 32 bytes");
  }
  return LazyDatabase(() async {
    logger.fine("opening database: $realFile");

    final dictPath = await PathUtil.getLocalPath('dict');
    return NativeDatabase.createInBackground(
      realFile,
      logStatements: true,
      isolateSetup: setupSqlCipher,
      setup: (rawDb) {
        print("setting up sqlcipher db...");
        final result = rawDb.select('pragma cipher_version');
        if (result.isEmpty) {
          throw UnsupportedError(
            'This database needs to run with SQLCipher, but that library is '
            'not available!',
          );
        } else {
          final cipherVersion = result.single['cipher_version'];
          if (cipherVersion != SQLCIPHER_VERSION) {
            throw UnsupportedError(
              "This application only supports SQLCipher with version=$SQLCIPHER_VERSION, "
              'however database with version=$cipherVersion detected',
            );
          }
        }

        // fixme: for debug only
        print("pragma key = \"x'${hex.encode(key.binaryValue)}'\";");

        rawDb.execute("pragma key = \"x'${hex.encode(key.binaryValue)}'\";");
        rawDb.execute('select count(*) from sqlite_master');

        rawDb.execute("select enable_jieba('$dictPath')");
      },
    );
  });
}

QueryExecutor _openDatabase(String file, ProtectedValue key) {
  if (key.binaryValue.length != 32) {
    throw ArgumentError("SQL Cipher must use a key of 32 bytes");
  }
  return LazyDatabase(() async {
    final path = await getApplicationDocumentsDirectory();
    final realFile = File(p.join(path.path, file));
    logger.fine("opening database: $realFile");

    final dictPath = await PathUtil.getLocalPath('dict');
    return NativeDatabase.createInBackground(
      realFile,
      logStatements: true,
      isolateSetup: setupSqlCipher,
      setup: (rawDb) {
        print("setting up sqlcipher db...");
        final result = rawDb.select('pragma cipher_version');
        if (result.isEmpty) {
          throw UnsupportedError(
            'This database needs to run with SQLCipher, but that library is '
            'not available!',
          );
        } else {
          final cipherVersion = result.single['cipher_version'];
          if (cipherVersion != SQLCIPHER_VERSION) {
            throw UnsupportedError(
              "This application only supports SQLCipher with version=$SQLCIPHER_VERSION, "
              'however database with version=$cipherVersion detected',
            );
          }
        }

        // fixme: for debug only
        print("pragma key = \"x'${hex.encode(key.binaryValue)}'\";");

        rawDb.execute("pragma key = \"x'${hex.encode(key.binaryValue)}'\";");
        rawDb.execute("PRAGMA foreign_keys;");
        rawDb.execute('select count(*) from sqlite_master');

        rawDb.execute("select enable_jieba('$dictPath')");
      },
    );
  });
}
