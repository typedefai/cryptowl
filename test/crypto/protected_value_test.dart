import 'dart:convert';

import 'package:convert/convert.dart';
import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('should return original value when get binary', () async {
    final p = ProtectedValue.fromString("hello world!");
    final p1 = ProtectedValue.fromBinary(hex.decode(
            "3f09ea13ceffb8e867a4af3ab17854f9f5f152591653c737a8962b94356e2c0f")
        as Uint8List);
    expect(p.binaryValue, utf8.encode("hello world!"));
    expect(
        p1.binaryValue,
        hex.decode(
            "3f09ea13ceffb8e867a4af3ab17854f9f5f152591653c737a8962b94356e2c0f"));
  });

  test('should return base32 text when get text', () async {
    final p = ProtectedValue.fromString("hello world!");

    expect(p.getText(), 'D1JPR-V3F41-VPYWK-CCGGG');
  });
}
