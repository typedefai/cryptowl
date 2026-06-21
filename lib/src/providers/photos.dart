import 'package:cryptowl/src/domain/photo.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:logging/logging.dart';

import 'repositories.dart';

final _logger = Logger("PhotoNotifiers");

final photosProvider =
    FutureProvider.family<List<PhotoBasic>, String?>((ref, albumId) async {
  return ref.read(photoRepositoryProvider).listPhotos(albumId: albumId);
});

final albumsProvider = FutureProvider<List<Album>>((ref) async {
  return ref.read(photoRepositoryProvider).listAlbums();
});

final photoDetailProvider =
    FutureProvider.autoDispose.family<Photo, String>((ref, id) async {
  _logger.fine("Fetching photo detail for $id");
  return ref.read(photoRepositoryProvider).findById(id);
});
