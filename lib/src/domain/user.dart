import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:flutter/foundation.dart';

import '../database/database.dart';

@immutable
class Session {
  final SqliteDb sqliteDb;
  final ProtectedValue symmetricKey;
  final ProtectedValue? secondaryKey;

  const Session(this.sqliteDb, this.symmetricKey, {this.secondaryKey});

  Session withSecondaryKey(ProtectedValue key) {
    return Session(sqliteDb, symmetricKey, secondaryKey: key);
  }

  bool get hasSecondaryKey => secondaryKey != null;
}
