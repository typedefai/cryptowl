import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:cryptowl/src/database/database.dart';
import 'package:cryptowl/src/domain/user.dart';
import 'package:cryptowl/src/providers/credentials.dart';
import 'package:cryptowl/src/repositories/note_repository.dart';
import 'package:cryptowl/src/service/note_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import '../repositories/test_util.dart';
@GenerateMocks([Ref])
import 'note_service_test.mocks.dart';

void main() {
  late SqliteDb database;
  late NoteRepository repository;
  late NoteService service;

  final mockRef = MockRef();

  setUp(() async {
    database = SqliteDb.from(openTestDatabase());
    await database.select(database.categories).get();
    repository = NoteRepository(mockRef);
    service = NoteService(repository);

    provideDummy<Future<Session?>>(Future.value(null));

    when(mockRef.read(asyncLoginProvider.future)).thenAnswer(
        (_) async => Session(database, ProtectedValue.fromString("fake key")));
  });

  tearDown(() async {
    await database.close();
  });

  group("check app initialization", () {
    test('should return false if config file not exists', () async {
      service.createNote("", "");
    });
  });
}
