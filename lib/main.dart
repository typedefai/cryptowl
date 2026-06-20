import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:logging/logging.dart';
import 'package:native_sqlcipher/native_sqlcipher.dart';
import 'package:sqlite3/open.dart';

import 'src/app.dart';

final logger = Logger('Cryptowl');

void main() async {
  Logger.root.level = Level.ALL;
  Logger.root.onRecord.listen((record) {
    // ignore: avoid_print
    print('${record.level.name}: ${record.time}: ${record.message}');
  });
  WidgetsFlutterBinding.ensureInitialized();
  open.overrideForAll(openSqlcipher);

  runApp(
    ProviderScope(
      child: CryptowlApp(),
    ),
  );
}
