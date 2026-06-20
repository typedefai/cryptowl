import 'dart:convert';

import 'package:convert/convert.dart';
import 'package:cryptowl/src/crypto/hmac.dart';
import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final cgHmac = CryptographyHmac();
  final cryptoHmac = CryptoHmac();

  final key = hex.decode(
          "509f825b859521f72fe511d2c120f53ed52bf641932d92ba086b89be3d65153a")
      as Uint8List;
  final message = utf8.encode("41964e60-5fc3-472c-8b87-71363c71b03c");

  test('should return hmac-sha256 hash using crypto', () async {
    final hash = await cryptoHmac.calculateMac(
        ProtectedValue.fromBinary(message), ProtectedValue.fromBinary(key));

    expect(hash, hasLength(32));
    expect(hex.encode(hash),
        "d14d4dca4b89d24cf80b731320b57b3b94efe47e6b19972d40a5914b7053d5d7");

    final hash1 = await cgHmac.calculateMac(
        ProtectedValue.fromBinary(message), ProtectedValue.fromBinary(key));

    expect(hash1, hasLength(32));
    expect(hex.encode(hash1),
        "d14d4dca4b89d24cf80b731320b57b3b94efe47e6b19972d40a5914b7053d5d7");
  });
}
