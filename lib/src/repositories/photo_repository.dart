import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/database/database.dart';
import 'package:drift/drift.dart';
import 'package:logging/logging.dart';

import '../domain/photo.dart';
import 'base_repository.dart';

class PhotoRepository extends SqlcipherRepository {
  final logger = Logger("PhotoRepository");

  PhotoRepository(super.ref);

  Future<List<PhotoBasic>> listPhotos({String? albumId}) async {
    final db = await requireDb();
    final query = db.selectPhotos(
        (u) => OrderBy([OrderingTerm(expression: u.createdAt, mode: OrderingMode.desc)]));

    final records = await query.get();
    return records
        .where((item) => albumId == null || item.albumId == albumId)
        .map((item) => PhotoBasic(
              id: item.id,
              albumId: item.albumId,
              filename: item.filename,
              originalName: item.originalName,
              mimeType: item.mimeType,
              fileSize: item.fileSize,
              width: item.width,
              height: item.height,
              classification: Classification.parse(item.classification),
              thumbnailFilename: item.thumbnailFilename,
              createdAt: item.createdAt,
              updatedAt: item.updatedAt,
            ))
        .toList();
  }

  Future<List<Album>> listAlbums() async {
    final db = await requireDb();
    final query = db.selectAlbums(
        (u) => OrderBy([OrderingTerm(expression: u.createdAt, mode: OrderingMode.desc)]));

    final records = await query.get();
    return records.map((item) => Album(
          id: item.id,
          name: item.name,
          classification: Classification.parse(item.classification),
          coverPhotoId: item.coverPhotoId,
          createdAt: item.createdAt,
          updatedAt: item.updatedAt,
        )).toList();
  }

  Future<Photo> findById(String id) async {
    final db = await requireDb();
    final item = await (db.tPhoto.select()..where((tbl) => tbl.id.equals(id)))
        .getSingle();

    return Photo(
      id: item.id,
      albumId: item.albumId,
      filename: item.filename,
      originalName: item.originalName,
      mimeType: item.mimeType,
      fileSize: item.fileSize,
      width: item.width,
      height: item.height,
      classification: Classification.parse(item.classification),
      thumbnailFilename: item.thumbnailFilename,
      createdAt: item.createdAt,
      updatedAt: item.updatedAt,
    );
  }

  Future<void> insert(Photo photo) async {
    final db = await requireDb();
    await db.into(db.tPhoto).insert(TPhotoData(
          id: photo.id,
          albumId: photo.albumId,
          filename: photo.filename,
          originalName: photo.originalName,
          mimeType: photo.mimeType,
          fileSize: photo.fileSize,
          width: photo.width,
          height: photo.height,
          classification: photo.classification.value,
          thumbnailFilename: photo.thumbnailFilename,
          createdAt: photo.createdAt,
          updatedAt: photo.updatedAt,
        ));
  }

  Future<void> delete(String id) async {
    final db = await requireDb();
    await (db.tPhoto.update()..where((r) => r.id.equals(id))).write(
      TPhotoCompanion(
        deletedAt: Value(DateTime.now()),
      ),
    );
  }

  Future<void> createAlbum(Album album) async {
    final db = await requireDb();
    await db.into(db.tAlbum).insert(TAlbumData(
          id: album.id,
          name: album.name,
          classification: album.classification.value,
          coverPhotoId: album.coverPhotoId,
          createdAt: album.createdAt,
          updatedAt: album.updatedAt,
        ));
  }

  Future<void> deleteAlbum(String id) async {
    final db = await requireDb();
    await (db.tAlbum.update()..where((r) => r.id.equals(id))).write(
      TAlbumCompanion(
        deletedAt: Value(DateTime.now()),
      ),
    );
  }
}
