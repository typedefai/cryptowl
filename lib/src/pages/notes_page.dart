import 'package:cryptowl/src/components/note_list.dart';
import 'package:cryptowl/src/domain/note.dart';
import 'package:cryptowl/src/providers/notes.dart';
import 'package:flutter/material.dart';
import 'package:flutter_adaptive_scaffold/flutter_adaptive_scaffold.dart';
import 'package:go_router/go_router.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:remixicon/remixicon.dart';

import '../components/note_search_list.dart';
import '../localization/app_localizations.dart';
import 'note_create_page.dart';

class NotesPage extends HookConsumerWidget {
  const NotesPage({super.key});

  static const String path = '/notes';
  static const String name = 'Notes';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final sortType = ref.watch(noteSortTypeProvider);

    final isLarge = Breakpoints.mediumAndUp.isActive(context);
    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: Text(AppLocalizations.of(context)!.notes),
        leading: isLarge
            ? null
            : IconButton(
                onPressed: () {},
                icon: Icon(RemixIcons.menu_line),
                tooltip: "Menu",
              ),
        actions: [
          IconButton(
            onPressed: () {
              ref.read(noteSortTypeProvider.notifier).state =
                  _toggleSortType(sortType);
            },
            icon: Icon(_sortType(sortType)),
            tooltip: "Sort",
          ),
          IconButton(
            onPressed: () {},
            icon: Icon(RemixIcons.filter_line),
            tooltip: "Filter",
          ),
          IconButton(
            onPressed: () {
              showSearch(context: context, delegate: DataSearch());
            },
            icon: Icon(RemixIcons.search_line),
            tooltip: "Search",
          ),
          if (isLarge)
            IconButton(
              color: Theme.of(context).colorScheme.primary,
              onPressed: () {
                context.goNamed(
                  NoteCreatePage.name,
                );
              },
              icon: Icon(RemixIcons.add_circle_line),
              tooltip: AppLocalizations.of(context)!.createNote,
            ),
        ],
      ),
      floatingActionButton: Breakpoints.mediumAndUp.isActive(context)
          ? null
          : FloatingActionButton(
              heroTag: "note_add",
              onPressed: () {
                context.goNamed(
                  NoteCreatePage.name,
                );
              },
              child: const Icon(RemixIcons.add_line),
            ),
      body: NoteList(),
    );
  }

  NoteSortType _toggleSortType(NoteSortType type) {
    switch (type) {
      case NoteSortType.dateAsc:
        return NoteSortType.dateDesc;
      case NoteSortType.dateDesc:
        return NoteSortType.dateAsc;
    }
  }

  IconData _sortType(NoteSortType type) {
    switch (type) {
      case NoteSortType.dateAsc:
        return RemixIcons.sort_number_asc;
      case NoteSortType.dateDesc:
        return RemixIcons.sort_number_desc;
    }
  }
}

class DataSearch extends SearchDelegate<String> {
  @override
  List<Widget> buildActions(BuildContext context) {
    return [
      IconButton(
          icon: Icon(Icons.clear),
          onPressed: () {
            query = '';
          })
    ];
  }

  @override
  Widget buildLeading(BuildContext context) {
    //leading icon on the left of the app bar
    return IconButton(
        icon: AnimatedIcon(
          icon: AnimatedIcons.menu_arrow,
          progress: transitionAnimation,
        ),
        onPressed: () {
          close(context, "");
        });
  }

  @override
  Widget buildResults(BuildContext context) {
    print('xxxx' + query);
    return NoteSearchList(query);
  }

  @override
  Widget buildSuggestions(BuildContext context) {
    // show when someone searches for something
    final suggestionList = [];

    return ListView.builder(
      itemBuilder: (context, index) => ListTile(
        onTap: () {
          showResults(context);
        },
        trailing: Icon(Icons.remove_red_eye),
        title: RichText(
          text: TextSpan(
              text: suggestionList[index].titlelist.substring(0, query.length),
              style: TextStyle(color: Colors.red, fontWeight: FontWeight.bold),
              children: [
                TextSpan(
                    text:
                        suggestionList[index].titlelist.substring(query.length),
                    style: TextStyle(color: Colors.grey))
              ]),
        ),
      ),
      itemCount: suggestionList.length,
    );
  }
}
