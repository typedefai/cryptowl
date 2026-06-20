import 'dart:typed_data';

import 'package:crypto/crypto.dart' as c;
import 'package:cryptography/cryptography.dart' as cg;
import 'package:cryptowl/src/crypto/protected_value.dart';

abstract class Hmac {
  Future<Uint8List> calculateMac(ProtectedValue message, ProtectedValue key);
}

class CryptoHmac extends Hmac {
  @override
  Future<Uint8List> calculateMac(
      ProtectedValue message, ProtectedValue key) async {
    final hmac = c.Hmac(c.sha256, key.binaryValue);
    return hmac.convert(message.binaryValue).bytes as Uint8List;
  }
}

class CryptographyHmac extends Hmac {
  @override
  Future<Uint8List> calculateMac(
      ProtectedValue message, ProtectedValue key) async {
    final secretKey = cg.SecretKey(key.binaryValue);

    final hmac = cg.Hmac.sha256();
    final mac =
        await hmac.calculateMac(message.binaryValue, secretKey: secretKey);
    return mac.bytes as Uint8List;
  }
}
