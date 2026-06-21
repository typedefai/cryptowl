import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/domain/note.dart';
import 'package:cryptowl/src/repositories/note_repository.dart';

import '../crypto/random_util.dart';

class NoteService {
  final NoteRepository repository;

  NoteService(this.repository);

  Future<Note> createNote(String delta, String plainText,
      {Classification classification = Classification.confidential}) async {
    final now = DateTime.now();
    final item = Note(
      id: RandomUtil.generateUUID(),
      classification: classification,
      contentPlain: plainText,
      contentJson: delta,
      createdAt: now,
      updatedAt: now,
    );
    return repository.insert(item);
  }

  Future<Note> updateNote(String id, String delta, String plainText) async {
    return repository.update(id, contentJson: delta, plainText: plainText);
  }

  Future<void> deleteNote(String id) async {
    return repository.delete(id);
  }

  Future<Note> duplicateNote(String id) async {
    final original = await repository.findById(id);
    final now = DateTime.now();
    final item = Note(
      id: RandomUtil.generateUUID(),
      title: original.title != null ? '${original.title} (copy)' : null,
      classification: original.classification,
      contentPlain: original.contentPlain,
      contentJson: original.contentJson,
      createdAt: now,
      updatedAt: now,
    );
    return repository.insert(item);
  }
}
