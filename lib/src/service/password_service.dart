import 'dart:convert';
import 'dart:typed_data';

import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/crypto/aead_crypto.dart';
import 'package:cryptowl/src/crypto/crockford_base32.dart';
import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:cryptowl/src/database/database.dart';
import 'package:cryptowl/src/domain/password.dart';
import 'package:cryptowl/src/repositories/password_repository.dart';
import 'package:cryptowl/src/service/kdf_service.dart';
import 'package:drift/drift.dart';

const DEFAULT = 0;

class PasswordService {
  final KdfService kdfService;
  final PasswordRepository passwordRepository;
  final aesGcm = CryptographyAesGcm();
  final xchacha20 = CryptographyXChaCha20();

  PasswordService(this.kdfService, this.passwordRepository);

  Future<void> createPassword(
      Password password, ProtectedValue kek, ProtectedValue? topSecretKek) async {
    final now = DateTime.now();

    if (password.classification == Classification.confidential) {
      final passwordId = await kdfService.generateUUID();
      // Confidential: no per-entry DEK, store password as a confidential attribute
      final passwordEntity = TPasswordData(
          id: passwordId,
          type: DEFAULT,
          title: password.title,
          categoryId: DEFAULT,
          classification: Classification.confidential.value,
          encryptedDataId: "", // No encrypted data for confidential
          createdAt: now,
          updatedAt: now);

      final attributes = <TPasswordAttributeCompanion>[
        // Store the password value as a confidential attribute (plaintext in DB)
        TPasswordAttributeCompanion(
            passwordId: Value(passwordId),
            classification: Value(Classification.confidential.value),
            value: Value(utf8.decode(password.value.binaryValue)),
            name: Value("password"),
            createdAt: Value(now),
            updatedAt: Value(now)),
        // Other attributes
        ...password.attributes.map((a) => TPasswordAttributeCompanion(
            passwordId: Value(passwordId),
            classification: Value(Classification.confidential.value),
            value: Value(utf8.decode(a.value.binaryValue)),
            name: Value(a.name),
            createdAt: Value(now),
            updatedAt: Value(now))),
      ];

      return passwordRepository.create(
          passwordEntity,
          TDataEncryptKeyData(
              id: "", data: "", nonce: "", authTag: "",
              createdAt: now, updatedAt: now),
          TEncryptedDataData(
              id: "", dekId: "", content: Uint8List(0),
              algorithmId: "", authTag: "", nonce: "",
              createdAt: now, updatedAt: now),
          attributes);
    }

    // Secret or Top Secret: use per-entry DEK
    final isTopSecret = password.classification == Classification.topSecret;
    final crypto = isTopSecret ? xchacha20 : aesGcm;
    final algorithmId = crypto.type.id;

    // Select the right KEK
    final activeKek = isTopSecret ? topSecretKek : kek;
    if (activeKek == null) {
      throw Exception("Top Secret KEK not available. Unlock Top Secret first.");
    }

    // Generate DEK and encrypt it with the KEK
    final dek =
        await kdfService.generateRandomBytes(length: crypto.type.keySize);
    final dekNonce = await kdfService.generateRandomBytes(length: 12);
    final dekId = await kdfService.generateUUID();
    final encryptedDek = await aesGcm.encrypt(
        dek, activeKek, dekNonce.binaryValue, utf8.encode(dekId));

    final keyEntity = TDataEncryptKeyData(
        id: dekId,
        data: CrockfordBase32.encode(encryptedDek.cipherData),
        nonce: CrockfordBase32.encode(dekNonce.binaryValue),
        authTag: CrockfordBase32.encode(encryptedDek.authTag),
        createdAt: now,
        updatedAt: now);

    // Encrypt the password value with the DEK
    final encryptedDataId = await kdfService.generateUUID();
    final passwordId = await kdfService.generateUUID();
    final dataNonce =
        await kdfService.generateRandomBytes(length: crypto.type.nonceSize);
    final encryptedData = await crypto.encrypt(password.value, dek,
        dataNonce.binaryValue, utf8.encode(encryptedDataId));
    final encryptedDataEntity = TEncryptedDataData(
        id: encryptedDataId,
        dekId: dekId,
        content: encryptedData.cipherData,
        algorithmId: algorithmId,
        authTag: CrockfordBase32.encode(encryptedData.authTag),
        nonce: CrockfordBase32.encode(dataNonce.binaryValue),
        createdAt: now,
        updatedAt: now);

    final passwordEntity = TPasswordData(
        id: passwordId,
        type: DEFAULT,
        title: password.title,
        categoryId: DEFAULT,
        classification: password.classification.value,
        encryptedDataId: encryptedDataId,
        createdAt: now,
        updatedAt: now);

    // Attributes are always Confidential for searchability
    final attributes = password.attributes
        .map((a) => TPasswordAttributeCompanion(
            passwordId: Value(passwordId),
            classification: Value(Classification.confidential.value),
            value: Value(utf8.decode(a.value.binaryValue)),
            name: Value(a.name),
            createdAt: Value(now),
            updatedAt: Value(now)))
        .toList();

    return passwordRepository.create(
        passwordEntity, keyEntity, encryptedDataEntity, attributes);
  }

