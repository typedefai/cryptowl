import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/pages/note_edit_page.dart';
import 'package:cryptowl/src/providers/notes.dart';
import 'package:cryptowl/src/providers/providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:remixicon/remixicon.dart';

import '../components/custom_leading.dart';
import '../components/error.dart';
import '../components/fleather_rich_text.dart';
import '../localization/app_localizations.dart';

final _dateFormatter = DateFormat('yyyy-MM-dd HH:mm');

class NoteDetailPage extends ConsumerStatefulWidget {
  const NoteDetailPage({super.key});

  static const String path = '/detail/:id';
  static const String name = 'Note Detail';

  @override
  ConsumerState<NoteDetailPage> createState() => _NodeDetailPageState();
}

class _NodeDetailPageState extends ConsumerState<NoteDetailPage> {
  String _classificationLabel(Classification c) {
    switch (c) {
      case Classification.confidential:
        return 'Confidential';
      case Classification.secret:
        return 'Secret';
      case Classification.topSecret:
        return 'Top Secret';
    }
  }

  IconData _classificationIcon(Classification c) {
    switch (c) {
      case Classification.confidential:
        return Icons.lock_open;
      case Classification.secret:
        return Icons.lock;
      case Classification.topSecret:
        return Icons.shield;
    }
  }

  Color _classificationColor(Classification c) {
    switch (c) {
      case Classification.confidential:
        return Colors.green;
      case Classification.secret:
        return Colors.orange;
      case Classification.topSecret:
        return Colors.red;
    }
  }

  Future<bool?> _confirmDeletion(BuildContext context) async {
    return showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('Delete note'),
          content: const Text('This note will be moved to trash.'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Delete'),
            ),
          ],
        );
      },
    );
  }

  void _showInfo(BuildContext context, dynamic note) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Note Info'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _infoRow('Classification',
                _classificationLabel(note.classification)),
            _infoRow('Created', _dateFormatter.format(note.createdAt)),
            _infoRow('Updated', _dateFormatter.format(note.updatedAt)),
            if (note.contentPlain != null)
              _infoRow(
                  'Word count', note.contentPlain!.split(RegExp(r'\s+')).length.toString()),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  Widget _infoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          SizedBox(
            width: 100,
            child: Text(label,
                style: const TextStyle(fontWeight: FontWeight.bold)),
          ),
          Expanded(child: Text(value)),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final id = GoRouterState.of(context).pathParameters["id"]!;
    final detailFuture = ref.watch(noteDetailProvider(id));
    final noteService = ref.read(noteServiceProvider);

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
            onSelected: (value) async {
              switch (value) {
                case 'info':
                  final note = await ref.read(noteDetailProvider(id).future);
                  if (context.mounted) _showInfo(context, note);
                  break;
                case 'duplicate':
                  final newNote = await noteService.duplicateNote(id);
                  ref.invalidate(notesProvider);
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Note duplicated')),
                    );
                  }
                  break;
                case 'delete':
                  final confirm = await _confirmDeletion(context);
                  if (confirm == true) {
                    await noteService.deleteNote(id);
                    ref.invalidate(notesProvider);
                    if (context.mounted) context.pop();
                  }
                  break;
                default:
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Coming soon: $value')),
                    );
                  }
              }
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
                  value: 'pin',
                  child: Text(AppLocalizations.of(context)!.pin)),
              PopupMenuItem(
                  value: 'star',
                  child: Text(AppLocalizations.of(context)!.favourite)),
              const PopupMenuDivider(),
              PopupMenuItem(
                  value: 'delete',
                  child: Text(AppLocalizations.of(context)!.delete,
                      style: const TextStyle(color: Colors.red))),
            ],
            icon: Icon(RemixIcons.more_line),
            tooltip: AppLocalizations.of(context)!.more,
          ),
        ],
      ),
      body: detailFuture.when(
        data: (note) => Column(
          children: [
            // Classification badge
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                children: [
                  Chip(
                    avatar: Icon(
                      _classificationIcon(note.classification),
                      size: 16,
                      color: _classificationColor(note.classification),
                    ),
                    label: Text(
                      _classificationLabel(note.classification),
                      style: TextStyle(
                        fontSize: 12,
                        color: _classificationColor(note.classification),
                      ),
                    ),
                    visualDensity: VisualDensity.compact,
                    materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  ),
                  const SizedBox(width: 8),
                  if (note.title != null)
                    Expanded(
                      child: Text(
                        note.title!,
                        style: Theme.of(context).textTheme.titleSmall,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                ],
              ),
            ),
            const Divider(height: 1),
            // Note content
            Expanded(
              child: FleatherRichText(content: note.contentJson),
            ),
          ],
        ),
        loading: () => const Center(
          child: CircularProgressIndicator(),
        ),
        error: (e, _) => ErrorInfo(e.toString()),
      ),
    );
  }
}
