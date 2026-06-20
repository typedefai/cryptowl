import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:convert/convert.dart';
import 'package:cryptography/cryptography.dart';
import 'package:cryptowl/src/common/exceptions.dart';
import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:cryptowl/src/service/config_service.dart';
import 'package:cryptowl/src/service/kdf_service.dart';
import 'package:cryptowl/src/service/version_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:path/path.dart' as path;

import '../repositories/test_util.dart';
@GenerateNiceMocks([MockSpec<ConfigService>(), MockSpec<VersionService>()])
import 'kdf_service_test.mocks.dart';

void main() {
  final mockVersionService = MockVersionService();
  late KdfService service;
  setUp(() {
    setupArgon2();
    service = KdfService(ConfigService(mockVersionService));
  });

  test('should return 32 bytes secret key', () async {
    final key = await service.generateRandomBytes(length: 32);
    expect(key.binaryValue, hasLength(32));
  });

  test('should return 16 bytes salt', () async {
    final key = await service.generateRandomBytes(length: 16);
    expect(key.binaryValue, hasLength(16));
  });

  test('should return transformed master key', () async {
    // https://argon2.online/
    // $argon2id$v=19$m=19456,t=2,p=1$ZHJAcmlndXouY29t$CMiXFHTwhEhxwgjjl7BDk65dnX3p8plUpMKY95AE2o4

    final secretKey = hex.decode(
            "9a54bef1921ce1c89255dc67229ffffd2dd1efb5ef3cdd3da66ae9ab53fb974f")
        as Uint8List;
    final key = await service.createTransformedMasterKey(
        ProtectedValue.fromString("123456"),
        ProtectedValue.fromBinary(secretKey),
        hex.decode("b27f6e2bd596308c190c4f1d68660bc3") as Uint8List);
    String encoded = hex.encode(key.binaryValue);
    expect(
        encoded,
        equals(
            "509f825b859521f72fe511d2c120f53ed52bf641932d92ba086b89be3d65153a"));
  });

  test('should return stretched master key', () async {
    final masterKey = hex.decode(
            "509f825b859521f72fe511d2c120f53ed52bf641932d92ba086b89be3d65153a")
        as Uint8List;
    final masterSeed =
        hex.decode("8a7c01c0b81c8872e016d779486bc189") as Uint8List;
    final strecthedMasterKey = await service.createStretchedMasterKey(
        ProtectedValue.fromBinary(masterKey), utf8.encode("WJB6W"), masterSeed);

    expect(strecthedMasterKey.binaryValue, hasLength(64));
    expect(
        hex.encode(strecthedMasterKey.binaryValue),
        equals(
            "6414d3f58fcaf252675f1544e4e7d5e389fd1dd319c6df9693b88b44e7363340c38a015a41594d78650f501bd7e86fcd88ba7a21d4efb54dc3820056bd9039c9"));
  });

  test('should return protected symmetric key', () async {
    final stretchedMasterKey = hex.decode(
            "6414d3f58fcaf252675f1544e4e7d5e389fd1dd319c6df9693b88b44e7363340c38a015a41594d78650f501bd7e86fcd88ba7a21d4efb54dc3820056bd9039c9")
        as Uint8List;
    final symmetricKey = hex.decode(
            "8fc13f5ef75f029588dfe60f72706283bbc1e781a13f3df799c25131abb8b300adf0efe34d377c605f964bd505bf174c1f4521d6244d5e75309dc3ea115b95be")
        as Uint8List;
    final instanceId = utf8.encode("WJB6W");
    final nonce = hex.decode("2921075aed8cae8b22aae119") as Uint8List;

    final result = await service.createProtectedSymmetricKey(
        ProtectedValue.fromBinary(symmetricKey),
        ProtectedValue.fromBinary(stretchedMasterKey),
        nonce,
        instanceId);

    expect(hex.encode(result.cipherData),
        "b2b3cb94378b635340f335529e868a1f87d360cbf0b864be73901eb753d1b5b4320bce63ae386e599114a648422cd3f73321f85ddb4cd89eab936101f3c72883");
    expect(
        hex.encode(result.authTag), equals("4620505d9547ba3e77473bfecbaa5beb"));
  });

  test(
      'should create config and could decrypt the symmetric key if credentials correct',
      () async {
    final masterPassword = utf8.encode("123456");
    final secretKey = hex.decode(
            "9a54bef1921ce1c89255dc67229ffffd2dd1efb5ef3cdd3da66ae9ab53fb974f")
        as Uint8List;
    final transformSeed =
        hex.decode("b27f6e2bd596308c190c4f1d68660bc3") as Uint8List;
    final masterSeed = hex.decode(
            "9a54bef1921ce1c89255dc67229ffffd2dd1efb5ef3cdd3da66ae9ab53fb974f")
        as Uint8List;
    final symmetricKey = hex.decode(
            "9a54bef1921ce1c89255dc67229ffffd2dd1efb5ef3cdd3da66ae9ab53fb974f")
        as Uint8List;
    final nonce = hex.decode(
            "9a54bef1921ce1c89255dc67229ffffd2dd1efb5ef3cdd3da66ae9ab53fb974f")
        as Uint8List;
    final config = await service.generateAppConfig(
        ProtectedValue.fromBinary(masterPassword),
        ProtectedValue.fromBinary(secretKey),
        "001",
        transformSeed,
        masterSeed,
        ProtectedValue.fromBinary(symmetricKey),
        nonce);

    final decrypted = await service.decryptSymmetricKey(
        ProtectedValue.fromBinary(masterPassword),
        ProtectedValue.fromBinary(secretKey),
        config);
    expect(decrypted.binaryValue, symmetricKey);
  });

  test('should decrypt and get symmetric key if credentials correct', () async {
    final scriptDir = path.dirname(Platform.script.path);
    final file = File(path.join(scriptDir, 'test/services/WJB6W.cfg'));
    final content = await file.readAsString();
    final config = await ConfigService(mockVersionService).loadConfig(content);

    final masterPassword = utf8.encode("123456");
    final secretKey = hex.decode(
            "9a54bef1921ce1c89255dc67229ffffd2dd1efb5ef3cdd3da66ae9ab53fb974f")
        as Uint8List;

    final symmetricKey = hex.decode(
            "8fc13f5ef75f029588dfe60f72706283bbc1e781a13f3df799c25131abb8b300adf0efe34d377c605f964bd505bf174c1f4521d6244d5e75309dc3ea115b95be")
        as Uint8List;

    final decrypted = await service.decryptSymmetricKey(
        ProtectedValue.fromBinary(masterPassword),
        ProtectedValue.fromBinary(secretKey),
        config);
    expect(decrypted.binaryValue, symmetricKey);
  });

  test('should return error when decrypt if master password in correct',
      () async {
    final scriptDir = path.dirname(Platform.script.path);
    final file = File(path.join(scriptDir, 'test/services/WJB6W.cfg'));
    final content = await file.readAsString();
    final config = await ConfigService(mockVersionService).loadConfig(content);

    final masterPassword = utf8.encode("45678");
    final secretKey = hex.decode(
            "9a54bef1921ce1c89255dc67229ffffd2dd1efb5ef3cdd3da66ae9ab53fb974f")
        as Uint8List;

    Future<ProtectedValue> r() async => await service.decryptSymmetricKey(
        ProtectedValue.fromBinary(masterPassword),
        ProtectedValue.fromBinary(secretKey),
        config);

    expect(r, throwsA(isA<SecretBoxAuthenticationError>()));
  });

  test('should return error when decrypt if secret key incorrect', () async {
    final scriptDir = path.dirname(Platform.script.path);
    final file = File(path.join(scriptDir, 'test/services/WJB6W.cfg'));
    final content = await file.readAsString();
    final config = await ConfigService(mockVersionService).loadConfig(content);

    final masterPassword = utf8.encode("123456");
    final secretKey = hex.decode(
        "9a54bef1921ce1c89255dc67229ffffd2dd1efb5ef3cdd3da66ae9ab53fb974f"
            .replaceAll("9", "8")) as Uint8List;

    Future<ProtectedValue> r() async => await service.decryptSymmetricKey(
        ProtectedValue.fromBinary(masterPassword),
        ProtectedValue.fromBinary(secretKey),
        config);

    expect(r, throwsA(isA<SecretBoxAuthenticationError>()));
  });

  test('should return error when decrypt if config file hash not match',
      () async {
    final scriptDir = path.dirname(Platform.script.path);
    final file = File(path.join(scriptDir, 'test/services/WJB6W.cfg'));
    final content = await file.readAsString();
    // corrupt create/update time, if other fields are corrupted, will result in SecretBoxAuthenticationError
    final config = await ConfigService(mockVersionService)
        .loadConfig(content.replaceAll("2025-09-21", "2025-09-22"));

    final masterPassword = utf8.encode("123456");
    final secretKey = hex.decode(
            "9a54bef1921ce1c89255dc67229ffffd2dd1efb5ef3cdd3da66ae9ab53fb974f")
        as Uint8List;

    Future<ProtectedValue> r() async => await service.decryptSymmetricKey(
        ProtectedValue.fromBinary(masterPassword),
        ProtectedValue.fromBinary(secretKey),
        config);

    expect(r, throwsA(isA<CorruptedConfigException>()));
  });
}
