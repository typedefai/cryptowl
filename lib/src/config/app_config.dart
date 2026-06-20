import 'package:freezed_annotation/freezed_annotation.dart';

part 'app_config.g.dart';

@JsonSerializable()
class AppConfig {
  final String version;
  final ConfigData data;
  final String hash;

  AppConfig({
    required this.version,
    required this.data,
    required this.hash,
  });

  factory AppConfig.fromJson(Map<String, dynamic> json) =>
      _$AppConfigFromJson(json);

  Map<String, dynamic> toJson() => _$AppConfigToJson(this);
}

@JsonSerializable()
class ConfigData {
  final String instanceId;
  final DateTime createdAt;
  final DateTime updatedAt;
  final KdfParams kdf;
  final String transformSeed;
  final String masterSeed;
  final String encryptedKey;
  final String authTag;
  final String nonce;

  ConfigData({
    required this.instanceId,
    required this.createdAt,
    required this.updatedAt,
    required this.kdf,
    required this.transformSeed,
    required this.masterSeed,
    required this.encryptedKey,
    required this.authTag,
    required this.nonce,
  });

  factory ConfigData.fromJson(Map<String, dynamic> json) =>
      _$ConfigDataFromJson(json);

  Map<String, dynamic> toJson() => _$ConfigDataToJson(this);
}

@JsonSerializable()
class KdfParams {
  final String algorithm;
  final int m;
  final int t;
  final int p;

  KdfParams({
    required this.algorithm,
    required this.m,
    required this.t,
    required this.p,
  });

  factory KdfParams.fromJson(Map<String, dynamic> json) =>
      _$KdfParamsFromJson(json);

  Map<String, dynamic> toJson() => _$KdfParamsToJson(this);
}
