import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/common/note_util.dart';
import 'package:cryptowl/src/database/database.dart';

class NoteAbstract {
  String id;
  String? title;
  String? abstract;
  Classification classification;
  DateTime createdAt;
  DateTime updatedAt;

  NoteAbstract({
    required this.id,
    this.title,
    required this.abstract,
    required this.classification,
    required this.createdAt,
    required this.updatedAt,
  });
}

enum NoteSortType { dateAsc, dateDesc }

class Note {
  String id;
  String? title;
  String contentJson;
  String? contentPlain;
  Classification classification;
  DateTime createdAt;
  DateTime updatedAt;

  Note({
    required this.id,
    this.title,
    this.contentPlain,
    required this.contentJson,
    required this.classification,
    required this.createdAt,
    required this.updatedAt,
  });

  TNoteData toEntity() {
    final storePlain = classification == Classification.confidential;
    return TNoteData(
      id: id,
      classification: classification.value,
      title: title,
      contentJson: contentJson,
      createdAt: createdAt,
      updatedAt: updatedAt,
      contentChecksum: NoteUtil.checksum(contentJson),
      abstract: storePlain ? NoteUtil.createAbstract(contentPlain ?? "") : null,
      contentPlain: storePlain ? contentPlain : null,
    );
  }

  static Note fromEntity(TNoteData entity) {
    return Note(
      id: entity.id,
      classification: Classification.parse(entity.classification),
      title: entity.title,
      contentJson: entity.contentJson,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    );
  }
}
