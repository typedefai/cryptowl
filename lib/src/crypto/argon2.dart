import 'dart:convert';
import 'dart:ffi';
import 'dart:typed_data';

import 'package:ffi/ffi.dart';
import 'package:native_argon2/native_argon2.dart' as f;

enum Argon2Variant { argon2i, argon2d, argon2id }

class Argon2Arguments {
  Argon2Arguments(this.key, this.salt, this.memory, this.iterations,
      this.length, this.parallelism, this.variant, this.version);

  final Uint8List key;
  final Uint8List salt;
  final int memory;
  final int iterations;
  final int length;
  final int parallelism;
  final Argon2Variant variant;
  final int version;

  @override
  String toString() {
    String argon2Type = '';
    switch (variant) {
      case Argon2Variant.argon2i:
        argon2Type = 'argon2i';
      case Argon2Variant.argon2d:
        argon2Type = 'argon2d';
      case Argon2Variant.argon2id:
        argon2Type = 'argon2id';
    }
    final String version = 'v=${this.version}';
    final String memory = 'm=${this.memory}';
    final String iterations = 't=${this.iterations}';
    final String parallelism = 'p=${this.parallelism}';
    final String saltString = base64.encode(salt).replaceAll('=', '');
    final String keyString = base64.encode(key).replaceAll('=', '');

    return '\$$argon2Type\$$version\$$memory,$iterations,$parallelism\$$saltString\$$keyString';
  }

  static String _addPadding(String encodedString) {
    if (encodedString.length % 4 != 0) {
      final int paddingLength = 4 - (encodedString.length % 4);
      final String padding = '=' * paddingLength;
      return encodedString + padding;
    } else {
      return encodedString;
    }
  }

  static Argon2Arguments parse(String args) {
    final regex = RegExp(
        r'^\$argon2(i|d|id)\$v=(\d+)\$m=(\d+),t=(\d+),p=(\d+)\$([A-Za-z0-9+/=]+)\$([A-Za-z0-9+/=]+)$');

    final Match? match = regex.firstMatch(args);

    if (match == null) {
      throw const FormatException('Invalid argon2 hash string');
    }
    final String variantName = match.group(1)!;
    final int version = int.parse(match.group(2)!);
    final int memory = int.parse(match.group(3)!);
    final int iterations = int.parse(match.group(4)!);
    final int parallelism = int.parse(match.group(5)!);
    final String salt = _addPadding(match.group(6)!);
    final String hash = _addPadding(match.group(7)!);

    final key = base64Decode(hash);
    Argon2Variant variant;
    if (variantName == 'i') {
      variant = Argon2Variant.argon2i;
    } else if (variantName == 'd') {
      variant = Argon2Variant.argon2d;
    } else {
      variant = Argon2Variant.argon2id;
    }
    return Argon2Arguments(key, base64Decode(salt), memory, iterations,
        key.length, parallelism, variant, version);
  }

  @override
  bool operator ==(dynamic other) =>
      other is Argon2Arguments && other.toString() == toString();

  @override
  int get hashCode => toString().hashCode;
}

abstract class Argon2 {
  Future<Uint8List> deriveKey(Argon2Arguments args);
}

class NativeArgon2 extends Argon2 {
  final nativeArgon2 = f.NativeArgon2();
  Future<Uint8List> deriveKey(Argon2Arguments args) async {
    final int hashLen = args.length;
    final Pointer<Uint8> hashStr = malloc.allocate<Uint8>(hashLen);

    var params = f.Argon2RawParams(
      tCost: args.iterations,
      mCost: args.memory,
      parallelism: args.parallelism,
      password: args.key,
      salt: args.salt,
      hashLen: args.length,
      hash: hashStr.cast<Void>(),
    );
    final int result;
    switch (args.variant) {
      case Argon2Variant.argon2i:
        result = nativeArgon2.argon2iHashRaw(params);
        break;
      case Argon2Variant.argon2d:
        result = nativeArgon2.argon2dHashRaw(params);
        break;
      case Argon2Variant.argon2id:
        result = nativeArgon2.argon2idHashRaw(params);
        break;
    }
    if (result != 0) {
      malloc.free(hashStr);
      throw Exception('Argon2 hashing failed with error code: $result');
    }
    final uint8list = Uint8List.fromList(
      hashStr.cast<Uint8>().asTypedList(hashLen),
    );
    malloc.free(hashStr);
    return uint8list;
  }
}
