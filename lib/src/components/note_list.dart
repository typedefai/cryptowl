import 'package:cryptowl/src/domain/note.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../main.dart';
import '../pages/note_detail_page.dart';
import '../providers/providers.dart';
import 'empty.dart';
import 'list_item.dart';

enum FilterMenu {
  all,
  favorite,
  deleted,
}

class NoteList extends ConsumerWidget {
  const NoteList({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notes = ref.watch(notesProvider);

    return notes.when(
      loading: () => const Center(
        child: CircularProgressIndicator(),
      ),
      error: (e, trace) {
        logger.severe(e, trace);
        return ErrorWidget(e);
      },
      data: (items) => items.isEmpty
          ? Empty()
          : RefreshIndicator(
              child: _buildList(context, items),
              onRefresh: () async {
                ref.invalidate(notesProvider);
              }),
    );
  }

  Widget _buildList(BuildContext context, List<NoteAbstract> items) {
    return ListView.builder(
      itemCount: items.length,
      itemBuilder: (_, index) {
        final item = items[index];
        return ListItem(
          title: item.abstract ?? "",
          content:
              MaterialLocalizations.of(context).formatShortDate(item.updatedAt),
          onTap: () {
            context.goNamed(
              NoteDetailPage.name,
              pathParameters: <String, String>{'id': item.id},
            );
          },
        );
      },
    );
  }
}
