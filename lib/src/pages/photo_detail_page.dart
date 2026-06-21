import 'dart:io';

import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/components/error.dart';
import 'package:cryptowl/src/providers/photos.dart';
import 'package:cryptowl/src/providers/providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:path_provider/path_provider.dart';

final _dateFormatter = DateFormat('yyyy-MM-dd HH:mm');

class PhotoDetailPage extends ConsumerWidget {
  const PhotoDetailPage({super.key});

  static const String path = '/photo/:id';
  static const String name = 'Photo Detail';

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
      builder: (context) => AlertDialog(
        title: const Text('Delete photo'),
        content: const Text('This photo will be moved to trash.'),
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
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final id = GoRouterState.of(context).pathParameters['id']!;
    final photoAsync = ref.watch(photoDetailProvider(id));
    final photoService = ref.read(photoServiceProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Photo'),
        actions: [
          IconButton(
            onPressed: () async {
              final confirm = await _confirmDeletion(context);
              if (confirm == true) {
                await photoService.deletePhoto(id);
                ref.invalidate(photosProvider(null));
                if (context.mounted) context.pop();
              }
            },
            icon: const Icon(Icons.delete_outline),
          ),
        ],
      ),
      body: photoAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => ErrorInfo(e.toString()),
        data: (photo) => FutureBuilder<String>(
          future: _filePath(photo.filename),
          builder: (context, snapshot) {
            if (!snapshot.hasData) {
              return const Center(child: CircularProgressIndicator());
            }
            final file = File(snapshot.data!);
            if (!file.existsSync()) {
              return const Center(child: Text('File not found'));
            }
            return Column(
              children: [
                Expanded(
                  child: InteractiveViewer(
                    child: Image.file(file, fit: BoxFit.contain),
                  ),
                ),
                // Info bar
                Container(
                  padding: const EdgeInsets.all(12),
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Chip(
                            label: Text(
                              _classificationLabel(photo.classification),
                              style: TextStyle(
                                fontSize: 12,
                                color:
                                    _classificationColor(photo.classification),
                              ),
                            ),
                            visualDensity: VisualDensity.compact,
                          ),
                          const SizedBox(width: 8),
                          if (photo.originalName != null)
                            Expanded(
                              child: Text(
                                photo.originalName!,
                                overflow: TextOverflow.ellipsis,
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                            ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '${_dateFormatter.format(photo.createdAt)}'
                        '${photo.fileSize != null ? ' • ${_formatSize(photo.fileSize!)}' : ''}'
                        '${photo.width != null && photo.height != null ? ' • ${photo.width}×${photo.height}' : ''}',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  Future<String> _filePath(String filename) async {
    final dir = await getApplicationDocumentsDirectory();
    return '${dir.path}/photos/$filename';
  }

  String _formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
}
