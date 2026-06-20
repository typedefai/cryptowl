import 'dart:convert';

import 'package:fleather/fleather.dart';
import 'package:flutter/widgets.dart';

class FleatherRichText extends StatelessWidget {
  final String content;

  const FleatherRichText({super.key, required this.content});

  @override
  Widget build(BuildContext context) {
    final document = ParchmentDocument.fromJson(jsonDecode(content));
    final controller = FleatherController(document: document);

    return DefaultTextStyle.merge(
      //style: TextStyle(fontFamilyFallback: ["LXGWWenKaiTC"], fontSize: 14),
      child: FleatherField(
        controller: controller,
        expands: false,
        readOnly: true,
        padding: EdgeInsets.only(
          left: 8,
          right: 8,
          bottom: MediaQuery.of(context).padding.bottom,
        ),
      ),
    );
  }
}
