import 'dart:convert';

import 'package:cryptowl/src/common/classification.dart';
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

  Future<TDataEncryptKeyData> findDataEncryptKey(String dekId) async {
    final db = await requireDb();
    return (db.tDataEncryptKey.select()
          ..where((tbl) => tbl.id.equals(dekId)))
        .getSingle();
  }

  Future<TEncryptedDataData> findEncryptedData(String encryptedDataId) async {
    final db = await requireDb();
    return (db.tEncryptedData.select()
          ..where((tbl) => tbl.id.equals(encryptedDataId)))
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
      required String remark}) async {
    final db = await requireDb();
    final now = DateTime.now();

    // Update password title
    await (db.tPassword.update()..where((r) => r.id.equals(id))).write(
      TPasswordCompanion(
        title: Value(title),
        updatedAt: Value(now),
      ),
    );

    // Get existing attributes
    final existing = await findPasswordAttributes(id);

    // Update or create username attribute
    final userAttr = existing.where((a) => a.name == ATTR_USER).firstOrNull;
    if (userAttr != null) {
      await (db.tPasswordAttribute.update()
            ..where((r) => r.id.equals(userAttr.id)))
          .write(TPasswordAttributeCompanion(
        value: Value(username),
        updatedAt: Value(now),
      ));
    } else if (username.isNotEmpty) {
      await db.into(db.tPasswordAttribute).insert(TPasswordAttributeCompanion(
        passwordId: Value(id),
        classification: Value(Classification.confidential.value),
        value: Value(username),
        name: Value(ATTR_USER),
        createdAt: Value(now),
        updatedAt: Value(now),
      ));
    }

    // Update or create remark attribute
    final remarkAttr = existing.where((a) => a.name == ATTR_REMARK).firstOrNull;
    if (remarkAttr != null) {
      await (db.tPasswordAttribute.update()
            ..where((r) => r.id.equals(remarkAttr.id)))
          .write(TPasswordAttributeCompanion(
        value: Value(remark),
        updatedAt: Value(now),
      ));
    } else if (remark.isNotEmpty) {
      await db.into(db.tPasswordAttribute).insert(TPasswordAttributeCompanion(
        passwordId: Value(id),
        classification: Value(Classification.confidential.value),
        value: Value(remark),
        name: Value(ATTR_REMARK),
        createdAt: Value(now),
        updatedAt: Value(now),
      ));
    }

    // Update password value for Confidential passwords
    final passwordAttr =
        existing.where((a) => a.name == "password").firstOrNull;
    if (passwordAttr != null) {
      await (db.tPasswordAttribute.update()
            ..where((r) => r.id.equals(passwordAttr.id)))
          .write(TPasswordAttributeCompanion(
        value: Value(utf8.decode(value.binaryValue)),
        updatedAt: Value(now),
      ));
    }
    // Note: Secret/Top Secret password value update requires re-encryption
    // with the DEK, which is handled by PasswordService, not here.
  }
}
