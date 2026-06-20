import 'dart:typed_data';

import 'package:base32/base32.dart';
import 'package:base32/encodings.dart';
import 'package:cryptowl/src/crypto/protected_value.dart';

class CrockfordBase32 {
  static String encodeProtected(ProtectedValue key) {
    final str = base32.encode(key.binaryValue, encoding: Encoding.crockford);
    return _addHyphens(str, groupSize: 5);
  }

  static String encode(Uint8List key) {
    final str = base32.encode(key, encoding: Encoding.crockford);
    return _addHyphens(str, groupSize: 5);
  }

  static ProtectedValue decode(String keyStr) {
    final base32Str = keyStr.replaceAll('-', '');
    final key = base32.decode(base32Str, encoding: Encoding.crockford);
    return ProtectedValue.fromBinary(key);
  }

  static String _addHyphens(String input, {int groupSize = 4}) {
    final buffer = StringBuffer();
    int count = 0;

    for (int i = 0; i < input.length; i++) {
      buffer.write(input[i]);
      count++;

      if (count == groupSize && i != input.length - 1) {
        buffer.write('-');
        count = 0;
      }
    }

    return buffer.toString();
  }
}
