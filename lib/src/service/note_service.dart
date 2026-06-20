import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/domain/note.dart';
import 'package:cryptowl/src/repositories/note_repository.dart';

import '../crypto/random_util.dart';

class NoteService {
  final NoteRepository repository;

  NoteService(this.repository);

  Future<Note> createNote(String delta, String plainText) async {
    final now = DateTime.now();
    final item = Note(
      id: RandomUtil.generateUUID(),
      classification: Classification.confidential,
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
}
