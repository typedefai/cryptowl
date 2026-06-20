import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:convert/convert.dart';
import 'package:cryptowl/src/common/exceptions.dart';
import 'package:cryptowl/src/crypto/aead_crypto.dart';
import 'package:cryptowl/src/crypto/hmac.dart';
import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:logging/logging.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;

import '../common/path_util.dart';
import '../config/app_config.dart';
import '../crypto/crockford_base32.dart';
import 'version_service.dart';

/// Secure store that uses FlutterSecureStorage (Keychain) in release builds
/// and file-based storage in debug builds.
///
/// macOS Keychain requires a trusted code signature (not ad-hoc), which is
/// only available in properly signed release builds. Debug builds use ad-hoc
/// signing and must fall back to file-based storage.
abstract class SecureStore {
  Future<ProtectedValue?> read(String key);
  Future<void> write(String key, ProtectedValue data);
}

class KeychainSecureStore extends SecureStore {
  final _storage = FlutterSecureStorage();
  final Logger _logger;

  KeychainSecureStore(this._logger);

  @override
  Future<ProtectedValue?> read(String key) async {
    _logger.fine("readSecureStore: key='$key' (keychain)");
    final value = await _storage.read(key: key);
    _logger.fine("readSecureStore: key='$key', found=${value != null}");
    if (value != null) {
      return CrockfordBase32.decode(value);
    }
    return null;
  }

  @override
  Future<void> write(String key, ProtectedValue data) async {
    final encoded = CrockfordBase32.encodeProtected(data);
    _logger.fine("saveSecureStore: key='$key' (keychain), encodedLength=${encoded.length}");
    await _storage.write(key: key, value: encoded);
    _logger.fine("saveSecureStore: key='$key', SUCCESS");
  }
}

class FileSecureStore extends SecureStore {
  final Logger _logger;

  FileSecureStore(this._logger);

  Future<String> _filePath(String key) async {
    final safeKey = key.replaceAll(RegExp(r'[^a-zA-Z0-9_\-]'), '_');
    return PathUtil.getLocalPath('.secrets/$safeKey');
  }

  @override
  Future<ProtectedValue?> read(String key) async {
    _logger.fine("readSecureStore: key='$key' (file)");
    try {
      final path = await _filePath(key);
      final file = File(path);
      if (!await file.exists()) {
        _logger.fine("readSecureStore: key='$key', not found");
        return null;
      }
      final value = await file.readAsString();
      _logger.fine("readSecureStore: key='$key', found=true");
      return CrockfordBase32.decode(value.trim());
    } catch (e) {
      _logger.warning("readSecureStore: key='$key', error: $e");
      return null;
    }
  }

  @override
  Future<void> write(String key, ProtectedValue data) async {
    final encoded = CrockfordBase32.encodeProtected(data);
    _logger.fine("saveSecureStore: key='$key' (file), encodedLength=${encoded.length}");
    try {
      final path = await _filePath(key);
      final file = File(path);
      await file.parent.create(recursive: true);
      await file.writeAsString(encoded, flush: true);
      _logger.fine("saveSecureStore: key='$key', SUCCESS");
    } catch (e, st) {
      _logger.severe("saveSecureStore: key='$key', FAILED: $e", e, st);
      rethrow;
    }
  }
}

class ConfigService {
  final logger = Logger('ConfigService');
  late final SecureStore secureStore;
  final hmac = CryptoHmac();

  final VersionService versionService;

  ConfigService(this.versionService) {
    // Keychain requires trusted code signing (not ad-hoc).
    // Debug builds use ad-hoc signing, so we use file-based storage.
    // Release builds use Keychain for hardware-backed security.
    if (kReleaseMode) {
      secureStore = KeychainSecureStore(logger);
      logger.info("Secure store: Keychain (release mode)");
    } else {
      secureStore = FileSecureStore(logger);
      logger.info("Secure store: file-based (debug mode)");
    }
  }

  Future<AppConfig> loadConfig(String content) async {
    try {
      final jsonMap = jsonDecode(content) as Map<String, dynamic>;
      final config = AppConfig.fromJson(jsonMap);

      return config;
    } catch (e) {
      throw CorruptedConfigException('Failed to load config: $e');
    }
  }

  Future<bool> verifyConfig(AppConfig config, ProtectedValue macKey) async {
    final message = utf8.encode(json.encode(config.data.toJson()));
    final hash =
        await hmac.calculateMac(ProtectedValue.fromBinary(message), macKey);
    return hex.encode(CrockfordBase32.decode(config.hash).binaryValue) ==
        hex.encode(hash);
  }

  Future<AppConfig> createConfig(
      String instanceId,
      Uint8List transformSeed,
      Uint8List masterSeed,
      AuthEncryptedResult protectedSymmetricKey,
      ProtectedValue macKey,
      Uint8List nonce, {
      String? secondaryKeySalt,
    }) async {
    final now = DateTime.now();
    final configData = ConfigData(
      instanceId: instanceId,
      createdAt: now,
      updatedAt: now,
      kdf: KdfParams(algorithm: "argon2id", m: 19, t: 2, p: 1),
      transformSeed: CrockfordBase32.encode(transformSeed),
      masterSeed: CrockfordBase32.encode(masterSeed),
      encryptedKey: CrockfordBase32.encode(protectedSymmetricKey.cipherData),
      authTag: CrockfordBase32.encode(protectedSymmetricKey.authTag),
      nonce: CrockfordBase32.encode(nonce),
      secondaryKeySalt: secondaryKeySalt,
    );

    final message = utf8.encode(json.encode(configData..toJson()));
    final hash =
        await hmac.calculateMac(ProtectedValue.fromBinary(message), macKey);
    return AppConfig(
        version: await versionService.getPackageVersion(),
        data: configData,
        hash: CrockfordBase32.encode(hash));
  }

  Future<String> saveConfig(AppConfig config) async {
    final jsonString = jsonEncode(config.toJson());
    return jsonString;
  }

  Future<ProtectedValue?> readSecureStore(String key) async {
    return secureStore.read(key);
  }

  Future<void> saveSecureStore(String key, ProtectedValue data) async {
    return secureStore.write(key, data);
  }

  Future<Uint8List> generateEmergencyKit(String secretKey) async {
    final pdf = pw.Document();

    pdf.addPage(
      pw.Page(
        build: (context) => pw.Center(
          child: pw.Column(
            mainAxisAlignment: pw.MainAxisAlignment.center,
            children: [
              pw.Text(
                'SECRET KEY BACKUP',
                style: pw.TextStyle(
                  fontSize: 24,
                  fontWeight: pw.FontWeight.bold,
                ),
              ),
              pw.SizedBox(height: 20),
              pw.Text(
                'Generated on: ${DateTime.now().toString()}',
                style: const pw.TextStyle(fontSize: 12),
              ),
              pw.Divider(),
              pw.Text(
                secretKey,
                style: pw.TextStyle(
                  fontSize: 18,
                  font: pw.Font.courier(),
                ),
              ),
              pw.SizedBox(height: 30),
              pw.Text(
                '⚠️ Keep this key secure! Do not share it.',
                style: pw.TextStyle(color: PdfColors.red),
              ),
            ],
          ),
        ),
      ),
    );

    return pdf.save();
  }
}
