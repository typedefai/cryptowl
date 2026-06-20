import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/domain/note.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group("domain to entity", () {
    test('should return db entity if it is confidential', () async {
      final time = DateTime.now();
      final note = Note(
          id: '1',
          contentJson:
              '[{"insert":"Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry\'s standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.\\n"}]',
          classification: Classification.confidential,
          contentPlain:
              "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.",
          title: "Title",
          createdAt: time,
          updatedAt: time);

      final entity = note.toEntity();
      expect(entity.id, "1");
      expect(entity.title, "Title");
      expect(entity.contentJson,
          '[{"insert":"Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry\'s standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.\\n"}]');
      expect(entity.contentChecksum,
          "ac32142fa02157f706d67e017c0039342d55d47b369739281649824735b16d3f");
      expect(entity.classification, "C");
      expect(entity.abstract,
          "Lorem Ipsum is simply dummy text of the printing and typeset...");
      expect(entity.contentPlain,
          "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.");
      expect(entity.createdAt, time);
      expect(entity.updatedAt, time);
      expect(entity.deletedAt, null);
    });

    test('should encrypt content and do not store plain text if note is secret',
        () async {
      final time = DateTime.now();
      final note = Note(
          id: '1',
          contentJson:
              '[{"insert":"Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry\'s standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.\\n"}]',
          classification: Classification.secret,
          contentPlain:
              "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.",
          title: "Title",
          createdAt: time,
          updatedAt: time);

      final entity = note.toEntity();
      expect(entity.id, "1");
      expect(entity.title, "Title");
      expect(entity.contentJson,
          '[{"insert":"Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry\'s standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.\\n"}]');
      expect(entity.classification, "S");
      expect(entity.abstract, null);
      expect(entity.contentPlain, null);
      expect(entity.createdAt, time);
      expect(entity.updatedAt, time);
      expect(entity.deletedAt, null);
    });
  });
}
