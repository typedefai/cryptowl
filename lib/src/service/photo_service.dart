import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/crypto/aead_crypto.dart';
import 'package:cryptowl/src/crypto/crockford_base32.dart';
import 'package:cryptowl/src/crypto/random_util.dart';
import 'package:cryptowl/src/database/database.dart';
import 'package:cryptowl/src/domain/photo.dart';
import 'package:cryptowl/src/repositories/photo_repository.dart';
import 'package:cryptowl/src/service/kdf_service.dart';
import 'package:logging/logging.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../crypto/protected_value.dart';

class PhotoService {
  final logger = Logger('PhotoService');
  final KdfService kdfService;
  final PhotoRepository photoRepository;
  final aesGcm = CryptographyAesGcm();
  final xchacha20 = CryptographyXChaCha20();

  PhotoService(this.kdfService, this.photoRepository);

  Future<String> _photosDir() async {
    final dir = await getApplicationDocumentsDirectory();
    final photosDir = Directory('${dir.path}/photos');
    if (!await photosDir.exists()) {
      await photosDir.create(recursive: true);
    }
    return photosDir.path;
  }

  Future<Photo> importPhoto(
    File sourceFile,
    Classification classification, {
    String? albumId,
    ProtectedValue? kek,
    ProtectedValue? topSecretKek,
  }) async {
    final photosPath = await _photosDir();
    final photoId = RandomUtil.generateUUID();
    final now = DateTime.now();

    final ext = p.extension(sourceFile.path);
    final filename = '$photoId$ext';
    final thumbFilename = '${photoId}_thumb$ext';

    final sourceBytes = await sourceFile.readAsBytes();

    if (classification == Classification.confidential) {
      // Store as plaintext file
      final destFile = File('$photosPath/$filename');
      await destFile.writeAsBytes(sourceBytes, flush: true);

      // For now, copy as thumbnail (proper resize would need image package)
      final thumbFile = File('$photosPath/$thumbFilename');
      await thumbFile.writeAsBytes(sourceBytes, flush: true);

      final photo = Photo(
        id: photoId,
        albumId: albumId,
        filename: filename,
        originalName: p.basename(sourceFile.path),
        mimeType: _mimeType(ext),
        fileSize: sourceBytes.length,
        classification: classification,
        thumbnailFilename: thumbFilename,
        createdAt: now,
        updatedAt: now,
      );

      await photoRepository.insert(photo);
      return photo;
    }

    // Secret or Top Secret: encrypt the file
    final isTopSecret = classification == Classification.topSecret;
    final crypto = isTopSecret ? xchacha20 : aesGcm;
    final activeKek = isTopSecret ? topSecretKek : kek;
    if (activeKek == null) {
      throw Exception('KEK not available for encryption');
    }

    // Generate DEK
    final dek = ProtectedValue.fromBinary(
        RandomUtil.generateSecureBytes(crypto.type.keySize));
    final dekNonce =
        ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(12));
    final dekId = RandomUtil.generateUUID();

    // Encrypt DEK with KEK
    final encryptedDek = await aesGcm.encrypt(
        dek, activeKek, dekNonce.binaryValue, utf8.encode(dekId));

    // Save DEK to database
    final db = await photoRepository.requireDb();
    await db.into(db.tDataEncryptKey).insert(TDataEncryptKeyData(
          id: dekId,
          data: CrockfordBase32.encode(encryptedDek.cipherData),
          nonce: CrockfordBase32.encode(dekNonce.binaryValue),
          authTag: CrockfordBase32.encode(encryptedDek.authTag),
          createdAt: now,
          updatedAt: now,
        ));

    // Encrypt file content
    final fileNonce =
        RandomUtil.generateSecureBytes(crypto.type.nonceSize);
    final encryptedContent = await crypto.encrypt(
        ProtectedValue.fromBinary(sourceBytes),
        dek,
        fileNonce,
        utf8.encode(photoId));

    // Save encrypted file
    final encFilename = '$photoId.enc';
    final encFile = File('$photosPath/$encFilename');
    // Store: nonce (prefixed) + authTag (prefixed) + ciphertext
    final encData = Uint8List.fromList([
      ...fileNonce,
      ...encryptedContent.authTag,
      ...encryptedContent.cipherData,
    ]);
    await encFile.writeAsBytes(encData, flush: true);

    // Save thumbnail as plaintext (small, for grid display)
    final thumbFile = File('$photosPath/$thumbFilename');
    await thumbFile.writeAsBytes(sourceBytes, flush: true);

    final photo = Photo(
      id: photoId,
      albumId: albumId,
      filename: encFilename,
      originalName: p.basename(sourceFile.path),
      mimeType: _mimeType(ext),
      fileSize: sourceBytes.length,
      classification: classification,
      thumbnailFilename: thumbFilename,
      createdAt: now,
      updatedAt: now,
    );

    await photoRepository.insert(photo);
    return photo;
  }

  Future<File> getPhotoFile(String photoId, ProtectedValue kek,
      {ProtectedValue? topSecretKek}) async {
    final photo = await photoRepository.findById(photoId);
    final photosPath = await _photosDir();

    if (photo.classification == Classification.confidential) {
      return File('$photosPath/${photo.filename}');
    }

    // Decrypt the file
    final encFile = File('$photosPath/${photo.filename}');
    final encBytes = await encFile.readAsBytes();

    final isTopSecret = photo.classification == Classification.topSecret;
    final crypto = isTopSecret ? xchacha20 : aesGcm;
    final nonceSize = crypto.type.nonceSize;

    // Read nonce, authTag, ciphertext
    final fileNonce = encBytes.sublist(0, nonceSize);
    final authTag = encBytes.sublist(nonceSize, nonceSize + 16);
    final ciphertext = encBytes.sublist(nonceSize + 16);

    // Get DEK from database
    final db = await photoRepository.requireDb();
    // For simplicity, we'd need to track the DEK ID per photo
    // This is a simplified version — in production, store encrypted_dek_id in t_photo
    throw UnimplementedError('Full DEK decryption not yet implemented');
  }

  Future<File> getThumbnail(String photoId) async {
    final photo = await photoRepository.findById(photoId);
    final photosPath = await _photosDir();
    return File('$photosPath/${photo.thumbnailFilename}');
  }

  Future<Album> createAlbum(
      String name, Classification classification) async {
    final now = DateTime.now();
    final album = Album(
      id: RandomUtil.generateUUID(),
      name: name,
      classification: classification,
      createdAt: now,
      updatedAt: now,
    );
    await photoRepository.createAlbum(album);
    return album;
  }

  Future<void> deletePhoto(String id) async {
    return photoRepository.delete(id);
  }

  Future<void> deleteAlbum(String id) async {
    return photoRepository.deleteAlbum(id);
  }

  String _mimeType(String ext) {
    switch (ext.toLowerCase()) {
      case '.jpg':
      case '.jpeg':
        return 'image/jpeg';
      case '.png':
        return 'image/png';
      case '.gif':
        return 'image/gif';
      case '.webp':
        return 'image/webp';
      case '.heic':
        return 'image/heic';
      case '.mp4':
        return 'video/mp4';
      case '.mov':
        return 'video/quicktime';
      default:
        return 'application/octet-stream';
    }
  }
}
