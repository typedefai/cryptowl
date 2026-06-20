import 'dart:io';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as path;

import '../common/path_util.dart';

const dictAssets = [
  'assets/dict/jieba.dict.utf8',
  'assets/dict/hmm_model.utf8',
  'assets/dict/user.dict.utf8',
  'assets/dict/idf.utf8',
  'assets/dict/stop_words.utf8',
];

class FileService {
  Future<void> _copyAssetsToDocDir(List<String> assetPaths) async {
    final docDir = await PathUtil.getLocalPath("dict");

    final dictDir = Directory(docDir);
    if (!(await dictDir.exists())) {
      await dictDir.create(recursive: true);
    }
    for (final asset in assetPaths) {
      final data = await rootBundle.load(asset);
      final filename = asset.split('/').last;
      final file = File('$docDir/$filename');
      await file.writeAsBytes(data.buffer.asUint8List(), flush: true);
    }
  }

  Future<void> copyJiebaDicts() async {
    await _copyAssetsToDocDir(dictAssets);
  }

  Future<List<String>> getSqlcipherInstances() async {
    final documentDir = Directory(await PathUtil.getLocalPath("."));
    return await documentDir
        .list()
        .where(
            (e) => e is File && path.extension(e.path).toLowerCase() == '.enc')
        .map((db) => path.basename(db.path))
        .toList();
  }

  Future<File> getConfigFile(String instanceId) async {
    final config = "${instanceId}.cfg";
    return File(await PathUtil.getLocalPath(config));
  }

  Future<void> writeFile(String content, String fileName) async {
    final file = File(await PathUtil.getLocalPath(fileName));
    await file.writeAsString(content, flush: true);
  }
}
