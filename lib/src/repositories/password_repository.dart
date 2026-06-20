import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:cryptowl/src/database/database.dart';
import 'package:drift/drift.dart';
import 'package:logging/logging.dart';
import 'package:uuid/uuid.dart';

import '../domain/password.dart';
import 'base_repository.dart';

class PasswordRepository extends SqlcipherRepository {
  final logger = Logger("PasswordRepository");
  final uuid = Uuid();

  PasswordRepository(super.ref);

  Future<List<PasswordBasic>> list() async {
    final db = await requireDb();
    final items = await db
        .selectPasswords((u) => OrderBy(
            [OrderingTerm(expression: u.createdAt, mode: OrderingMode.asc)]))
        .get();
    return items.map((item) {
      return PasswordBasic(
        id: item.id,
        type: item.type,
        categoryId: item.categoryId,
        title: item.title ?? "",
        expireTime: item.expireTime,
        createdAt: item.createdAt,
        updatedAt: item.updatedAt,
      );
    }).toList();
  }

  Future<TPasswordData> findPasswordById(String passwordId) async {
    final db = await requireDb();
    return (db.tPassword.select()..where((tbl) => tbl.id.equals(passwordId)))
        .getSingle();
  }

  Future<TDataEncryptKeyData> findPasswordEncryptedData(
      String encryptedId) async {
    final db = await requireDb();
    return (db.tDataEncryptKey.select()
          ..where((tbl) => tbl.id.equals(encryptedId)))
        .getSingle();
  }

  Future<List<TPasswordAttributeData>> findPasswordAttributes(
      String passwordId) async {
    final db = await requireDb();
    return (db.tPasswordAttribute.select()
          ..where((tbl) => tbl.passwordId.equals(passwordId)))
        .get();
  }

  Future<bool> delete(String id) async {
    return true;
  }

  Future<void> create(
      TPasswordData passwordEntity,
      TDataEncryptKeyData dekEntity,
      TEncryptedDataData encryptedDataEntity,
      List<TPasswordAttributeCompanion> attributeEntities) async {
    final db = await requireDb();
    return db.transaction(() async {
      logger.info("Saving password: ${passwordEntity.id}");
      await db.into(db.tDataEncryptKey).insert(dekEntity);
      await db.into(db.tEncryptedData).insert(encryptedDataEntity);
      await db.into(db.tPassword).insert(passwordEntity);
      for (var a in attributeEntities) {
        await db.into(db.tPasswordAttribute).insert(a);
      }
    });
  }

  Future<void> update(String id,
      {required String title,
      required ProtectedValue value,
      required String username,
      required String url,
      required String remark}) async {}
}
