import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:convert/convert.dart';
import 'package:cryptowl/src/common/exceptions.dart';
import 'package:cryptowl/src/crypto/aead_crypto.dart';
import 'package:cryptowl/src/crypto/hmac.dart';
import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:logging/logging.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;

import '../common/path_util.dart';
import '../config/app_config.dart';
import '../crypto/crockford_base32.dart';
import 'version_service.dart';

/// Hybrid secure store: tries FlutterSecureStorage (Keychain) first,
/// falls back to file-based storage if Keychain is unavailable
/// (e.g. ad-hoc signed debug builds on macOS).
class _HybridSecureStore {
  final _keychain = FlutterSecureStorage();
  final Logger _logger;
  bool _keychainAvailable = true;

  _HybridSecureStore(this._logger);

  Future<String> _filePath(String key) async {
    final safeKey = key.replaceAll(RegExp(r'[^a-zA-Z0-9_\-]'), '_');
    return PathUtil.getLocalPath('.secrets/$safeKey');
  }

  Future<ProtectedValue?> read(String key) async {
    // Try Keychain first
    if (_keychainAvailable) {
      try {
        final value = await _keychain.read(key: key);
        if (value != null) {
          _logger.fine("readSecureStore: key='$key' (keychain), found=true");
          return CrockfordBase32.decode(value);
        }
        _logger.fine("readSecureStore: key='$key' (keychain), found=false");
        return null;
      } catch (e) {
        _logger.warning("readSecureStore: keychain unavailable, falling back to file: $e");
        _keychainAvailable = false;
      }
    }

    // Fallback: file-based storage
    try {
      final path = await _filePath(key);
      final file = File(path);
      if (!await file.exists()) {
        _logger.fine("readSecureStore: key='$key' (file), not found");
        return null;
      }
      final value = await file.readAsString();
      _logger.fine("readSecureStore: key='$key' (file), found=true");
      return CrockfordBase32.decode(value.trim());
    } catch (e) {
      _logger.warning("readSecureStore: key='$key', error: $e");
      return null;
    }
  }

  Future<void> write(String key, ProtectedValue data) async {
    final encoded = CrockfordBase32.encodeProtected(data);

    // Try Keychain first
    if (_keychainAvailable) {
      try {
        await _keychain.write(key: key, value: encoded);
        _logger.fine("saveSecureStore: key='$key' (keychain), SUCCESS");
        return;
      } catch (e) {
        _logger.warning("saveSecureStore: keychain unavailable, falling back to file: $e");
        _keychainAvailable = false;
      }
    }

    // Fallback: file-based storage
    try {
      final path = await _filePath(key);
      final file = File(path);
      await file.parent.create(recursive: true);
      await file.writeAsString(encoded, flush: true);
      _logger.fine("saveSecureStore: key='$key' (file), SUCCESS");
    } catch (e, st) {
      _logger.severe("saveSecureStore: key='$key', FAILED: $e", e, st);
      rethrow;
    }
  }
}

class ConfigService {
  final logger = Logger('ConfigService');
  late final _HybridSecureStore _secureStore;
  final hmac = CryptoHmac();

  final VersionService versionService;

  ConfigService(this.versionService) {
    _secureStore = _HybridSecureStore(logger);
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
    return _secureStore.read(key);
  }

  Future<void> saveSecureStore(String key, ProtectedValue data) async {
    return _secureStore.write(key, data);
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
