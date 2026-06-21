import 'package:cryptowl/src/components/empty.dart';
import 'package:cryptowl/src/components/photo_thumbnail.dart';
import 'package:cryptowl/src/domain/photo.dart';
import 'package:cryptowl/src/providers/photos.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:path_provider/path_provider.dart';

import '../pages/photo_detail_page.dart';

class PhotoGrid extends ConsumerWidget {
  final String? albumId;

  const PhotoGrid({super.key, this.albumId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final photosAsync = ref.watch(photosProvider(albumId));

    return photosAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (photos) => photos.isEmpty
          ? const Empty(tip: 'No photos yet. Tap + to import.')
          : RefreshIndicator(
              onRefresh: () async {
                ref.invalidate(photosProvider(albumId));
              },
              child: GridView.builder(
                padding: const EdgeInsets.all(4),
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 3,
                  mainAxisSpacing: 4,
                  crossAxisSpacing: 4,
                ),
                itemCount: photos.length,
                itemBuilder: (context, index) {
                  final photo = photos[index];
                  return FutureBuilder<String>(
                    future: _thumbnailPath(photo),
                    builder: (context, snapshot) {
                      return PhotoThumbnail(
                        thumbnailPath: snapshot.data,
                        classification: photo.classification,
                        onTap: () {
                          context.goNamed(
                            PhotoDetailPage.name,
                            pathParameters: {'id': photo.id},
                          );
                        },
                      );
                    },
                  );
                },
              ),
            ),
    );
  }

  Future<String> _thumbnailPath(PhotoBasic photo) async {
    final dir = await getApplicationDocumentsDirectory();
    return '${dir.path}/photos/${photo.thumbnailFilename}';
  }
}
