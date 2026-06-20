import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:flutter/foundation.dart';

import '../database/database.dart';

@immutable
class Session {
  final SqliteDb sqliteDb;
  final ProtectedValue symmetricKey;

  const Session(this.sqliteDb, this.symmetricKey);
}
