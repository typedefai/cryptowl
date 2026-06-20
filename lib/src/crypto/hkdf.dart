import 'dart:typed_data';

import 'package:crypto/crypto.dart' as c;
import 'package:cryptography/cryptography.dart' as cg;
import 'package:cryptowl/src/crypto/protected_value.dart';

abstract class Hkdf {
  Future<ProtectedValue> deriveKey({
    required ProtectedValue ikm,
    Uint8List? salt,
    Uint8List? info,
    int outputLength = 64,
  });
}

class CryptoHkdf extends Hkdf {
  @override
  Future<ProtectedValue> deriveKey(
      {required ProtectedValue ikm,
      Uint8List? salt,
      Uint8List? info,
      int outputLength = 64}) async {
    final _salt = salt ?? Uint8List(0);
    final hmac = c.Hmac(c.sha256, _salt);
    final prk = hmac.convert(ikm.binaryValue).bytes;

    final t = <Uint8List>[];
    final okm = <int>[];
    final infoBytes = info ?? Uint8List(0);
    int i = 1;
    while (okm.length < outputLength) {
      // T(i) = HMAC-Hash(PRK, T(i-1) | info | i)
      final input = Uint8List.fromList(
          (t.isEmpty ? Uint8List(0) : t.last) + infoBytes + [i]);
      final hmacExpand = c.Hmac(c.sha256, prk);
      t.add(Uint8List.fromList(hmacExpand.convert(input).bytes));
      okm.addAll(t.last);
      i++;
    }

    return ProtectedValue.fromBinary(
        Uint8List.fromList(okm.sublist(0, outputLength)));
  }
}

class CryptoGraphyHkdf extends Hkdf {
  @override
  Future<ProtectedValue> deriveKey(
      {required ProtectedValue ikm,
      Uint8List? salt,
      Uint8List? info,
      int outputLength = 64}) async {
    final algorithm = cg.Hkdf(
      hmac: cg.Hmac.sha256(),
      outputLength: outputLength,
    );
    final secretKey = cg.SecretKey(ikm.binaryValue);
    final output = await algorithm.deriveKey(
      secretKey: secretKey,
      nonce: salt ?? Uint8List(0),
      info: info ?? Uint8List(0),
    );

    return ProtectedValue.fromBinary(Uint8List.fromList(output.bytes));
  }
}
