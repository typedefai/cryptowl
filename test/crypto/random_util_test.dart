import 'package:cryptowl/src/crypto/random_util.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('should return uuid', () async {
    final str = RandomUtil.generateUUID();

    expect(str, hasLength(36));
  });

  test('should return random bytes with given length', () async {
    final nonce = RandomUtil.generateSecureBytes(32);

    expect(nonce, hasLength(32));
  });
}
