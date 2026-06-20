import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:cryptowl/src/crypto/aead_crypto.dart';
import 'package:cryptowl/src/domain/user.dart';
import 'package:cryptowl/src/service/config_service.dart';
import 'package:cryptowl/src/service/file_service.dart';
import 'package:cryptowl/src/service/kdf_service.dart';
import 'package:logging/logging.dart';

import '../common/exceptions.dart';
import '../crypto/crockford_base32.dart';
import '../crypto/protected_value.dart';
import '../crypto/random_util.dart';
import '../database/database.dart';

class AppService {
  final logger = Logger('AppService');
  final FileService fileService;
  final KdfService kdfService;
  final ConfigService configService;
  final aeadCrypto = CryptographyAesGcm();

  AppService(this.fileService, this.kdfService, this.configService);

  Future<bool> isInitialized() async {
    final dbs = await fileService.getSqlcipherInstances();
    logger.info("Find existing instances: $dbs");
    return dbs.isNotEmpty;
  }

  Future<void> initialize(ProtectedValue masterPassword, String? hint,
      {ProtectedValue? secondaryPassword}) async {
    logger.info("=== INITIALIZE START ===");

    logger.fine("[1/10] Copying Jieba dictionaries...");
    await fileService.copyJiebaDicts();
    logger.fine("[1/10] Jieba dictionaries copied");

    logger.fine("[2/10] Generating random secret key (32 bytes)...");
    final secretKey = await kdfService.generateRandomBytes(length: 32);
    logger.fine("[2/10] Secret key generated");

    final instanceId = RandomUtil.generateName();
    logger.fine("[3/10] Instance ID: $instanceId");

    final secretKeyLocation = _secretKeyId(instanceId);
    logger.fine("[4/10] Saving secret key to secure store at '$secretKeyLocation'...");
    await configService.saveSecureStore(secretKeyLocation, secretKey);
    logger.fine("[4/10] Secret key saved to secure store");

    if (hint != null) {
      logger.fine("[4b/10] Saving hint to secure store...");
      await configService.saveSecureStore(
          _hintId(instanceId), ProtectedValue.fromBinary(utf8.encode(hint)));
      logger.fine("[4b/10] Hint saved");
    }

    logger.fine("[5/10] Verifying secret key was saved correctly...");
    final savedSecretKey =
        await configService.readSecureStore(secretKeyLocation);
    if (secretKey != savedSecretKey) {
      logger.severe("[5/10] FAILED: Secret key verification mismatch!");
      throw Exception("Failed to save secret key");
    }
    logger.fine("[5/10] Secret key verified");

    logger.fine("[6/10] Generating random seeds and symmetric key...");
    final transformSeed = await kdfService.generateRandomBytes(length: 16);
    final masterSeed = await kdfService.generateRandomBytes(length: 16);
    final symmetricKey = await kdfService.generateRandomBytes(length: 64);
    final nonce = await kdfService.generateRandomBytes(length: 12);
    logger.fine("[6/10] Random values generated");

    // Generate secondary key salt if secondary password provided
    String? secondaryKeySalt;
    if (secondaryPassword != null) {
      logger.fine("[6b/10] Generating secondary key salt...");
      final salt = await kdfService.generateRandomBytes(length: 16);
      secondaryKeySalt = salt.getText();
      logger.fine("[6b/10] Secondary key salt generated");
    }

    logger.fine("[7/10] Generating app config (KDF + encryption)...");
    final config = await kdfService.generateAppConfig(
        masterPassword,
        secretKey,
        instanceId,
        transformSeed.binaryValue,
        masterSeed.binaryValue,
        symmetricKey,
        nonce.binaryValue,
        secondaryKeySalt: secondaryKeySalt);
    logger.fine("[7/10] App config generated");

    logger.fine("[8/10] Writing config file...");
    await fileService.writeFile(
        json.encode(config.toJson()), "${instanceId}.cfg");
    logger.fine("[8/10] Config file written");

    logger.fine("[9/10] Opening SQLCipher database...");
    final db = SqliteDb.open(
        "${instanceId}.enc",
        ProtectedValue.fromBinary(
            Uint8List.sublistView(symmetricKey.binaryValue, 0, 32)));

    logger.fine("[10/10] Creating sqlcipher db $instanceId...");
    await db.select(db.tPassword).get();
    await db.close();
    logger.info("=== INITIALIZE COMPLETE === instanceId=$instanceId");
  }

