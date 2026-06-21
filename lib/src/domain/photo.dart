import '../common/classification.dart';
import '../database/database.dart';

class Album {
  String id;
  String name;
  Classification classification;
  String? coverPhotoId;
  DateTime createdAt;
  DateTime updatedAt;

  Album({
    required this.id,
    required this.name,
    required this.classification,
    this.coverPhotoId,
    required this.createdAt,
    required this.updatedAt,
  });

  static Album fromEntity(TAlbumData entity) {
    return Album(
      id: entity.id,
      name: entity.name,
      classification: Classification.parse(entity.classification),
      coverPhotoId: entity.coverPhotoId,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    );
  }
}

class PhotoBasic {
  String id;
  String? albumId;
  String filename;
  String? originalName;
  String? mimeType;
  int? fileSize;
  int? width;
  int? height;
  Classification classification;
  String? thumbnailFilename;
  DateTime createdAt;
  DateTime updatedAt;

  PhotoBasic({
    required this.id,
    this.albumId,
    required this.filename,
    this.originalName,
    this.mimeType,
    this.fileSize,
    this.width,
    this.height,
    required this.classification,
    this.thumbnailFilename,
    required this.createdAt,
    required this.updatedAt,
  });
}

class Photo {
  String id;
  String? albumId;
  String filename;
  String? originalName;
  String? mimeType;
  int? fileSize;
  int? width;
  int? height;
  Classification classification;
  String? thumbnailFilename;
  DateTime createdAt;
  DateTime updatedAt;

  Photo({
    required this.id,
    this.albumId,
    required this.filename,
    this.originalName,
    this.mimeType,
    this.fileSize,
    this.width,
    this.height,
    required this.classification,
    this.thumbnailFilename,
    required this.createdAt,
    required this.updatedAt,
  });
}