  Future<Password> getPasswordDetail(String id, ProtectedValue kek,
      {ProtectedValue? topSecretKek}) async {
    final passwordEntity = await passwordRepository.findPasswordById(id);
    final attributes = await passwordRepository.findPasswordAttributes(id);
    final classification = Classification.parse(passwordEntity.classification);

    ProtectedValue? decryptedValue;

    if (classification == Classification.confidential) {
      // Password stored as a confidential attribute named "password"
      final passwordAttr = attributes.where((a) => a.name == "password").firstOrNull;
      if (passwordAttr != null && passwordAttr.value != null) {
        decryptedValue = ProtectedValue.fromString(passwordAttr.value!);
      }
    } else if (classification == Classification.secret ||
        classification == Classification.topSecret) {
      // Decrypt the password value using the DEK
      final isTopSecret = classification == Classification.topSecret;
      final activeKek = isTopSecret ? topSecretKek : kek;
      if (activeKek == null) {
        // Can't decrypt without the right KEK
        return Password.fromEntity(passwordEntity, attributes);
      }

      final encryptedData =
          await passwordRepository.findEncryptedData(passwordEntity.encryptedDataId);
      final dekEntity =
          await passwordRepository.findDataEncryptKey(encryptedData.dekId);

      // Decrypt the DEK
      final dekCipherData = CrockfordBase32.decode(dekEntity.data);
      final dekNonce = CrockfordBase32.decode(dekEntity.nonce);
      final dekAuthTag = CrockfordBase32.decode(dekEntity.authTag);
      final decryptedDek = await aesGcm.decrypt(
          AuthEncryptedResult(dekCipherData.binaryValue, dekAuthTag.binaryValue),
          activeKek,
          dekNonce.binaryValue,
          utf8.encode(dekEntity.id));

      // Decrypt the password value
      final algorithmType = AlgorithmType.parse(encryptedData.algorithmId);
      final contentNonce = CrockfordBase32.decode(encryptedData.nonce);
      final contentAuthTag = CrockfordBase32.decode(encryptedData.authTag);
      final crypto = isTopSecret ? xchacha20 : aesGcm;
      decryptedValue = await crypto.decrypt(
          AuthEncryptedResult(
              encryptedData.content, contentAuthTag.binaryValue),
          decryptedDek,
          contentNonce.binaryValue,
          utf8.encode(encryptedData.id));
    }

    return Password.fromEntity(passwordEntity, attributes,
        decryptedValue: decryptedValue);
  }

  Future<void> updatePassword(String id, String title, ProtectedValue newValue,
      String username, String remark, ProtectedValue kek,
      {ProtectedValue? topSecretKek}) async {
    final passwordEntity = await passwordRepository.findPasswordById(id);
    final classification = Classification.parse(passwordEntity.classification);
    final now = DateTime.now();

    // Update title and attributes (always Confidential)
    await passwordRepository.update(id,
        title: title,
        value: newValue,
        username: username,
        url: '',
        remark: remark);

    if (classification == Classification.confidential) {
      // Password value already updated as plaintext attribute in repository
      return;
    }

    // Secret/Top Secret: re-encrypt the password value with the existing DEK
    final isTopSecret = classification == Classification.topSecret;
    final activeKek = isTopSecret ? topSecretKek : kek;
    if (activeKek == null) {
      throw Exception('KEK not available for encryption');
    }

    final crypto = isTopSecret ? xchacha20 : aesGcm;

    // Get the existing encrypted data and DEK
    final encryptedData = await passwordRepository
        .findEncryptedData(passwordEntity.encryptedDataId);
    final dekEntity =
        await passwordRepository.findDataEncryptKey(encryptedData.dekId);

    // Decrypt the DEK
    final dekCipherData = CrockfordBase32.decode(dekEntity.data);
    final dekNonceData = CrockfordBase32.decode(dekEntity.nonce);
    final dekAuthTag = CrockfordBase32.decode(dekEntity.authTag);
    final decryptedDek = await aesGcm.decrypt(
        AuthEncryptedResult(
            dekCipherData.binaryValue, dekAuthTag.binaryValue),
        activeKek,
        dekNonceData.binaryValue,
        utf8.encode(dekEntity.id));

    // Re-encrypt the new password value with the DEK
    final dataNonce =
        await kdfService.generateRandomBytes(length: crypto.type.nonceSize);
    final encryptedValue = await crypto.encrypt(newValue, decryptedDek,
        dataNonce.binaryValue, utf8.encode(encryptedData.id));

    // Update the encrypted data in the database
    final db = await passwordRepository.requireDb();
    await (db.tEncryptedData.update()
          ..where((r) => r.id.equals(encryptedData.id)))
        .write(TEncryptedDataCompanion(
      content: Value(encryptedValue.cipherData),
      authTag: Value(CrockfordBase32.encode(encryptedValue.authTag)),
      nonce: Value(CrockfordBase32.encode(dataNonce.binaryValue)),
      updatedAt: Value(now),
    ));
  }
}