  Future<Session> login(String instanceId, ProtectedValue password) async {
    logger.info("=== LOGIN START === instanceId=$instanceId");

    logger.fine("[1/5] Reading config file...");
    final File configFile = await fileService.getConfigFile(instanceId);
    if (!await configFile.exists()) {
      logger.severe("[1/5] Config file does not exist: $configFile");
      throw CorruptedConfigException("Config file does not exist: $configFile");
    }
    final data = await configFile.readAsBytes();
    final config = await configService.loadConfig(utf8.decode(data));
    logger.fine("[1/5] Config loaded, instanceId=${config.data.instanceId}");

    final secretKeyLocation = _secretKeyId(config.data.instanceId);
    logger.fine("[2/5] Reading secret key from secure store at '$secretKeyLocation'...");
    final secretKey = await configService.readSecureStore(secretKeyLocation);
    if (secretKey == null) {
      logger.severe("[2/5] Secret key not found!");
      throw CorruptedConfigException(
          "Secret key not found for instance:${config.data.instanceId}");
    }
    logger.fine("[2/5] Secret key found");

    ProtectedValue symmetricKey, encryptionKey;
    try {
      logger.fine("[3/5] Decrypting symmetric key (Argon2id + HKDF + AES-GCM)...");
      symmetricKey =
          await kdfService.decryptSymmetricKey(password, secretKey, config);
      encryptionKey = ProtectedValue.fromBinary(
          Uint8List.sublistView(symmetricKey.binaryValue, 0, 32));
      logger.fine("[3/5] Symmetric key decrypted");
    } catch (e) {
      logger.warning("[3/5] Failed to decrypt symmetric key: $e");
      throw IncorrectPasswordException();
    }
    logger.info("[3/5] Successfully decrypted symmetric key");

    try {
      logger.fine("[4/5] Opening SQLCipher database...");
      final sqlite =
          SqliteDb.open("${config.data.instanceId}.enc", encryptionKey);

      logger.fine("[5/5] Verifying database...");
      await sqlite.select(sqlite.tPassword).get();
      logger.info("=== LOGIN COMPLETE ===");
      return Session(sqlite, symmetricKey);
    } catch (e) {
      logger.severe("[4-5/5] Failed to open sqlcipher: $e");
      throw InternalException("Failed to open sqlcihper: ${e}");
    }
  }

  /// Unlock Top Secret items by deriving the secondary key from the secondary password.
  /// Returns a new Session with the secondary key set.
  Future<Session> unlockSecondaryKey(
      Session session, ProtectedValue secondaryPassword) async {
    logger.info("=== UNLOCK TOP SECRET ===");

    // Read the config to get the secondary key salt
    final dbs = await fileService.getSqlcipherInstances();
    if (dbs.isEmpty) {
      throw InternalException("No vault found");
    }
    final instanceId = dbs.first.replaceAll(RegExp(r'.enc$'), '');
    final configFile = await fileService.getConfigFile(instanceId);
    final data = await configFile.readAsBytes();
    final config = await configService.loadConfig(utf8.decode(data));

    if (config.data.secondaryKeySalt == null) {
      throw InternalException("No secondary password configured");
    }

    logger.fine("Deriving secondary key...");
    final salt = CrockfordBase32.decode(config.data.secondaryKeySalt!);
    final secondaryKey =
        await kdfService.createSecondaryKey(secondaryPassword, salt.binaryValue);
    final topSecretKek = await kdfService.createTopSecretKek(secondaryKey);
    logger.info("=== TOP SECRET UNLOCKED ===");

    return session.withSecondaryKey(topSecretKek);
  }

  String _secretKeyId(String instanceId) {
    return "SECRET-KEY:$instanceId";
  }

  String _hintId(String instanceId) {
    return "HINT:$instanceId";
  }
}
