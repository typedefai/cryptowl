import 'package:flutter/material.dart';
import 'package:remixicon/remixicon.dart';

class CustomLeading extends StatelessWidget {
  const CustomLeading({super.key});

  @override
  Widget build(BuildContext context) {
    return IconButton(
      iconSize: 24,
      icon: const Icon(
        RemixIcons.arrow_left_line,
      ),
      onPressed: () => Navigator.of(context).pop(),
    );
  }
}
