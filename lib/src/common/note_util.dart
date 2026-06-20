import 'dart:convert';

import 'package:crypto/crypto.dart';

class NoteUtil {
  static String createAbstract(String input, [int maxLength = 60]) {
    final lines =
        input.split('\n').where((line) => line.trim().isNotEmpty).toList();
    final result = lines.take(2).join(' ').trim();

    return result.length > maxLength
        ? result.substring(0, maxLength) +
            (result.length > maxLength ? '...' : '')
        : result;
  }

  static String checksum(String content) {
    return sha256.convert(utf8.encode(content)).toString();
  }
}
