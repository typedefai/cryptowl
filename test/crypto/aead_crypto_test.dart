import 'dart:convert';

import 'package:convert/convert.dart';
import 'package:cryptography/cryptography.dart';
import 'package:cryptowl/src/crypto/aead_crypto.dart';
import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final aesGcm = CryptographyAesGcm();
  final chacha20 = CryptographyChaCha20();

  // https://fotoventus.cz/tool/hkdf.html
  final data = utf8.encode("hello world!");
  final key = hex.decode(
          "3f09ea13ceffb8e867a4af3ab17854f9f5f152591653c737a8962b94356e2c0f")
      as Uint8List;
  final nonce = hex.decode("b27f6e2bd596308c190c4f1d") as Uint8List;
  final info = utf8.encode("41964e60-5fc3-472c-8b87-71363c71b03c");

  test('should return encoded key with auth tag', () async {
    final r = await aesGcm.encrypt(ProtectedValue.fromBinary(data),
        ProtectedValue.fromBinary(key), nonce, info);
    expect(hex.encode(r.cipherData), "33335861071ff401989294fa");
    expect(hex.encode(r.authTag), "53b19b6a4498a61b415c2e7963f1cab5");

    final r1 = await chacha20.encrypt(ProtectedValue.fromBinary(data),
        ProtectedValue.fromBinary(key), nonce, info);
    expect(hex.encode(r1.cipherData), "b42582b8cfd2c8d9e5ccdada");
    expect(hex.encode(r1.authTag), "c1c0b8cc288dafddc4389b1b85125fe7");
  });

  test('should return encoded key with auth tag via xchacha20', () async {
    final xchacha20 = CryptographyXChaCha20();
    final nonce = hex.decode("b27f6e2bd596308c190c4f1db27f6e2bd596308c190c4f1d")
        as Uint8List;
    final r1 = await xchacha20.encrypt(ProtectedValue.fromBinary(data),
        ProtectedValue.fromBinary(key), nonce, info);
    // TODO: this output is not cross verified
    expect(hex.encode(r1.cipherData), "3482330ca8061aa1160443cc");
    expect(hex.encode(r1.authTag), "ccda4a95f5d19a010e9c32d91c9e9a77");
  });

  test('should return decryted data given key and other info correct',
      () async {
    final data = hex.decode("33335861071ff401989294fa") as Uint8List;
    final authTag = hex.decode("53b19b6a4498a61b415c2e7963f1cab5") as Uint8List;
    final r = await aesGcm.decrypt(AuthEncryptedResult(data, authTag),
        ProtectedValue.fromBinary(key), nonce, info);
    expect(utf8.decode(r.binaryValue), "hello world!");

    final data1 = hex.decode("b42582b8cfd2c8d9e5ccdada") as Uint8List;
    final authTag1 =
        hex.decode("c1c0b8cc288dafddc4389b1b85125fe7") as Uint8List;
    final r1 = await chacha20.decrypt(AuthEncryptedResult(data1, authTag1),
        ProtectedValue.fromBinary(key), nonce, info);
    expect(utf8.decode(r1.binaryValue), "hello world!");
  });

  test('should throw error given data incorrect', () async {
    final wrongData = hex
        .decode("33335861071ff401989294fa".replaceAll("3", "4")) as Uint8List;
    final authTag = hex.decode("53b19b6a4498a61b415c2e7963f1cab5") as Uint8List;
    Future<ProtectedValue> r() async => await aesGcm.decrypt(
        AuthEncryptedResult(wrongData, authTag),
        ProtectedValue.fromBinary(key),
        nonce,
        info);

    expect(r, throwsA(isA<SecretBoxAuthenticationError>()));

    final wrongData1 = hex
        .decode("b42582b8cfd2c8d9e5ccdada".replaceAll("b", "4")) as Uint8List;
    final authTag1 =
        hex.decode("c1c0b8cc288dafddc4389b1b85125fe7") as Uint8List;
    Future<ProtectedValue> r1() async => await chacha20.decrypt(
        AuthEncryptedResult(wrongData1, authTag1),
        ProtectedValue.fromBinary(key),
        nonce,
        info);

    expect(r1, throwsA(isA<SecretBoxAuthenticationError>()));
  });

  test('should throw error given aad not provided', () async {
    final data = hex.decode("33335861071ff401989294fa") as Uint8List;
    final authTag = hex.decode("53b19b6a4498a61b415c2e7963f1cab5") as Uint8List;
    Future<ProtectedValue> r() async => await aesGcm.decrypt(
        AuthEncryptedResult(data, authTag),
        ProtectedValue.fromBinary(key),
        nonce,
        null);

    expect(r, throwsA(isA<SecretBoxAuthenticationError>()));

    final data1 = hex.decode("b42582b8cfd2c8d9e5ccdada") as Uint8List;
    final authTag1 =
        hex.decode("c1c0b8cc288dafddc4389b1b85125fe7") as Uint8List;
    Future<ProtectedValue> r1() async => await chacha20.decrypt(
        AuthEncryptedResult(data1, authTag1),
        ProtectedValue.fromBinary(key),
        nonce,
        null);

    expect(r1, throwsA(isA<SecretBoxAuthenticationError>()));
  });

  test('should throw error given key incorrect', () async {
    final data = hex.decode("33335861071ff401989294fa") as Uint8List;
    final authTag = hex.decode("53b19b6a4498a61b415c2e7963f1cab5") as Uint8List;
    final wrongKey = hex.decode(
        "3f09ea13ceffb8e867a4af3ab17854f9f5f152591653c737a8962b94356e2c0f"
            .replaceAll("1", "2")) as Uint8List;
    Future<ProtectedValue> r() async => await aesGcm.decrypt(
        AuthEncryptedResult(data, authTag),
        ProtectedValue.fromBinary(wrongKey),
        nonce,
        info);

    expect(r, throwsA(isA<SecretBoxAuthenticationError>()));

    final data1 = hex.decode("b42582b8cfd2c8d9e5ccdada") as Uint8List;
    final authTag1 =
        hex.decode("c1c0b8cc288dafddc4389b1b85125fe7") as Uint8List;
    Future<ProtectedValue> r1() async => await chacha20.decrypt(
        AuthEncryptedResult(data1, authTag1),
        ProtectedValue.fromBinary(wrongKey),
        nonce,
        info);

    expect(r1, throwsA(isA<SecretBoxAuthenticationError>()));
  });

  test('should throw error given nonce incorrect', () async {
    final data = hex.decode("33335861071ff401989294fa") as Uint8List;
    final authTag = hex.decode("53b19b6a4498a61b415c2e7963f1cab5") as Uint8List;
    final wrongNonce = hex
        .decode("b27f6e2bd596308c190c4f1d".replaceAll("b", "f")) as Uint8List;
    Future<ProtectedValue> r() async => await aesGcm.decrypt(
        AuthEncryptedResult(data, authTag),
        ProtectedValue.fromBinary(key),
        wrongNonce,
        info);

    expect(r, throwsA(isA<SecretBoxAuthenticationError>()));

    final data1 = hex.decode("b42582b8cfd2c8d9e5ccdada") as Uint8List;
    final authTag1 =
        hex.decode("c1c0b8cc288dafddc4389b1b85125fe7") as Uint8List;
    Future<ProtectedValue> r1() async => await chacha20.decrypt(
        AuthEncryptedResult(data1, authTag1),
        ProtectedValue.fromBinary(key),
        wrongNonce,
        info);

    expect(r1, throwsA(isA<SecretBoxAuthenticationError>()));
  });
}
