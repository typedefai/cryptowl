import 'dart:math';

import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:cryptowl/src/crypto/random_util.dart';
import 'package:cryptowl/src/database/database.dart';
import 'package:cryptowl/src/domain/user.dart';
import 'package:cryptowl/src/providers/credentials.dart';
import 'package:cryptowl/src/repositories/password_repository.dart';
import 'package:faker/faker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:native_sqlcipher/native_sqlcipher.dart';
import 'package:sqlite3/open.dart';

@GenerateMocks([Ref])
import 'password_repository_test.mocks.dart';
import 'test_util.dart';

void main() {
  late SqliteDb database;
  late PasswordRepository repository;

  WidgetsFlutterBinding.ensureInitialized();

  final mockRef = MockRef();

  // Future<int> createPasswords() async {
  //   const sql = """
  //   """;
  //   return database.executor.runInsert(sql, []);
  // }

  setUp(() async {
    open.overrideForAll(openSqlcipher);
    database = SqliteDb.from(openTestDatabase());
    await database.select(database.categories).get();
    repository = PasswordRepository(mockRef);

    //await createPasswords();
    provideDummy<Future<Session?>>(Future.value(null));

    when(mockRef.read(asyncLoginProvider.future)).thenAnswer(
        (_) async => Session(database, ProtectedValue.fromString("fake key")));
  });

  tearDown(() async {
    await database.close();
  });

  test('(not a test): generate fake password data', () {
    var faker = Faker();
    var sql =
        'insert into passwords(id, type, category_id, is_favorite, is_deleted, create_time, last_update_time, title, value) \nvalues \n';
    final count = 50;
    for (var i = 0; i < count; i++) {
      final id = RandomUtil.generateUUID();
      final title = faker.company.name().replaceAll('\'', '');
      final value = faker.lorem.sentence().replaceAll('\'', '');
      final time = DateTime.now().toIso8601String();
      final category = Random().nextInt(5) + 1;
      final favorite = Random().nextInt(2);
      final deleted = Random().nextInt(2);
      final type = Random().nextInt(4) + 1;
      sql +=
          "('$id', '$type', $category, $favorite, $deleted, '$time', '$time', '$title', '$value')";
      if (i != count - 1) {
        sql += ",\n";
      } else {
        sql += ";\n";
      }
    }
    print(sql);
  }, skip: true);

  test('should get all undeleted passwords', () async {
    final list = await repository.list();
    expect(list.length, 0);
  });

  group("select by filters", () {});
}
