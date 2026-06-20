import 'dart:convert';

import 'package:cryptowl/src/components/fleather_rich_editor.dart';
import 'package:cryptowl/src/providers/notes.dart';
import 'package:cryptowl/src/providers/repositories.dart';
import 'package:fleather/fleather.dart';
import 'package:flutter/material.dart';
import 'package:flutter_hooks/flutter_hooks.dart';
import 'package:go_router/go_router.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:remixicon/remixicon.dart';

import '../components/custom_leading.dart';
import '../localization/app_localizations.dart';

class NoteCreatePage extends HookConsumerWidget {
  const NoteCreatePage({super.key});

  static const String path = '/create';
  static const String name = 'Note create';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final doc = ParchmentDocument();
    final controller = FleatherController(document: doc);
    final focusNode = useFocusNode();

    return Scaffold(
        appBar: AppBar(
          centerTitle: false,
          title: Text(AppLocalizations.of(context)!.createNote),
          leading: CustomLeading(),
          actions: [
            IconButton(
              onPressed: () async {
                await ref.read(noteServiceProvider).createNote(
                    jsonEncode(controller.document.toDelta().toJson()),
                    controller.document.toPlainText());
                ref.invalidate(notesProvider);
                if (context.mounted) {
                  context.pop();
                }
              },
              icon: Icon(RemixIcons.check_line),
            )
          ],
        ),
        body: FleatherRichEditor(
          controller: controller,
          focusNode: focusNode,
        ));
  }
}
