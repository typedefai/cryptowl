import 'dart:io';

import 'package:fleather/fleather.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

class FleatherRichEditor extends StatelessWidget {
  final FleatherController controller;
  final FocusNode focusNode;

  const FleatherRichEditor(
      {super.key, required this.controller, required this.focusNode});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        FleatherToolbar.basic(controller: controller),
        Divider(height: 1, thickness: 1, color: Colors.grey.shade200),
        Expanded(
          child: FleatherEditor(
            autofocus: true,
            controller: controller,
            focusNode: focusNode,
            padding: EdgeInsets.only(
              left: 8,
              right: 8,
              bottom: MediaQuery.of(context).padding.bottom,
            ),
            onLaunchUrl: _launchUrl,
            maxContentWidth: 800,
            embedBuilder: _embedBuilder,
            spellCheckConfiguration: SpellCheckConfiguration(
                spellCheckService: DefaultSpellCheckService(),
                misspelledSelectionColor: Colors.red,
                misspelledTextStyle: DefaultTextStyle.of(context).style),
          ),
        ),
      ],
    );
  }

  void _launchUrl(String? url) async {
    if (url == null) return;
    final uri = Uri.parse(url);
    final canLaunch = await canLaunchUrl(uri);
    if (canLaunch) {
      await launchUrl(uri);
    }
  }

  Widget _embedBuilder(BuildContext context, EmbedNode node) {
    if (node.value.type == 'icon') {
      final data = node.value.data;
      // Icons.rocket_launch_outlined
      return Icon(
        IconData(int.parse(data['codePoint']), fontFamily: data['fontFamily']),
        color: Color(int.parse(data['color'])),
        size: 18,
      );
    }

    if (node.value.type == 'image') {
      final sourceType = node.value.data['source_type'];
      ImageProvider? image;
      if (sourceType == 'assets') {
        image = AssetImage(node.value.data['source']);
      } else if (sourceType == 'file') {
        image = FileImage(File(node.value.data['source']));
      } else if (sourceType == 'url') {
        image = NetworkImage(node.value.data['source']);
      }
      if (image != null) {
        return Padding(
          // Caret takes 2 pixels, hence not symmetric padding values.
          padding: const EdgeInsets.only(left: 4, right: 2, top: 2, bottom: 2),
          child: Container(
            width: 300,
            height: 300,
            decoration: BoxDecoration(
              image: DecorationImage(image: image, fit: BoxFit.cover),
            ),
          ),
        );
      }
    }

    return defaultFleatherEmbedBuilder(context, node);
  }
}
