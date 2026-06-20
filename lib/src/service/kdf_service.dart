import 'dart:convert';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:cryptowl/src/common/exceptions.dart';
import 'package:cryptowl/src/config/app_config.dart';
import 'package:cryptowl/src/crypto/argon2.dart';
import 'package:cryptowl/src/crypto/crockford_base32.dart';
import 'package:cryptowl/src/crypto/hkdf.dart';
import 'package:cryptowl/src/crypto/random_util.dart';
import 'package:cryptowl/src/service/config_service.dart';

import '../crypto/aead_crypto.dart';
import '../crypto/protected_value.dart';

class KdfService {
  final hkdf = CryptoGraphyHkdf();
  final argon2 = NativeArgon2();
  final aeadCrypto = CryptographyAesGcm();

  final ConfigService configService;

  KdfService(this.configService);

  /// https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
  /// OWASP suggested:
  /// Use Argon2id with a minimum configuration of 19 MiB of memory,
  /// an iteration count of 2, and 1 degree of parallelism.
  /// $argon2id$v=19$m=19456,t=2,p=1$ZHJAcmlndXouY29t$CMiXFHTwhEhxwgjjl7BDk65dnX3p8plUpMKY95AE2o4
  Future<ProtectedValue> createTransformedMasterKey(
      ProtectedValue masterPassword,
      ProtectedValue secretKey,
      Uint8List salt) async {
    final hmacSha256 = Hmac(sha256, masterPassword.binaryValue);
    final hash1 = hmacSha256.convert(secretKey.binaryValue);

    final key = await argon2.deriveKey(
      Argon2Arguments(Uint8List.fromList(hash1.bytes), salt, 19 * 1024, 2, 32,
          1, Argon2Variant.argon2id, 19),
    );
    return ProtectedValue.fromBinary(key);
  }

  Future<ProtectedValue> createStretchedMasterKey(
      ProtectedValue transformedMasterKey,
      Uint8List instanceId,
      Uint8List masterSeed) async {
    return hkdf.deriveKey(
        ikm: transformedMasterKey, salt: masterSeed, info: instanceId);
  }

  Future<AuthEncryptedResult> createProtectedSymmetricKey(
      ProtectedValue symmetricKey,
      ProtectedValue stretchedMasterKey,
      Uint8List nonce,
      Uint8List instanceId) async {
    if (symmetricKey.binaryValue.length != 64) {
      throw InvalidKeyException(
          "Symmetric key should be 64 bytes long but actually is ${symmetricKey.binaryValue.length}");
    }
    if (stretchedMasterKey.binaryValue.length != 64) {
      throw InvalidKeyException(
          "Stretched master key should be 64 bytes long but actually is ${stretchedMasterKey.binaryValue.length}");
    }
    if (nonce.length != 12) {
      throw InvalidKeyException(
          "Nonce should be 12 bytes long but actually is ${nonce.length}");
    }
    return _encryptSymmetricKey(
        symmetricKey, stretchedMasterKey, nonce, instanceId);
  }

  Future<String> generateUUID() async {
    return RandomUtil.generateUUID();
  }

  Future<ProtectedValue> generateRandomBytes({int length = 32}) async {
    return ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(length));
  }

  Future<AppConfig> generateAppConfig(
    ProtectedValue masterPassword,
    ProtectedValue secretKey,
    String instanceId,
    Uint8List transformSeed,
    Uint8List masterSeed,
    ProtectedValue symmetricKey,
    Uint8List nonce,
  ) async {
    final instanceIdBytes = utf8.encode(instanceId);

    final transformedMasterKey = await createTransformedMasterKey(
        masterPassword, secretKey, transformSeed);
    final stretchedMasterKey = await createStretchedMasterKey(
        transformedMasterKey, instanceIdBytes, masterSeed);
    final encryptedSymmetricKey = await _encryptSymmetricKey(
        symmetricKey, stretchedMasterKey, nonce, instanceIdBytes);

    return configService.createConfig(
        instanceId,
        transformSeed,
        masterSeed,
        encryptedSymmetricKey,
        ProtectedValue.fromBinary(
            Uint8List.sublistView(stretchedMasterKey.binaryValue, 32)),
        nonce);
  }

  Future<ProtectedValue> decryptSymmetricKey(ProtectedValue masterPassword,
      ProtectedValue secretKey, AppConfig config) async {
    final configData = config.data;

    final transformSeed = CrockfordBase32.decode(configData.transformSeed);
    final masterSeed = CrockfordBase32.decode(configData.masterSeed);
    final transformedMasterKey = await createTransformedMasterKey(
        masterPassword, secretKey, transformSeed.binaryValue);
    final instanceIdBytes = utf8.encode(configData.instanceId);
    final stretchedMasterKey = await createStretchedMasterKey(
        transformedMasterKey, instanceIdBytes, masterSeed.binaryValue);

    final encryptedSymmetricKey =
        CrockfordBase32.decode(configData.encryptedKey);
    final authTag = CrockfordBase32.decode(configData.authTag);
    final nonce = CrockfordBase32.decode(configData.nonce);
    final decryptKey =
        Uint8List.sublistView(stretchedMasterKey.binaryValue, 0, 32);
    final symmetricKey = await aeadCrypto.decrypt(
        AuthEncryptedResult(
            encryptedSymmetricKey.binaryValue, authTag.binaryValue),
        ProtectedValue.fromBinary(decryptKey),
        nonce.binaryValue,
        instanceIdBytes);

    // though the symmetric key is decrypted, other config data might be corrupted,
    // thus it's necessary to verify it anyway
    final hashVerifyOk = await configService.verifyConfig(
        config,
        ProtectedValue.fromBinary(
            Uint8List.sublistView(stretchedMasterKey.binaryValue, 32)));
    if (!hashVerifyOk) {
      throw CorruptedConfigException("Config file is corrupted");
    }
    return symmetricKey;
  }

  Future<AuthEncryptedResult> _encryptSymmetricKey(
      ProtectedValue symmetricKey,
      ProtectedValue stretchedMasterKey,
      Uint8List nonce,
      Uint8List instanceId) async {
    final key = Uint8List.sublistView(stretchedMasterKey.binaryValue, 0, 32);
    return aeadCrypto.encrypt(
        symmetricKey, ProtectedValue.fromBinary(key), nonce, instanceId);
  }
}
