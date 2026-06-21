import 'dart:io';
import 'dart:typed_data';

import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/components/photo_grid.dart';
import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:cryptowl/src/providers/photos.dart';
import 'package:cryptowl/src/providers/providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:remixicon/remixicon.dart';

import '../localization/app_localizations.dart';

class PhotosPage extends ConsumerWidget {
  const PhotosPage({super.key});

  static const String path = '/photos';
  static const String name = 'Photos';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: AppBar(
        centerTitle: false,
        title: Text(AppLocalizations.of(context)!.photos),
        actions: [
          IconButton(
            onPressed: () {
              // TODO: search photos
            },
            icon: Icon(RemixIcons.search_line),
          ),
          PopupMenuButton<String>(
            onSelected: (value) {
              // TODO: handle menu
            },
            itemBuilder: (context) => [
              const PopupMenuItem(
                  value: 'albums', child: Text('Albums')),
              const PopupMenuItem(
                  value: 'select', child: Text('Select')),
            ],
            icon: Icon(RemixIcons.more_line),
          ),
        ],
      ),
      body: const PhotoGrid(),
      floatingActionButton: FloatingActionButton(
        heroTag: 'photo_add',
        onPressed: () => _importPhoto(context, ref),
        child: const Icon(Icons.add_a_photo),
      ),
    );
  }

  Future<void> _importPhoto(BuildContext context, WidgetRef ref) async {
    final picker = ImagePicker();
    final picked = await picker.pickImage(source: ImageSource.gallery);
    if (picked == null) return;

    final file = File(picked.path);

    // Show classification selector
    if (!context.mounted) return;
    final classification = await showDialog<Classification>(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text('Encryption Level'),
        children: Classification.values.map((c) {
          String subtitle;
          switch (c) {
            case Classification.confidential:
              subtitle = 'Stored as-is, searchable';
              break;
            case Classification.secret:
              subtitle = 'Encrypted with AES-256-GCM';
              break;
            case Classification.topSecret:
              subtitle = 'Encrypted with XChaCha20-Poly1305';
              break;
          }
          return SimpleDialogOption(
            onPressed: () => Navigator.pop(context, c),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(c.name),
                Text(subtitle,
                    style:
                        const TextStyle(fontSize: 12, color: Colors.grey)),
              ],
            ),
          );
        }).toList(),
      ),
    );

    if (classification == null || !context.mounted) return;

    final session = ref.read(asyncLoginProvider).valueOrNull;
    if (session == null) return;

    final kek = ProtectedValue.fromBinary(
        Uint8List.sublistView(session.symmetricKey.binaryValue, 0, 32));

    try {
      await ref.read(photoServiceProvider).importPhoto(
            file,
            classification,
            kek: kek,
            topSecretKek: session.secondaryKey,
          );
      ref.invalidate(photosProvider(null));
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Photo imported')),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text('Import failed: $e'),
              backgroundColor: Colors.red),
        );
      }
    }
  }
}
