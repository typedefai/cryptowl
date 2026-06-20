import 'package:cryptowl/src/database/database.dart';
import 'package:flutter_test/flutter_test.dart';

import '../repositories/test_util.dart';

void main() {
  late SqliteDb database;

  setUp(() async {
    database = SqliteDb.from(openTestDatabase());
    await database
        .select(database.categories)
        .get(); // ensure drift is initiallized
  });

  tearDown(() async {
    await database.close();
  });

  test('should get all categories', () async {
    final list = await database.select(database.categories).get();
    expect(list.length, 1);
  });
}
