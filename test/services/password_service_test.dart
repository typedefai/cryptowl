import 'package:convert/convert.dart';
import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:cryptowl/src/database/database.dart';
import 'package:cryptowl/src/domain/password.dart';
import 'package:cryptowl/src/domain/user.dart';
import 'package:cryptowl/src/providers/providers.dart';
import 'package:cryptowl/src/repositories/password_repository.dart';
import 'package:cryptowl/src/service/kdf_service.dart';
import 'package:cryptowl/src/service/password_service.dart';
import 'package:drift/drift.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import '../repositories/test_util.dart';
@GenerateNiceMocks([MockSpec<Ref>(), MockSpec<KdfService>()])
import 'password_service_test.mocks.dart';

void main() {
  late SqliteDb database;
  late MockKdfService mockKdfService;
  late PasswordRepository passwordRepository;
  late PasswordService service;
  final mockRef = MockRef();

  WidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    setupArgon2();
    database = SqliteDb.from(openTestDatabase());
    await database.select(database.categories).get();
    mockKdfService = MockKdfService();

    provideDummy<Future<Session?>>(Future.value(null));

    when(mockRef.read(asyncLoginProvider.future)).thenAnswer(
        (_) async => Session(database, ProtectedValue.fromString("fake key")));
    passwordRepository = PasswordRepository(mockRef);
    service = PasswordService(mockKdfService, passwordRepository);
  });

  tearDown(() async {
    await database.close();
  });

  final kek = hex.decode(
          "9a54bef1921ce1c89255dc67229ffffd2dd1efb5ef3cdd3da66ae9ab53fb974f")
      as Uint8List;
  test('should create new secert password', () async {
    final key = hex.decode(
            "3f09ea13ceffb8e867a4af3ab17854f9f5f152591653c737a8962b94356e2c0f")
        as Uint8List;
    final keyNonce = hex.decode("b27f6e2bd596308c190c4f1d") as Uint8List;
    final dataNonce = hex.decode("87ab2c66744df1f6ae8fbd8a") as Uint8List;

    when(mockKdfService.generateRandomBytes(length: 32))
        .thenAnswer((_) async => ProtectedValue.fromBinary(key));
    var r1 = 0, r2 = 0;
    when(mockKdfService.generateRandomBytes(length: 12)).thenAnswer((_) async {
      r1++;
      if (r1 == 1) {
        return ProtectedValue.fromBinary(keyNonce);
      } else {
        return ProtectedValue.fromBinary(dataNonce);
      }
    });
    when(mockKdfService.generateUUID()).thenAnswer((_) async {
      r2++;
      if (r2 == 1) {
        return "41964e60-5fc3-472c-8b87-71363c71b03c"; // dekId
      } else if (r2 == 2) {
        return "41964e60-5fc3-472c-8b87-71363c71b03d"; // encryptedDataId
      } else {
        return "41964e60-5fc3-472c-8b87-71363c71b03e"; // passwordId
      }
    });

    final p = Password.create(
        "google",
        ProtectedValue.fromString("123456@google.com"),
        false,
        "Riguz",
        "Need VPN");

    await service.createPassword(p, ProtectedValue.fromBinary(kek));

    final dek = await database.select(database.tDataEncryptKey).getSingle();
    final encryptedData =
        await database.select(database.tEncryptedData).getSingle();
    final passwordEntity =
        await database.select(database.tPassword).getSingle();
    final attributes = await database.select(database.tPasswordAttribute).get();
    expect(dek.id, "41964e60-5fc3-472c-8b87-71363c71b03c");
    expect(crockford32ToHex(dek.nonce), "b27f6e2bd596308c190c4f1d");
    expect(crockford32ToHex(dek.data),
        "3082bf088b2fc632789b59ba9c7f78028f32a1e7dc003935bda86c73b5f183d4");
    expect(crockford32ToHex(dek.authTag), "766205c3c160ab5f9f72d0acb8490186");

    expect(encryptedData.id, "41964e60-5fc3-472c-8b87-71363c71b03d");
    expect(encryptedData.dekId, "41964e60-5fc3-472c-8b87-71363c71b03c");
    expect(crockford32ToHex(encryptedData.nonce), "87ab2c66744df1f6ae8fbd8a");
    expect(hex.encode(encryptedData.content),
        "5dd05d8d4fbf28670e65aa4f89965f9b9a");
    expect(crockford32ToHex(encryptedData.authTag),
        "740af7dfe47237d407725fec02f6ce96");
    expect(encryptedData.algorithmId, "2ad0737c-01a8-4d74-998f-9dfe855171fb");

    expect(passwordEntity.title, equals("google"));
    expect(passwordEntity.classification, "S");
    expect(
        passwordEntity.encryptedDataId, "41964e60-5fc3-472c-8b87-71363c71b03d");

    expect(attributes, hasLength(2));
    expect(attributes[0].passwordId, passwordEntity.id);
    expect(attributes[0].name, "user");
    expect(attributes[0].value, "Riguz");
    expect(attributes[1].name, "remark");
    expect(attributes[1].value, "Need VPN");
    expect(attributes[1].passwordId, passwordEntity.id);
  });

  test('should create new password without attributes', () async {
    final key = hex.decode(
            "3f09ea13ceffb8e867a4af3ab17854f9f5f152591653c737a8962b94356e2c0f")
        as Uint8List;
    final keyNonce = hex.decode("b27f6e2bd596308c190c4f1d") as Uint8List;
    final dataNonce = hex.decode("87ab2c66744df1f6ae8fbd8a") as Uint8List;

    when(mockKdfService.generateRandomBytes(length: 32))
        .thenAnswer((_) async => ProtectedValue.fromBinary(key));
    var r1 = 0, r2 = 0;
    when(mockKdfService.generateRandomBytes(length: 12)).thenAnswer((_) async {
      r1++;
      if (r1 == 1) {
        return ProtectedValue.fromBinary(keyNonce);
      } else {
        return ProtectedValue.fromBinary(dataNonce);
      }
    });
    when(mockKdfService.generateUUID()).thenAnswer((_) async {
      r2++;
      if (r2 == 1) {
        return "41964e60-5fc3-472c-8b87-71363c71b03c"; // dekId
      } else if (r2 == 2) {
        return "41964e60-5fc3-472c-8b87-71363c71b03d"; // encryptedDataId
      } else {
        return "41964e60-5fc3-472c-8b87-71363c71b03e"; // passwordId
      }
    });

    final p = Password.create("google",
        ProtectedValue.fromString("123456@google.com"), false, null, null);

    await service.createPassword(p, ProtectedValue.fromBinary(kek));

    final dek = await database.select(database.tDataEncryptKey).getSingle();
    final encryptedData =
        await database.select(database.tEncryptedData).getSingle();
    final passwordEntity =
        await database.select(database.tPassword).getSingle();
    final attributes = await database.select(database.tPasswordAttribute).get();
    expect(dek.id, "41964e60-5fc3-472c-8b87-71363c71b03c");
    expect(crockford32ToHex(dek.nonce), "b27f6e2bd596308c190c4f1d");
    expect(crockford32ToHex(dek.data),
        "3082bf088b2fc632789b59ba9c7f78028f32a1e7dc003935bda86c73b5f183d4");
    expect(crockford32ToHex(dek.authTag), "766205c3c160ab5f9f72d0acb8490186");

    expect(encryptedData.id, "41964e60-5fc3-472c-8b87-71363c71b03d");
    expect(encryptedData.dekId, "41964e60-5fc3-472c-8b87-71363c71b03c");
    expect(crockford32ToHex(encryptedData.nonce), "87ab2c66744df1f6ae8fbd8a");
    expect(hex.encode(encryptedData.content),
        "5dd05d8d4fbf28670e65aa4f89965f9b9a");
    expect(crockford32ToHex(encryptedData.authTag),
        "740af7dfe47237d407725fec02f6ce96");
    expect(encryptedData.algorithmId, "2ad0737c-01a8-4d74-998f-9dfe855171fb");

    expect(passwordEntity.title, equals("google"));
    expect(passwordEntity.classification, "S");
    expect(
        passwordEntity.encryptedDataId, "41964e60-5fc3-472c-8b87-71363c71b03d");

    expect(attributes, hasLength(0));
  });

  test('should create new top secret password', () async {
    final key = hex.decode(
            "3f09ea13ceffb8e867a4af3ab17854f9f5f152591653c737a8962b94356e2c0f")
        as Uint8List;
    final keyNonce = hex.decode("b27f6e2bd596308c190c4f1d") as Uint8List;
    final dataNonce =
        hex.decode("87ab2c66744df1f6ae8fbd8a87ab2c66744df1f6ae8fbd8a")
            as Uint8List;

    when(mockKdfService.generateRandomBytes(length: 32))
        .thenAnswer((_) async => ProtectedValue.fromBinary(key));
    var r2 = 0;
    when(mockKdfService.generateRandomBytes(length: 12))
        .thenAnswer((_) async => ProtectedValue.fromBinary(keyNonce));
    when(mockKdfService.generateRandomBytes(length: 24))
        .thenAnswer((_) async => ProtectedValue.fromBinary(dataNonce));
    when(mockKdfService.generateUUID()).thenAnswer((_) async {
      r2++;
      if (r2 == 1) {
        return "41964e60-5fc3-472c-8b87-71363c71b03c"; // dekId
      } else if (r2 == 2) {
        return "41964e60-5fc3-472c-8b87-71363c71b03d"; // encryptedDataId
      } else {
        return "41964e60-5fc3-472c-8b87-71363c71b03e"; // passwordId
      }
    });

    final p = Password.create(
        "google",
        ProtectedValue.fromString("123456@google.com"),
        true,
        "Riguz",
        "Need VPN");

    await service.createPassword(p, ProtectedValue.fromBinary(kek));

    final dek = await database.select(database.tDataEncryptKey).getSingle();
    final encryptedData =
        await database.select(database.tEncryptedData).getSingle();
    final passwordEntity =
        await database.select(database.tPassword).getSingle();
    final attributes = await database.select(database.tPasswordAttribute).get();
    expect(dek.id, "41964e60-5fc3-472c-8b87-71363c71b03c");
    expect(crockford32ToHex(dek.nonce), "b27f6e2bd596308c190c4f1d");
    expect(crockford32ToHex(dek.data),
        "3082bf088b2fc632789b59ba9c7f78028f32a1e7dc003935bda86c73b5f183d4");
    expect(crockford32ToHex(dek.authTag), "766205c3c160ab5f9f72d0acb8490186");

    expect(encryptedData.id, "41964e60-5fc3-472c-8b87-71363c71b03d");
    expect(encryptedData.dekId, "41964e60-5fc3-472c-8b87-71363c71b03c");
    expect(crockford32ToHex(encryptedData.nonce),
        "87ab2c66744df1f6ae8fbd8a87ab2c66744df1f6ae8fbd8a");
    expect(hex.encode(encryptedData.content),
        "31a19da13b6efdf49cb700ff2344be75a6");
    expect(crockford32ToHex(encryptedData.authTag),
        "810bfb67ec101748904cf6663d1372d0");
    expect(encryptedData.algorithmId, "8c378b87-5d19-4ef4-9848-561c533d9e04");

    expect(passwordEntity.title, equals("google"));
    expect(passwordEntity.classification, "T");
    expect(
        passwordEntity.encryptedDataId, "41964e60-5fc3-472c-8b87-71363c71b03d");

    expect(attributes, hasLength(2));
    expect(attributes[0].passwordId, passwordEntity.id);
    expect(attributes[0].name, "user");
    expect(attributes[0].value, "Riguz");
    expect(attributes[1].name, "remark");
    expect(attributes[1].value, "Need VPN");
    expect(attributes[1].passwordId, passwordEntity.id);
  });
}
