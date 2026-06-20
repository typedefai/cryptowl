import 'dart:convert';

import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/common/string.dart';
import 'package:cryptowl/src/crypto/random_util.dart';

import '../crypto/protected_value.dart';
import '../database/database.dart';

class PasswordBasic {
  String id;
  int type;
  int categoryId;
  String title;
  Classification classification;
  DateTime? expireTime;
  DateTime createdAt;
  DateTime updatedAt;

  PasswordBasic({
    required this.id,
    required this.type,
    required this.categoryId,
    required this.title,
    this.classification = Classification.secret,
    this.expireTime,
    required this.createdAt,
    required this.updatedAt,
  });
}

const ATTR_USER = "user";
const ATTR_REMARK = "remark";

class PasswordAttribute {
  String name;
  bool isProtected;
  ProtectedValue value;

  PasswordAttribute(
      {required this.name, required this.isProtected, required this.value});

  static PasswordAttribute fromEntity(TPasswordAttributeData entity) {
    final isProtected = Classification.parse(entity.classification) !=
        Classification.confidential;
    ProtectedValue value;
    if (isProtected || entity.value == null) {
      // Value needs decryption or is null — caller must provide decrypted value
      value = ProtectedValue.fromString("");
    } else {
      value = ProtectedValue.fromString(entity.value!);
    }
    return PasswordAttribute(
        name: entity.name, isProtected: isProtected, value: value);
  }

  String plainValue() {
    return utf8.decode(value.binaryValue);
  }
}

class Password {
  String id;
  int type;
  Classification classification;
  int categoryId;
  String? title;
  DateTime? expireTime;
  ProtectedValue value;
  List<PasswordAttribute> attributes;
  DateTime createdAt;
  DateTime updatedAt;

  Password(
      {required this.id,
      required this.type,
      required this.classification,
      required this.title,
      this.expireTime,
      required this.value,
      required this.attributes,
      required this.categoryId,
      required this.createdAt,
      required this.updatedAt});

  bool get isEncrypted =>
      classification == Classification.secret ||
      classification == Classification.topSecret;

  bool get isTopSecret => classification == Classification.topSecret;

  PasswordAttribute? getUser() {
    return attributes.where((a) => a.name == ATTR_USER).firstOrNull;
  }

  PasswordAttribute? getRemark() {
    return attributes.where((a) => a.name == ATTR_REMARK).firstOrNull;
  }

  factory Password.create(String title, ProtectedValue password,
      Classification classification, String? user, String? remark) {
    final now = DateTime.now();
    return Password(
        id: RandomUtil.generateUUID(),
        type: 0,
        classification: classification,
        title: title,
        value: password,
        categoryId: 0,
        attributes: [
          // Attributes are always Confidential for searchability
          if (user.isNotBlank)
            PasswordAttribute(
                name: ATTR_USER,
                isProtected: false,
                value: ProtectedValue.fromString(user!)),
          if (remark.isNotBlank)
            PasswordAttribute(
                name: ATTR_REMARK,
                isProtected: false,
                value: ProtectedValue.fromString(remark!)),
        ],
        createdAt: now,
        updatedAt: now);
  }

  static Password fromEntity(
      TPasswordData e, List<TPasswordAttributeData> attributes,
      {ProtectedValue? decryptedValue}) {
    return Password(
        id: e.id,
        type: e.type,
        title: e.title,
        classification: Classification.parse(e.classification),
        value: decryptedValue ?? ProtectedValue.fromString(""),
        categoryId: e.categoryId,
        createdAt: e.createdAt,
        attributes:
            attributes.map((a) => PasswordAttribute.fromEntity(a)).toList(),
        updatedAt: e.updatedAt);
  }
}
