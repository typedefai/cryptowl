import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../database/database.dart';
import '../providers/credentials.dart';

abstract class SqlcipherRepository {
  final Ref ref;

  SqlcipherRepository(this.ref);

  Future<SqliteDb> requireDb() async {
    final session = await ref.read(asyncLoginProvider.future);
    if (session == null) {
      throw Exception("No active sqlcipher opened!");
    }
    return session.sqliteDb;
  }
}
