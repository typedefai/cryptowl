class IncorrectPasswordException implements Exception {}

class CorruptedConfigException implements Exception {
  String cause;
  CorruptedConfigException(this.cause);
}

class InvalidKeyException implements Exception {
  String cause;
  InvalidKeyException(this.cause);
}

class InternalException implements Exception {
  String cause;
  InternalException(this.cause);
}
