import 'dart:io';
import 'dart:typed_data';

import 'package:convert/convert.dart';
import 'package:cryptowl/src/crypto/aead_crypto.dart';
import 'package:cryptowl/src/crypto/crockford_base32.dart';
import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:cryptowl/src/service/config_service.dart';
import 'package:cryptowl/src/service/version_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:path/path.dart' as path;

import '../repositories/test_util.dart';
@GenerateNiceMocks([MockSpec<VersionService>()])
import 'config_service_test.mocks.dart';

void main() {
  final mockVersionService = MockVersionService();
  setUp(() {
    setupArgon2();
  });

  test('should return config object with hash', () async {
    final service = ConfigService(mockVersionService);
    final transformSeed =
        hex.decode("b27f6e2bd596308c190c4f1d68660bc3") as Uint8List;
    final masterSeed =
        hex.decode("8a7c01c0b81c8872e016d779486bc189") as Uint8List;
    final stretchedMasterKey = hex.decode(
            "6414d3f58fcaf252675f1544e4e7d5e389fd1dd319c6df9693b88b44e7363340c38a015a41594d78650f501bd7e86fcd88ba7a21d4efb54dc3820056bd9039c9")
        as Uint8List;
    final protectedSymmetricKey = hex.decode(
            "b2b3cb94378b635340f335529e868a1f87d360cbf0b864be73901eb753d1b5b4320bce63ae386e599114a648422cd3f73321f85ddb4cd89eab936101f3c72883")
        as Uint8List;
    final authTag = hex.decode("4620505d9547ba3e77473bfecbaa5beb") as Uint8List;
    final instanceId = "WJB6W";
    final nonce = hex.decode("2921075aed8cae8b22aae119") as Uint8List;

    when(mockVersionService.getPackageVersion())
        .thenAnswer((_) async => "1.0.0");

    final config = await service.createConfig(
        instanceId,
        transformSeed,
        masterSeed,
        AuthEncryptedResult(protectedSymmetricKey, authTag),
        ProtectedValue.fromBinary(stretchedMasterKey.sublist(32)),
        nonce);
    expect(config.version, "1.0.0");
    expect(config.data.instanceId, "WJB6W");
    expect(CrockfordBase32.decode(config.data.transformSeed).binaryValue,
        transformSeed);
    expect(
        CrockfordBase32.decode(config.data.masterSeed).binaryValue, masterSeed);
    expect(CrockfordBase32.decode(config.data.encryptedKey).binaryValue,
        protectedSymmetricKey);
    expect(CrockfordBase32.decode(config.data.authTag).binaryValue, authTag);
    expect(CrockfordBase32.decode(config.data.nonce).binaryValue, nonce);

    expect(CrockfordBase32.decode(config.hash).binaryValue, hasLength(32));
  });

  test('should return parsed config object', () async {
    final service = ConfigService(mockVersionService);
    final scriptDir = path.dirname(Platform.script.path);
    final file = File(path.join(scriptDir, 'test/services/WJB6W.cfg'));
    final content = await file.readAsString();
    final config = await service.loadConfig(content);

    when(mockVersionService.getPackageVersion())
        .thenAnswer((_) async => "1.0.0");

    expect(config.version, "1.0.0");
    expect(config.data.encryptedKey,
        "PASWQ-51QHD-HN6G7-K6N99-X1MA3-Y3X6R-6BY2W-69FKK-J0FBE-MYHPP-T342Y-ECEQ3-GVJSJ-4AACJ-225K9-ZECS1-Z1EXP-K6RKT-NS6R8-1YF3J-H0R");
    expect(config.hash,
        "7QYKT-H9PJG-J2ZZ7-DC83R-EBFZ4-H1HKC-K0ZWD-D4QJP-GWK73-DGF29-G0");
  });
}
