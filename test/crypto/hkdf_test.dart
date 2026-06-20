import 'dart:convert';

import 'package:convert/convert.dart';
import 'package:cryptowl/src/crypto/hkdf.dart';
import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final cgHkdf = CryptoGraphyHkdf();
  final cryptoHkdf = CryptoHkdf();

  // https://fotoventus.cz/tool/hkdf.html
  final key = hex.decode(
          "509f825b859521f72fe511d2c120f53ed52bf641932d92ba086b89be3d65153a")
      as Uint8List;
  final salt = hex.decode("b27f6e2bd596308c190c4f1d68660bc3") as Uint8List;
  final info = utf8.encode("41964e60-5fc3-472c-8b87-71363c71b03c");

  test('should return extended key using crypto', () async {
    final stretched = await cryptoHkdf.deriveKey(
        ikm: ProtectedValue.fromBinary(key), salt: salt, info: info);

    expect(stretched.binaryValue.length, 64);
    expect(hex.encode(stretched.binaryValue),
        "6afa653ec25489cf4501713b2d97293361dcc492f05de076ee95a5033ff81682588a599a45b9110676cf76e421548013e4e289f305918ba31bd6e24f227d67c8");
  });

  test('should return extended key using cryptography', () async {
    final stretched = await cgHkdf.deriveKey(
        ikm: ProtectedValue.fromBinary(key), salt: salt, info: info);

    expect(stretched.binaryValue.length, 64);
    expect(hex.encode(stretched.binaryValue),
        "6afa653ec25489cf4501713b2d97293361dcc492f05de076ee95a5033ff81682588a599a45b9110676cf76e421548013e4e289f305918ba31bd6e24f227d67c8");
  });
}
