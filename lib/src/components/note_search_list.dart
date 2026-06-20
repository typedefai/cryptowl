import 'package:cryptowl/src/domain/note.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:remixicon/remixicon.dart';

import '../../main.dart';
import '../pages/note_detail_page.dart';
import '../providers/providers.dart';
import 'empty.dart';

class NoteSearchList extends ConsumerWidget {
  final String keyword;
  const NoteSearchList(this.keyword, {super.key});

  Widget _buildList(BuildContext context, List<NoteAbstract> items) {
    return ListView.builder(
      itemCount: items.length,
      itemBuilder: (_, index) {
        final item = items[index];
        return ListTile(
          dense: true,
          contentPadding: EdgeInsets.only(left: 10, right: 10),
          leading: Icon(RemixIcons.list_check_2,
              color: Theme.of(context).colorScheme.primary),
          titleAlignment: ListTileTitleAlignment.top,
          title: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: EdgeInsetsGeometry.only(bottom: 5),
                child: Text(
                  item.abstract ?? "",
                  overflow: TextOverflow.ellipsis,
                  maxLines: 2,
                ),
              ),
              Text(
                MaterialLocalizations.of(context)
                    .formatShortDate(item.updatedAt),
                style: Theme.of(context).textTheme.labelSmall,
              )
            ],
          ),
          onTap: () {
            context.goNamed(
              NoteDetailPage.name,
              pathParameters: <String, String>{'id': item.id},
            );
          },
          shape: Border(
            bottom: BorderSide(
              color: const Color.fromARGB(255, 233, 231, 231),
            ),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notes = ref.watch(noteSearchProvider(keyword));

    return notes.when(
      loading: () => const Center(
        child: CircularProgressIndicator(),
      ),
      error: (e, _) {
        logger.severe(e);
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
}
