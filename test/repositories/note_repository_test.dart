import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:cryptowl/src/database/database.dart';
import 'package:cryptowl/src/domain/note.dart';
import 'package:cryptowl/src/domain/user.dart';
import 'package:cryptowl/src/providers/credentials.dart';
import 'package:cryptowl/src/repositories/note_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

@GenerateMocks([Ref])
import 'note_repository_test.mocks.dart';
import 'test_util.dart';

final contents = [
  '[{"insert":"Hello world!\\n"}]',
  '[{"insert":"Hello "},{"insert":"world!","attributes":{"b":true}},{"insert":"\\n"}]',
  '[{"insert":"Hello 欢迎来到"},{"insert":"中国","attributes":{"b":true}},{"insert":"！\\n"}]',
  '[{"insert":"Hello world!\\n这是我们地球上现在最好的笔记软件！\\n"}]',
  '[{"insert":"Hello world!\\n这是我们地球上"},{"insert":"最好的笔记软件","attributes":{"b":true}},{"insert":"！\\n"}]'
];

final plains = [
  'Hello world!\n',
  'Hello world!\n',
  'Hello 欢迎来到中国！\n',
  'Hello world!\n这是我们地球上现在最好的笔记软件！\n',
  'Hello world!\n这是我们地球上最好的笔记软件！\n'
];

void main() {
  late SqliteDb database;
  late NoteRepository repository;

  final mockRef = MockRef();

  Future<void> createNotes() async {
    final sql = """
    INSERT INTO t_note
    (id, title, content_json, content_checksum, content_plain, abstract, classification, created_at, updated_at, deleted_at)
    VALUES
    ('213fef89-d636-4231-b1f9-d25876ef2430', null, '${contents[0]}', 'checksum1', '${plains[0]}', '${plains[0]}', 'C', '2022-07-25 09:28:42.015Z', '2022-07-25 09:28:42.015Z', null),
    ('213fef89-d636-4231-b1f9-d25876ef2431', null, '${contents[1]}', 'checksum2', '${plains[1]}', '${plains[1]}', 'C', '2022-07-26 09:28:42.015Z', '2022-07-26 09:28:42.015Z', null),
    ('213fef89-d636-4231-b1f9-d25876ef2432', 'Foobar', '${contents[2]}', 'checksum3', '${plains[2]}', '${plains[2]}', 'C', '2022-07-21 09:28:42.015Z', '2022-07-23 09:28:42.015Z', null),
    ('213fef89-d636-4231-b1f9-d25876ef2433', null, '${contents[3]}', 'checksum4', '${plains[3]}', '${plains[3]}', 'C', '2022-07-24 09:28:42.015Z', '2022-07-29 09:28:42.015Z', null),
    ('213fef89-d636-4231-b1f9-d25876ef2434', null, '${contents[4]}', 'checksum5', '${plains[4]}', '${plains[4]}', 'C', '2022-07-22 09:28:42.015Z', '2022-07-25 09:28:42.015Z', null),
    ('59439335-298f-40f0-b4a4-5d7eca4b3220', null, '${contents[4]}', 'checksum5', '${plains[4]}', '${plains[4]}', 'C', '2022-07-22 09:28:42.015Z', '2022-07-25 09:28:42.015Z', '2022-07-25 09:28:42.015Z');
    """;
    await database.executor.runInsert(sql, []);
  }

  setUp(() async {
    database = SqliteDb.from(openTestDatabase());

    await database.select(database.categories).get();
    repository = NoteRepository(mockRef);

    await createNotes();
    provideDummy<Future<Session?>>(Future.value(null));

    when(mockRef.read(asyncLoginProvider.future)).thenAnswer(
        (_) async => Session(database, ProtectedValue.fromString("fake key")));
  });

  tearDown(() async {
    await database.close();
  });

  group("select by filters", () {
    test('should get all undeleted notes', () async {
      final list = await repository.list(NoteSortType.dateDesc);
      expect(list.length, 5);
    });

    test('should sort by created time asc', () async {
      final list = await repository.list(NoteSortType.dateAsc);
      expect(list[0].id, "213fef89-d636-4231-b1f9-d25876ef2432");
      expect(list[0].title, "Foobar");
      expect(list[0].abstract, "Hello 欢迎来到中国！\n");
      expect(list[0].classification, Classification.confidential);
      expect(list[0].createdAt, DateTime.parse('2022-07-21 09:28:42.015Z'));
      expect(list[0].updatedAt, DateTime.parse('2022-07-23 09:28:42.015Z'));

      final list1 = await repository.list(NoteSortType.dateDesc);
      expect(list1[0].id, "213fef89-d636-4231-b1f9-d25876ef2431");
    });

    test('search by chinese keywords', () async {
      final list = await repository.search("中国");
      expect(list.length, 1);
      expect(list[0].id, "213fef89-d636-4231-b1f9-d25876ef2432");

      final list1 = await repository.search("地球");
      expect(list1.length, 2);
      expect(list1[0].id, "213fef89-d636-4231-b1f9-d25876ef2434");
      expect(list1[1].id, "213fef89-d636-4231-b1f9-d25876ef2433");

      final list2 = await repository.search("Hello");
      expect(list2.length, 5);
    });

    test('insert new notes', () async {
      //final list = await repository.insert()
    });
  });
}
