import 'dart:io';

import 'package:cryptowl/src/common/classification.dart';
import 'package:flutter/material.dart';

/// Displays a photo thumbnail with classification badge overlay.
class PhotoThumbnail extends StatelessWidget {
  final String? thumbnailPath;
  final Classification classification;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final bool selected;

  const PhotoThumbnail({
    super.key,
    this.thumbnailPath,
    required this.classification,
    this.onTap,
    this.onLongPress,
    this.selected = false,
  });

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

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      onLongPress: onLongPress,
      child: Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(8),
          border: selected
              ? Border.all(
                  color: Theme.of(context).colorScheme.primary, width: 3)
              : null,
        ),
        clipBehavior: Clip.antiAlias,
        child: Stack(
          fit: StackFit.expand,
          children: [
            // Thumbnail image
            if (thumbnailPath != null && File(thumbnailPath!).existsSync())
              Image.file(
                File(thumbnailPath!),
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => _placeholder(),
              )
            else
              _placeholder(),

            // Classification badge
            Positioned(
              top: 4,
              right: 4,
              child: Container(
                padding: const EdgeInsets.all(2),
                decoration: BoxDecoration(
                  color: Colors.black54,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Icon(
                  _classificationIcon(classification),
                  size: 14,
                  color: _classificationColor(classification),
                ),
              ),
            ),

            // Selection overlay
            if (selected)
              Container(
                color: Colors.blue.withAlpha(80),
                child: const Center(
                  child: Icon(Icons.check_circle, color: Colors.white, size: 32),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _placeholder() {
    return Container(
      color: Colors.grey[300],
      child: const Center(
        child: Icon(Icons.image, color: Colors.grey, size: 32),
      ),
    );
  }
}
