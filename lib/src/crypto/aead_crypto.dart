import 'dart:typed_data';

import 'package:cryptography/cryptography.dart' as cg;
import 'package:cryptowl/src/crypto/protected_value.dart';

enum AlgorithmType {
  aes256Gcm("2ad0737c-01a8-4d74-998f-9dfe855171fb", 32, 12),
  chacha20Poly1305("824b7f4a-3882-4194-8936-600d7d493ddb", 32, 12),
  xchacha20Poly1305("8c378b87-5d19-4ef4-9848-561c533d9e04", 32, 24);

  final String id;
  final int keySize;
  final int nonceSize;

  const AlgorithmType(this.id, this.keySize, this.nonceSize);

  factory AlgorithmType.parse(String id) {
    return AlgorithmType.values.firstWhere((element) => element.id == id);
  }
}

class AuthEncryptedResult {
  final Uint8List cipherData;
  final Uint8List authTag;

  AuthEncryptedResult(this.cipherData, this.authTag);
}

abstract class AeadCrypto {
  AlgorithmType getType();
  Future<AuthEncryptedResult> encrypt(
      ProtectedValue data, ProtectedValue key, Uint8List nonce, Uint8List? aad);
  Future<ProtectedValue> decrypt(AuthEncryptedResult encryptedData,
      ProtectedValue key, Uint8List nonce, Uint8List? aad);
}

abstract class _BaseAeadCrypto implements AeadCrypto {
  final AlgorithmType type;
  final cg.Cipher algorithm;

  _BaseAeadCrypto(this.algorithm, this.type);

  @override
  AlgorithmType getType() {
    return type;
  }

  @override
  Future<AuthEncryptedResult> encrypt(
    ProtectedValue data,
    ProtectedValue key,
    Uint8List nonce,
    Uint8List? aad,
  ) async {
    final secretKey = cg.SecretKey(key.binaryValue);
    final secretBox = await algorithm.encrypt(
      data.binaryValue,
      secretKey: secretKey,
      nonce: nonce,
      aad: aad ?? Uint8List(0),
    );
    return AuthEncryptedResult(
      secretBox.cipherText as Uint8List,
      secretBox.mac.bytes as Uint8List,
    );
  }

  @override
  Future<ProtectedValue> decrypt(
    AuthEncryptedResult encryptedData,
    ProtectedValue key,
    Uint8List nonce,
    Uint8List? aad,
  ) async {
    final secretKey = cg.SecretKey(key.binaryValue);
    final secretBox = cg.SecretBox(
      encryptedData.cipherData,
      nonce: nonce,
      mac: cg.Mac(encryptedData.authTag),
    );
    final clearText = await algorithm.decrypt(
      secretBox,
      secretKey: secretKey,
      aad: aad ?? Uint8List(0),
    );
    return ProtectedValue.fromBinary(clearText as Uint8List);
  }
}

class CryptographyAesGcm extends _BaseAeadCrypto {
  CryptographyAesGcm()
      : super(cg.AesGcm.with256bits(), AlgorithmType.aes256Gcm);
}

class CryptographyChaCha20 extends _BaseAeadCrypto {
  CryptographyChaCha20()
      : super(cg.Chacha20.poly1305Aead(), AlgorithmType.chacha20Poly1305);
}

class CryptographyXChaCha20 extends _BaseAeadCrypto {
  CryptographyXChaCha20()
      : super(cg.Xchacha20.poly1305Aead(), AlgorithmType.xchacha20Poly1305);
}
