import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:convert/convert.dart';
import 'package:cryptowl/src/crypto/argon2.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:native_argon2/native_argon2.dart' hide NativeArgon2;

void main() {
  setUp(() {
    String testLibPath;

    if (Platform.isMacOS) {
      testLibPath = 'deps/native_argon2/src/build/libnative_argon2.dylib';
    } else if (Platform.isLinux) {
      testLibPath = 'deps/native_argon2/src/build/libnative_argon2.so';
    } else if (Platform.isWindows) {
      testLibPath = 'deps/native_argon2/src/build/libnative_argon2.dll';
    } else {
      throw UnsupportedError(
        'Tests on ${Platform.operatingSystem} not supported',
      );
    }
    Argon2LibraryLoader.instance.configure(libraryPath: testLibPath);
  });

  test('should return argon2 arguments from encoded string', () async {
    final args = Argon2Arguments.parse(
        "\$argon2id\$v=19\$m=36,t=2,p=2\$ZHJAcmlndXouY29t\$hKxpGm8mI6BUUB9bofN944EXwzGI1dr2HM8H0+wK40Y");
    expect(args.variant, Argon2Variant.argon2id);
    expect(args.version, 19);
    expect(args.memory, 36);
    expect(args.parallelism, 2);
    expect(args.salt, utf8.encode("dr@riguz.com"));
  });

  test('should return argon2 derived key when use argon2d', () async {
    final nativeArgon2 = NativeArgon2();
    final salt = hex.decode(
            "3f09ea13ceffb8e867a4af3ab17854f9f5f152591653c737a8962b94356e2c0f")
        as Uint8List;
    final password = hex.decode(
            "bfa11b4e4376cf1b17088a3de375f1df6a9c4cb3eb36f3ce2416b10481eb619f")
        as Uint8List;
    //  argon2: seed=kef.seed, version=19, rounds=2, memory=1024, parallelism=2
    final key = await nativeArgon2.deriveKey(
      Argon2Arguments(
          password, salt, 1024, 2, 32, 2, Argon2Variant.argon2d, 19),
    );
    String encoded = hex.encode(key);
    expect(
        encoded,
        equals(
            "104e9ba7b6b4479eec1a8fe3f9ca285fd10e0f33435fcabd8edf3e16380a98c7"));
    expect(
        key,
        equals(hex.decode(
            "104e9ba7b6b4479eec1a8fe3f9ca285fd10e0f33435fcabd8edf3e16380a98c7")));
  });
}
