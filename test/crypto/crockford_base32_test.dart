import 'dart:convert';

import 'package:cryptowl/src/crypto/crockford_base32.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('should return crockford base32 string', () async {
    final password = utf8.encode("hello world!");

    final str = CrockfordBase32.encode(password);

    expect(str, equals("D1JPR-V3F41-VPYWK-CCGGG"));
  });

  test('should return decoded key from crockford base32 string', () async {
    final password = utf8.encode("hello world!");
    final key1 = CrockfordBase32.decode("D1JP-RV3F-41VP-YWKC-CGGG");
    final key2 = CrockfordBase32.decode("D1JPRV3F41VPYWKCCGGG");

    expect(key1.binaryValue, equals(password));
    expect(key2.binaryValue, equals(password));
  });
}
