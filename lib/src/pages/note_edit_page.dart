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

class NoteEditPage extends HookConsumerWidget {
  const NoteEditPage({super.key});

  static const String path = '/edit/:id';
  static const String name = 'Note Edit';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final id = GoRouterState.of(context).pathParameters["id"]!;
    final detailAsync = ref.watch(noteDetailProvider(id));
    final controller = useState<FleatherController?>(null);
    final focusNode = useFocusNode();

    useEffect(() {
      if (detailAsync is AsyncData && detailAsync.value != null) {
        final jsonContent = detailAsync.value!.contentJson;
        final doc = ParchmentDocument.fromJson(jsonDecode(jsonContent));
        controller.value = FleatherController(document: doc);
      }
      return () => controller.value?.dispose();
    }, [detailAsync]);

    if (controller.value == null || detailAsync.value == null) {
      return const Center(child: CircularProgressIndicator());
    }

    return Scaffold(
        appBar: AppBar(
          centerTitle: false,
          title: Text(AppLocalizations.of(context)!.editNote),
          leading: CustomLeading(),
          actions: [
            IconButton(
              onPressed: () async {
                await ref.read(noteServiceProvider).updateNote(
                    id,
                    jsonEncode(controller.value!.document.toDelta().toJson()),
                    controller.value!.document.toPlainText());
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
          controller: controller.value!,
          focusNode: focusNode,
        ));
  }
}
