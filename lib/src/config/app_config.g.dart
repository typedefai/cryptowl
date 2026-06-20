// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'app_config.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AppConfig _$AppConfigFromJson(Map<String, dynamic> json) => AppConfig(
  version: json['version'] as String,
  data: ConfigData.fromJson(json['data'] as Map<String, dynamic>),
  hash: json['hash'] as String,
);

Map<String, dynamic> _$AppConfigToJson(AppConfig instance) => <String, dynamic>{
  'version': instance.version,
  'data': instance.data,
  'hash': instance.hash,
};

ConfigData _$ConfigDataFromJson(Map<String, dynamic> json) => ConfigData(
  instanceId: json['instanceId'] as String,
  createdAt: DateTime.parse(json['createdAt'] as String),
  updatedAt: DateTime.parse(json['updatedAt'] as String),
  kdf: KdfParams.fromJson(json['kdf'] as Map<String, dynamic>),
  transformSeed: json['transformSeed'] as String,
  masterSeed: json['masterSeed'] as String,
  encryptedKey: json['encryptedKey'] as String,
  authTag: json['authTag'] as String,
  nonce: json['nonce'] as String,
  secondaryKeySalt: json['secondaryKeySalt'] as String?,
);

Map<String, dynamic> _$ConfigDataToJson(
  ConfigData instance,
) => <String, dynamic>{
  'instanceId': instance.instanceId,
  'createdAt': instance.createdAt.toIso8601String(),
  'updatedAt': instance.updatedAt.toIso8601String(),
  'kdf': instance.kdf,
  'transformSeed': instance.transformSeed,
  'masterSeed': instance.masterSeed,
  'encryptedKey': instance.encryptedKey,
  'authTag': instance.authTag,
  'nonce': instance.nonce,
  if (instance.secondaryKeySalt case final value?) 'secondaryKeySalt': value,
};

KdfParams _$KdfParamsFromJson(Map<String, dynamic> json) => KdfParams(
  algorithm: json['algorithm'] as String,
  m: (json['m'] as num).toInt(),
  t: (json['t'] as num).toInt(),
  p: (json['p'] as num).toInt(),
);

Map<String, dynamic> _$KdfParamsToJson(KdfParams instance) => <String, dynamic>{
  'algorithm': instance.algorithm,
  'm': instance.m,
  't': instance.t,
  'p': instance.p,
};
