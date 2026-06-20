import 'package:cryptowl/src/pages/note_edit_page.dart';
import 'package:cryptowl/src/providers/notes.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:remixicon/remixicon.dart';

import '../components/custom_leading.dart';
import '../components/error.dart';
import '../components/fleather_rich_text.dart';
import '../localization/app_localizations.dart';

class NoteDetailPage extends ConsumerStatefulWidget {
  const NoteDetailPage({super.key});

  static const String path = '/detail/:id';
  static const String name = 'Note Detail';

  @override
  ConsumerState<NoteDetailPage> createState() => _NodeDetailPageState();
}

class _NodeDetailPageState extends ConsumerState<NoteDetailPage> {
  @override
  Widget build(BuildContext context) {
    final id = GoRouterState.of(context).pathParameters["id"]!;
    final detailFuture = ref.watch(noteDetailProvider(id));

    return Scaffold(
      appBar: AppBar(
        centerTitle: false,
        title: Text(AppLocalizations.of(context)!.noteDetail),
        leading: CustomLeading(),
        actions: [
          IconButton(
            onPressed: () {
              context.goNamed(
                NoteEditPage.name,
                pathParameters: <String, String>{'id': id},
              );
            },
            icon: Icon(RemixIcons.edit_line),
            tooltip: AppLocalizations.of(context)!.edit,
          ),
          PopupMenuButton<String>(
            onSelected: (value) {
              // 处理菜单选择
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text('选择了: $value')),
              );
            },
            itemBuilder: (context) => [
              PopupMenuItem(
                  value: 'info',
                  child: Text(AppLocalizations.of(context)!.info)),
              PopupMenuItem(
                  value: 'history',
                  child: Text(AppLocalizations.of(context)!.history)),
              PopupMenuItem(
                  value: 'export',
                  child: Text(AppLocalizations.of(context)!.export)),
              PopupMenuItem(
                  value: 'duplicate',
                  child: Text(AppLocalizations.of(context)!.duplicate)),
              PopupMenuItem(
                  value: 'archive',
                  child: Text(AppLocalizations.of(context)!.archive)),
              PopupMenuItem(
                  value: 'pin', child: Text(AppLocalizations.of(context)!.pin)),
              PopupMenuItem(
                  value: 'star',
                  child: Text(AppLocalizations.of(context)!.favourite)),
              PopupMenuDivider(),
              PopupMenuItem(
                  value: 'delete',
                  child: Text(AppLocalizations.of(context)!.delete)),
            ],
            icon: Icon(RemixIcons.more_line),
            tooltip: AppLocalizations.of(context)!.more,
          ),
        ],
      ),
      body: detailFuture.when(
        data: (note) => FleatherRichText(content: note.contentJson),
        loading: () => const Center(
          child: CircularProgressIndicator(),
        ),
        error: (e, _) => ErrorInfo(e.toString()),
      ),
    );
  }
}
