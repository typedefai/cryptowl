import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/database/database.dart';
import 'package:cryptowl/src/repositories/base_repository.dart';
import 'package:drift/drift.dart';

import '../common/note_util.dart';
import '../domain/note.dart';

class NoteRepository extends SqlcipherRepository {
  NoteRepository(super.ref);

  Future<List<NoteAbstract>> list(NoteSortType sortType) async {
    final db = await requireDb();
    final mode =
        sortType == NoteSortType.dateAsc ? OrderingMode.asc : OrderingMode.desc;

    final query = db.selectNotes(
        (u) => OrderBy([OrderingTerm(expression: u.createdAt, mode: mode)]));

    final records = await query.get();
    return records.map((item) {
      return NoteAbstract(
        id: item.id,
        classification: Classification.parse(item.classification),
        title: item.title,
        abstract: item.abstract,
        createdAt: item.createdAt,
        updatedAt: item.updatedAt,
      );
    }).toList();
  }

  Future<List<NoteAbstract>> search(String keyword) async {
    final db = await requireDb();
    final query = db.searchNotes(keyword);

    final records = await query.get();
    return records.map((item) {
      return NoteAbstract(
        id: item.id,
        classification: Classification.parse(item.classification),
        title: item.title,
        abstract: item.abstract,
        createdAt: item.createdAt,
        updatedAt: item.updatedAt,
      );
    }).toList();
  }

  Future<Note> insert(Note item) async {
    final db = await requireDb();
    await db.into(db.tNote).insert(item.toEntity());
    return item;
  }

  Future<Note> update(String id,
      {Classification? classification,
      String? title,
      String? contentJson,
      String? plainText}) async {
    final db = await requireDb();
    // TODO: support history + transaction
    String? checksum =
        contentJson == null ? null : NoteUtil.checksum(contentJson);
    String? abstract =
        plainText == null ? null : NoteUtil.createAbstract(plainText);
    await (db.tNote.update()..where((r) => r.id.equals(id))).write(
      TNoteCompanion(
        classification: Value.absentIfNull(classification?.value),
        title: Value.absentIfNull(title),
        abstract: Value.absentIfNull(abstract),
        contentJson: Value.absentIfNull(contentJson),
        contentPlain: Value.absentIfNull(plainText),
        contentChecksum: Value.absentIfNull(checksum),
        updatedAt: Value(DateTime.now()),
      ),
    );
    return findById(id);
  }

  Future<Note> findById(String id) async {
    final db = await requireDb();
    final item = await (db.tNote.select()..where((tbl) => tbl.id.equals(id)))
        .getSingle();

    return Note.fromEntity(item);
  }
}
