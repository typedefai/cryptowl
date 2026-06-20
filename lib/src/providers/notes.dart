import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:logging/logging.dart';

import '../domain/note.dart';
import 'repositories.dart';

final _logger = Logger("NotesNotifier");

final noteSortTypeProvider =
    StateProvider<NoteSortType>((ref) => NoteSortType.dateDesc);

final notesProvider = FutureProvider<List<NoteAbstract>>((ref) async {
  final repo = ref.watch(noteRepositoryProvider);
  final sortType = ref.watch(noteSortTypeProvider);
  return repo.list(sortType);
});

final noteSearchProvider = FutureProvider.autoDispose
    .family<List<NoteAbstract>, String>((ref, keyword) async {
  final repo = ref.watch(noteRepositoryProvider);
  if (keyword.isEmpty) {
    return [];
  }
  return repo.search(keyword);
});

final noteDetailProvider =
    FutureProvider.autoDispose.family<Note, String>((ref, id) async {
  _logger.fine("Fetching note detail for $id");
  return ref.read(noteRepositoryProvider).findById(id);
});
