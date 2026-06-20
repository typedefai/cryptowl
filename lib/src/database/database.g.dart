// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'database.dart';

// ignore_for_file: type=lint
class Categories extends Table with TableInfo<Categories, CategoryEntity> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  Categories(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL PRIMARY KEY AUTOINCREMENT',
  );
  static const VerificationMeta _nameMeta = const VerificationMeta('name');
  late final GeneratedColumn<String> name = GeneratedColumn<String>(
    'name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _accessLevelMeta = const VerificationMeta(
    'accessLevel',
  );
  late final GeneratedColumn<int> accessLevel = GeneratedColumn<int>(
    'access_level',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _createTimeMeta = const VerificationMeta(
    'createTime',
  );
  late final GeneratedColumn<String> createTime = GeneratedColumn<String>(
    'create_time',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _lastUpdateTimeMeta = const VerificationMeta(
    'lastUpdateTime',
  );
  late final GeneratedColumn<String> lastUpdateTime = GeneratedColumn<String>(
    'last_update_time',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    name,
    accessLevel,
    createTime,
    lastUpdateTime,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'categories';
  @override
  VerificationContext validateIntegrity(
    Insertable<CategoryEntity> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('name')) {
      context.handle(
        _nameMeta,
        name.isAcceptableOrUnknown(data['name']!, _nameMeta),
      );
    } else if (isInserting) {
      context.missing(_nameMeta);
    }
    if (data.containsKey('access_level')) {
      context.handle(
        _accessLevelMeta,
        accessLevel.isAcceptableOrUnknown(
          data['access_level']!,
          _accessLevelMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_accessLevelMeta);
    }
    if (data.containsKey('create_time')) {
      context.handle(
        _createTimeMeta,
        createTime.isAcceptableOrUnknown(data['create_time']!, _createTimeMeta),
      );
    } else if (isInserting) {
      context.missing(_createTimeMeta);
    }
    if (data.containsKey('last_update_time')) {
      context.handle(
        _lastUpdateTimeMeta,
        lastUpdateTime.isAcceptableOrUnknown(
          data['last_update_time']!,
          _lastUpdateTimeMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_lastUpdateTimeMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  CategoryEntity map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return CategoryEntity(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      name: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}name'],
      )!,
      accessLevel: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}access_level'],
      )!,
      createTime: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}create_time'],
      )!,
      lastUpdateTime: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}last_update_time'],
      )!,
    );
  }

  @override
  Categories createAlias(String alias) {
    return Categories(attachedDatabase, alias);
  }

  @override
  bool get dontWriteConstraints => true;
}

class CategoryEntity extends DataClass implements Insertable<CategoryEntity> {
  final int id;
  final String name;
  final int accessLevel;
  final String createTime;
  final String lastUpdateTime;
  const CategoryEntity({
    required this.id,
    required this.name,
    required this.accessLevel,
    required this.createTime,
    required this.lastUpdateTime,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['name'] = Variable<String>(name);
    map['access_level'] = Variable<int>(accessLevel);
    map['create_time'] = Variable<String>(createTime);
    map['last_update_time'] = Variable<String>(lastUpdateTime);
    return map;
  }

  CategoriesCompanion toCompanion(bool nullToAbsent) {
    return CategoriesCompanion(
      id: Value(id),
      name: Value(name),
      accessLevel: Value(accessLevel),
      createTime: Value(createTime),
      lastUpdateTime: Value(lastUpdateTime),
    );
  }

  factory CategoryEntity.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return CategoryEntity(
      id: serializer.fromJson<int>(json['id']),
      name: serializer.fromJson<String>(json['name']),
      accessLevel: serializer.fromJson<int>(json['access_level']),
      createTime: serializer.fromJson<String>(json['create_time']),
      lastUpdateTime: serializer.fromJson<String>(json['last_update_time']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'name': serializer.toJson<String>(name),
      'access_level': serializer.toJson<int>(accessLevel),
      'create_time': serializer.toJson<String>(createTime),
      'last_update_time': serializer.toJson<String>(lastUpdateTime),
    };
  }

  CategoryEntity copyWith({
    int? id,
    String? name,
    int? accessLevel,
    String? createTime,
    String? lastUpdateTime,
  }) => CategoryEntity(
    id: id ?? this.id,
    name: name ?? this.name,
    accessLevel: accessLevel ?? this.accessLevel,
    createTime: createTime ?? this.createTime,
    lastUpdateTime: lastUpdateTime ?? this.lastUpdateTime,
  );
  CategoryEntity copyWithCompanion(CategoriesCompanion data) {
    return CategoryEntity(
      id: data.id.present ? data.id.value : this.id,
      name: data.name.present ? data.name.value : this.name,
      accessLevel: data.accessLevel.present
          ? data.accessLevel.value
          : this.accessLevel,
      createTime: data.createTime.present
          ? data.createTime.value
          : this.createTime,
      lastUpdateTime: data.lastUpdateTime.present
          ? data.lastUpdateTime.value
          : this.lastUpdateTime,
    );
  }

  @override
  String toString() {
    return (StringBuffer('CategoryEntity(')
          ..write('id: $id, ')
          ..write('name: $name, ')
          ..write('accessLevel: $accessLevel, ')
          ..write('createTime: $createTime, ')
          ..write('lastUpdateTime: $lastUpdateTime')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(id, name, accessLevel, createTime, lastUpdateTime);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is CategoryEntity &&
          other.id == this.id &&
          other.name == this.name &&
          other.accessLevel == this.accessLevel &&
          other.createTime == this.createTime &&
          other.lastUpdateTime == this.lastUpdateTime);
}

class CategoriesCompanion extends UpdateCompanion<CategoryEntity> {
  final Value<int> id;
  final Value<String> name;
  final Value<int> accessLevel;
  final Value<String> createTime;
  final Value<String> lastUpdateTime;
  const CategoriesCompanion({
    this.id = const Value.absent(),
    this.name = const Value.absent(),
    this.accessLevel = const Value.absent(),
    this.createTime = const Value.absent(),
    this.lastUpdateTime = const Value.absent(),
  });
  CategoriesCompanion.insert({
    this.id = const Value.absent(),
    required String name,
    required int accessLevel,
    required String createTime,
    required String lastUpdateTime,
  }) : name = Value(name),
       accessLevel = Value(accessLevel),
       createTime = Value(createTime),
       lastUpdateTime = Value(lastUpdateTime);
  static Insertable<CategoryEntity> custom({
    Expression<int>? id,
    Expression<String>? name,
    Expression<int>? accessLevel,
    Expression<String>? createTime,
    Expression<String>? lastUpdateTime,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (name != null) 'name': name,
      if (accessLevel != null) 'access_level': accessLevel,
      if (createTime != null) 'create_time': createTime,
      if (lastUpdateTime != null) 'last_update_time': lastUpdateTime,
    });
  }

  CategoriesCompanion copyWith({
    Value<int>? id,
    Value<String>? name,
    Value<int>? accessLevel,
    Value<String>? createTime,
    Value<String>? lastUpdateTime,
  }) {
    return CategoriesCompanion(
      id: id ?? this.id,
      name: name ?? this.name,
      accessLevel: accessLevel ?? this.accessLevel,
      createTime: createTime ?? this.createTime,
      lastUpdateTime: lastUpdateTime ?? this.lastUpdateTime,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (name.present) {
      map['name'] = Variable<String>(name.value);
    }
    if (accessLevel.present) {
      map['access_level'] = Variable<int>(accessLevel.value);
    }
    if (createTime.present) {
      map['create_time'] = Variable<String>(createTime.value);
    }
    if (lastUpdateTime.present) {
      map['last_update_time'] = Variable<String>(lastUpdateTime.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('CategoriesCompanion(')
          ..write('id: $id, ')
          ..write('name: $name, ')
          ..write('accessLevel: $accessLevel, ')
          ..write('createTime: $createTime, ')
          ..write('lastUpdateTime: $lastUpdateTime')
          ..write(')'))
        .toString();
  }
}

class TDataEncryptKey extends Table
    with TableInfo<TDataEncryptKey, TDataEncryptKeyData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  TDataEncryptKey(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL PRIMARY KEY',
  );
  static const VerificationMeta _dataMeta = const VerificationMeta('data');
  late final GeneratedColumn<String> data = GeneratedColumn<String>(
    'data',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _nonceMeta = const VerificationMeta('nonce');
  late final GeneratedColumn<String> nonce = GeneratedColumn<String>(
    'nonce',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _authTagMeta = const VerificationMeta(
    'authTag',
  );
  late final GeneratedColumn<String> authTag = GeneratedColumn<String>(
    'auth_tag',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL DEFAULT CURRENT_TIMESTAMP',
    defaultValue: const CustomExpression('CURRENT_TIMESTAMP'),
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL DEFAULT CURRENT_TIMESTAMP',
    defaultValue: const CustomExpression('CURRENT_TIMESTAMP'),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    data,
    nonce,
    authTag,
    createdAt,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 't_data_encrypt_key';
  @override
  VerificationContext validateIntegrity(
    Insertable<TDataEncryptKeyData> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('data')) {
      context.handle(
        _dataMeta,
        this.data.isAcceptableOrUnknown(data['data']!, _dataMeta),
      );
    } else if (isInserting) {
      context.missing(_dataMeta);
    }
    if (data.containsKey('nonce')) {
      context.handle(
        _nonceMeta,
        nonce.isAcceptableOrUnknown(data['nonce']!, _nonceMeta),
      );
    } else if (isInserting) {
      context.missing(_nonceMeta);
    }
    if (data.containsKey('auth_tag')) {
      context.handle(
        _authTagMeta,
        authTag.isAcceptableOrUnknown(data['auth_tag']!, _authTagMeta),
      );
    } else if (isInserting) {
      context.missing(_authTagMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  TDataEncryptKeyData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return TDataEncryptKeyData(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      data: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}data'],
      )!,
      nonce: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}nonce'],
      )!,
      authTag: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}auth_tag'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  TDataEncryptKey createAlias(String alias) {
    return TDataEncryptKey(attachedDatabase, alias);
  }

  @override
  bool get dontWriteConstraints => true;
}

class TDataEncryptKeyData extends DataClass
    implements Insertable<TDataEncryptKeyData> {
  final String id;
  final String data;
  final String nonce;
  final String authTag;
  final DateTime createdAt;
  final DateTime updatedAt;
  const TDataEncryptKeyData({
    required this.id,
    required this.data,
    required this.nonce,
    required this.authTag,
    required this.createdAt,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['data'] = Variable<String>(data);
    map['nonce'] = Variable<String>(nonce);
    map['auth_tag'] = Variable<String>(authTag);
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  TDataEncryptKeyCompanion toCompanion(bool nullToAbsent) {
    return TDataEncryptKeyCompanion(
      id: Value(id),
      data: Value(data),
      nonce: Value(nonce),
      authTag: Value(authTag),
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
    );
  }

  factory TDataEncryptKeyData.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return TDataEncryptKeyData(
      id: serializer.fromJson<String>(json['id']),
      data: serializer.fromJson<String>(json['data']),
      nonce: serializer.fromJson<String>(json['nonce']),
      authTag: serializer.fromJson<String>(json['auth_tag']),
      createdAt: serializer.fromJson<DateTime>(json['created_at']),
      updatedAt: serializer.fromJson<DateTime>(json['updated_at']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'data': serializer.toJson<String>(data),
      'nonce': serializer.toJson<String>(nonce),
      'auth_tag': serializer.toJson<String>(authTag),
      'created_at': serializer.toJson<DateTime>(createdAt),
      'updated_at': serializer.toJson<DateTime>(updatedAt),
    };
  }

  TDataEncryptKeyData copyWith({
    String? id,
    String? data,
    String? nonce,
    String? authTag,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) => TDataEncryptKeyData(
    id: id ?? this.id,
    data: data ?? this.data,
    nonce: nonce ?? this.nonce,
    authTag: authTag ?? this.authTag,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  TDataEncryptKeyData copyWithCompanion(TDataEncryptKeyCompanion data) {
    return TDataEncryptKeyData(
      id: data.id.present ? data.id.value : this.id,
      data: data.data.present ? data.data.value : this.data,
      nonce: data.nonce.present ? data.nonce.value : this.nonce,
      authTag: data.authTag.present ? data.authTag.value : this.authTag,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('TDataEncryptKeyData(')
          ..write('id: $id, ')
          ..write('data: $data, ')
          ..write('nonce: $nonce, ')
          ..write('authTag: $authTag, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(id, data, nonce, authTag, createdAt, updatedAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is TDataEncryptKeyData &&
          other.id == this.id &&
          other.data == this.data &&
          other.nonce == this.nonce &&
          other.authTag == this.authTag &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt);
}

class TDataEncryptKeyCompanion extends UpdateCompanion<TDataEncryptKeyData> {
  final Value<String> id;
  final Value<String> data;
  final Value<String> nonce;
  final Value<String> authTag;
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  final Value<int> rowid;
  const TDataEncryptKeyCompanion({
    this.id = const Value.absent(),
    this.data = const Value.absent(),
    this.nonce = const Value.absent(),
    this.authTag = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  TDataEncryptKeyCompanion.insert({
    required String id,
    required String data,
    required String nonce,
    required String authTag,
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       data = Value(data),
       nonce = Value(nonce),
       authTag = Value(authTag);
  static Insertable<TDataEncryptKeyData> custom({
    Expression<String>? id,
    Expression<String>? data,
    Expression<String>? nonce,
    Expression<String>? authTag,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (data != null) 'data': data,
      if (nonce != null) 'nonce': nonce,
      if (authTag != null) 'auth_tag': authTag,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  TDataEncryptKeyCompanion copyWith({
    Value<String>? id,
    Value<String>? data,
    Value<String>? nonce,
    Value<String>? authTag,
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
    Value<int>? rowid,
  }) {
    return TDataEncryptKeyCompanion(
      id: id ?? this.id,
      data: data ?? this.data,
      nonce: nonce ?? this.nonce,
      authTag: authTag ?? this.authTag,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (data.present) {
      map['data'] = Variable<String>(data.value);
    }
    if (nonce.present) {
      map['nonce'] = Variable<String>(nonce.value);
    }
    if (authTag.present) {
      map['auth_tag'] = Variable<String>(authTag.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TDataEncryptKeyCompanion(')
          ..write('id: $id, ')
          ..write('data: $data, ')
          ..write('nonce: $nonce, ')
          ..write('authTag: $authTag, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class TEncryptedData extends Table
    with TableInfo<TEncryptedData, TEncryptedDataData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  TEncryptedData(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL PRIMARY KEY',
  );
  static const VerificationMeta _dekIdMeta = const VerificationMeta('dekId');
  late final GeneratedColumn<String> dekId = GeneratedColumn<String>(
    'dek_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _contentMeta = const VerificationMeta(
    'content',
  );
  late final GeneratedColumn<Uint8List> content = GeneratedColumn<Uint8List>(
    'content',
    aliasedName,
    false,
    type: DriftSqlType.blob,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _algorithmIdMeta = const VerificationMeta(
    'algorithmId',
  );
  late final GeneratedColumn<String> algorithmId = GeneratedColumn<String>(
    'algorithm_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _authTagMeta = const VerificationMeta(
    'authTag',
  );
  late final GeneratedColumn<String> authTag = GeneratedColumn<String>(
    'auth_tag',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _nonceMeta = const VerificationMeta('nonce');
  late final GeneratedColumn<String> nonce = GeneratedColumn<String>(
    'nonce',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL DEFAULT CURRENT_TIMESTAMP',
    defaultValue: const CustomExpression('CURRENT_TIMESTAMP'),
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL DEFAULT CURRENT_TIMESTAMP',
    defaultValue: const CustomExpression('CURRENT_TIMESTAMP'),
  );
  static const VerificationMeta _deletedAtMeta = const VerificationMeta(
    'deletedAt',
  );
  late final GeneratedColumn<DateTime> deletedAt = GeneratedColumn<DateTime>(
    'deleted_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: 'DEFAULT NULL',
    defaultValue: const CustomExpression('NULL'),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    dekId,
    content,
    algorithmId,
    authTag,
    nonce,
    createdAt,
    updatedAt,
    deletedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 't_encrypted_data';
  @override
  VerificationContext validateIntegrity(
    Insertable<TEncryptedDataData> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('dek_id')) {
      context.handle(
        _dekIdMeta,
        dekId.isAcceptableOrUnknown(data['dek_id']!, _dekIdMeta),
      );
    } else if (isInserting) {
      context.missing(_dekIdMeta);
    }
    if (data.containsKey('content')) {
      context.handle(
        _contentMeta,
        content.isAcceptableOrUnknown(data['content']!, _contentMeta),
      );
    } else if (isInserting) {
      context.missing(_contentMeta);
    }
    if (data.containsKey('algorithm_id')) {
      context.handle(
        _algorithmIdMeta,
        algorithmId.isAcceptableOrUnknown(
          data['algorithm_id']!,
          _algorithmIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_algorithmIdMeta);
    }
    if (data.containsKey('auth_tag')) {
      context.handle(
        _authTagMeta,
        authTag.isAcceptableOrUnknown(data['auth_tag']!, _authTagMeta),
      );
    } else if (isInserting) {
      context.missing(_authTagMeta);
    }
    if (data.containsKey('nonce')) {
      context.handle(
        _nonceMeta,
        nonce.isAcceptableOrUnknown(data['nonce']!, _nonceMeta),
      );
    } else if (isInserting) {
      context.missing(_nonceMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    if (data.containsKey('deleted_at')) {
      context.handle(
        _deletedAtMeta,
        deletedAt.isAcceptableOrUnknown(data['deleted_at']!, _deletedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  TEncryptedDataData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return TEncryptedDataData(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      dekId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}dek_id'],
      )!,
      content: attachedDatabase.typeMapping.read(
        DriftSqlType.blob,
        data['${effectivePrefix}content'],
      )!,
      algorithmId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}algorithm_id'],
      )!,
      authTag: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}auth_tag'],
      )!,
      nonce: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}nonce'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
      deletedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}deleted_at'],
      ),
    );
  }

  @override
  TEncryptedData createAlias(String alias) {
    return TEncryptedData(attachedDatabase, alias);
  }

  @override
  List<String> get customConstraints => const [
    'FOREIGN KEY(dek_id)REFERENCES t_data_encrypt_key(id)',
  ];
  @override
  bool get dontWriteConstraints => true;
}

class TEncryptedDataData extends DataClass
    implements Insertable<TEncryptedDataData> {
  final String id;
  final String dekId;
  final Uint8List content;
  final String algorithmId;
  final String authTag;
  final String nonce;
  final DateTime createdAt;
  final DateTime updatedAt;
  final DateTime? deletedAt;
  const TEncryptedDataData({
    required this.id,
    required this.dekId,
    required this.content,
    required this.algorithmId,
    required this.authTag,
    required this.nonce,
    required this.createdAt,
    required this.updatedAt,
    this.deletedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['dek_id'] = Variable<String>(dekId);
    map['content'] = Variable<Uint8List>(content);
    map['algorithm_id'] = Variable<String>(algorithmId);
    map['auth_tag'] = Variable<String>(authTag);
    map['nonce'] = Variable<String>(nonce);
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    if (!nullToAbsent || deletedAt != null) {
      map['deleted_at'] = Variable<DateTime>(deletedAt);
    }
    return map;
  }

  TEncryptedDataCompanion toCompanion(bool nullToAbsent) {
    return TEncryptedDataCompanion(
      id: Value(id),
      dekId: Value(dekId),
      content: Value(content),
      algorithmId: Value(algorithmId),
      authTag: Value(authTag),
      nonce: Value(nonce),
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
      deletedAt: deletedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(deletedAt),
    );
  }

  factory TEncryptedDataData.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return TEncryptedDataData(
      id: serializer.fromJson<String>(json['id']),
      dekId: serializer.fromJson<String>(json['dek_id']),
      content: serializer.fromJson<Uint8List>(json['content']),
      algorithmId: serializer.fromJson<String>(json['algorithm_id']),
      authTag: serializer.fromJson<String>(json['auth_tag']),
      nonce: serializer.fromJson<String>(json['nonce']),
      createdAt: serializer.fromJson<DateTime>(json['created_at']),
      updatedAt: serializer.fromJson<DateTime>(json['updated_at']),
      deletedAt: serializer.fromJson<DateTime?>(json['deleted_at']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'dek_id': serializer.toJson<String>(dekId),
      'content': serializer.toJson<Uint8List>(content),
      'algorithm_id': serializer.toJson<String>(algorithmId),
      'auth_tag': serializer.toJson<String>(authTag),
      'nonce': serializer.toJson<String>(nonce),
      'created_at': serializer.toJson<DateTime>(createdAt),
      'updated_at': serializer.toJson<DateTime>(updatedAt),
      'deleted_at': serializer.toJson<DateTime?>(deletedAt),
    };
  }

  TEncryptedDataData copyWith({
    String? id,
    String? dekId,
    Uint8List? content,
    String? algorithmId,
    String? authTag,
    String? nonce,
    DateTime? createdAt,
    DateTime? updatedAt,
    Value<DateTime?> deletedAt = const Value.absent(),
  }) => TEncryptedDataData(
    id: id ?? this.id,
    dekId: dekId ?? this.dekId,
    content: content ?? this.content,
    algorithmId: algorithmId ?? this.algorithmId,
    authTag: authTag ?? this.authTag,
    nonce: nonce ?? this.nonce,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
    deletedAt: deletedAt.present ? deletedAt.value : this.deletedAt,
  );
  TEncryptedDataData copyWithCompanion(TEncryptedDataCompanion data) {
    return TEncryptedDataData(
      id: data.id.present ? data.id.value : this.id,
      dekId: data.dekId.present ? data.dekId.value : this.dekId,
      content: data.content.present ? data.content.value : this.content,
      algorithmId: data.algorithmId.present
          ? data.algorithmId.value
          : this.algorithmId,
      authTag: data.authTag.present ? data.authTag.value : this.authTag,
      nonce: data.nonce.present ? data.nonce.value : this.nonce,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      deletedAt: data.deletedAt.present ? data.deletedAt.value : this.deletedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('TEncryptedDataData(')
          ..write('id: $id, ')
          ..write('dekId: $dekId, ')
          ..write('content: $content, ')
          ..write('algorithmId: $algorithmId, ')
          ..write('authTag: $authTag, ')
          ..write('nonce: $nonce, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('deletedAt: $deletedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    dekId,
    $driftBlobEquality.hash(content),
    algorithmId,
    authTag,
    nonce,
    createdAt,
    updatedAt,
    deletedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is TEncryptedDataData &&
          other.id == this.id &&
          other.dekId == this.dekId &&
          $driftBlobEquality.equals(other.content, this.content) &&
          other.algorithmId == this.algorithmId &&
          other.authTag == this.authTag &&
          other.nonce == this.nonce &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt &&
          other.deletedAt == this.deletedAt);
}

class TEncryptedDataCompanion extends UpdateCompanion<TEncryptedDataData> {
  final Value<String> id;
  final Value<String> dekId;
  final Value<Uint8List> content;
  final Value<String> algorithmId;
  final Value<String> authTag;
  final Value<String> nonce;
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  final Value<DateTime?> deletedAt;
  final Value<int> rowid;
  const TEncryptedDataCompanion({
    this.id = const Value.absent(),
    this.dekId = const Value.absent(),
    this.content = const Value.absent(),
    this.algorithmId = const Value.absent(),
    this.authTag = const Value.absent(),
    this.nonce = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.deletedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  TEncryptedDataCompanion.insert({
    required String id,
    required String dekId,
    required Uint8List content,
    required String algorithmId,
    required String authTag,
    required String nonce,
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.deletedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       dekId = Value(dekId),
       content = Value(content),
       algorithmId = Value(algorithmId),
       authTag = Value(authTag),
       nonce = Value(nonce);
  static Insertable<TEncryptedDataData> custom({
    Expression<String>? id,
    Expression<String>? dekId,
    Expression<Uint8List>? content,
    Expression<String>? algorithmId,
    Expression<String>? authTag,
    Expression<String>? nonce,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<DateTime>? deletedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (dekId != null) 'dek_id': dekId,
      if (content != null) 'content': content,
      if (algorithmId != null) 'algorithm_id': algorithmId,
      if (authTag != null) 'auth_tag': authTag,
      if (nonce != null) 'nonce': nonce,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (deletedAt != null) 'deleted_at': deletedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  TEncryptedDataCompanion copyWith({
    Value<String>? id,
    Value<String>? dekId,
    Value<Uint8List>? content,
    Value<String>? algorithmId,
    Value<String>? authTag,
    Value<String>? nonce,
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
    Value<DateTime?>? deletedAt,
    Value<int>? rowid,
  }) {
    return TEncryptedDataCompanion(
      id: id ?? this.id,
      dekId: dekId ?? this.dekId,
      content: content ?? this.content,
      algorithmId: algorithmId ?? this.algorithmId,
      authTag: authTag ?? this.authTag,
      nonce: nonce ?? this.nonce,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      deletedAt: deletedAt ?? this.deletedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (dekId.present) {
      map['dek_id'] = Variable<String>(dekId.value);
    }
    if (content.present) {
      map['content'] = Variable<Uint8List>(content.value);
    }
    if (algorithmId.present) {
      map['algorithm_id'] = Variable<String>(algorithmId.value);
    }
    if (authTag.present) {
      map['auth_tag'] = Variable<String>(authTag.value);
    }
    if (nonce.present) {
      map['nonce'] = Variable<String>(nonce.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (deletedAt.present) {
      map['deleted_at'] = Variable<DateTime>(deletedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TEncryptedDataCompanion(')
          ..write('id: $id, ')
          ..write('dekId: $dekId, ')
          ..write('content: $content, ')
          ..write('algorithmId: $algorithmId, ')
          ..write('authTag: $authTag, ')
          ..write('nonce: $nonce, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('deletedAt: $deletedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class TPassword extends Table with TableInfo<TPassword, TPasswordData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  TPassword(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL PRIMARY KEY',
  );
  static const VerificationMeta _typeMeta = const VerificationMeta('type');
  late final GeneratedColumn<int> type = GeneratedColumn<int>(
    'type',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL DEFAULT 1',
    defaultValue: const CustomExpression('1'),
  );
  static const VerificationMeta _categoryIdMeta = const VerificationMeta(
    'categoryId',
  );
  late final GeneratedColumn<int> categoryId = GeneratedColumn<int>(
    'category_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL DEFAULT 1',
    defaultValue: const CustomExpression('1'),
  );
  static const VerificationMeta _classificationMeta = const VerificationMeta(
    'classification',
  );
  late final GeneratedColumn<String> classification = GeneratedColumn<String>(
    'classification',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints:
        'NOT NULL CHECK (classification IN (\'C\', \'S\', \'T\'))',
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    $customConstraints: '',
  );
  static const VerificationMeta _encryptedDataIdMeta = const VerificationMeta(
    'encryptedDataId',
  );
  late final GeneratedColumn<String> encryptedDataId = GeneratedColumn<String>(
    'encrypted_data_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _expireTimeMeta = const VerificationMeta(
    'expireTime',
  );
  late final GeneratedColumn<DateTime> expireTime = GeneratedColumn<DateTime>(
    'expire_time',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: '',
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL DEFAULT CURRENT_TIMESTAMP',
    defaultValue: const CustomExpression('CURRENT_TIMESTAMP'),
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL DEFAULT CURRENT_TIMESTAMP',
    defaultValue: const CustomExpression('CURRENT_TIMESTAMP'),
  );
  static const VerificationMeta _deletedAtMeta = const VerificationMeta(
    'deletedAt',
  );
  late final GeneratedColumn<DateTime> deletedAt = GeneratedColumn<DateTime>(
    'deleted_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: 'DEFAULT NULL',
    defaultValue: const CustomExpression('NULL'),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    type,
    categoryId,
    classification,
    title,
    encryptedDataId,
    expireTime,
    createdAt,
    updatedAt,
    deletedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 't_password';
  @override
  VerificationContext validateIntegrity(
    Insertable<TPasswordData> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('type')) {
      context.handle(
        _typeMeta,
        type.isAcceptableOrUnknown(data['type']!, _typeMeta),
      );
    }
    if (data.containsKey('category_id')) {
      context.handle(
        _categoryIdMeta,
        categoryId.isAcceptableOrUnknown(data['category_id']!, _categoryIdMeta),
      );
    }
    if (data.containsKey('classification')) {
      context.handle(
        _classificationMeta,
        classification.isAcceptableOrUnknown(
          data['classification']!,
          _classificationMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_classificationMeta);
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    }
    if (data.containsKey('encrypted_data_id')) {
      context.handle(
        _encryptedDataIdMeta,
        encryptedDataId.isAcceptableOrUnknown(
          data['encrypted_data_id']!,
          _encryptedDataIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_encryptedDataIdMeta);
    }
    if (data.containsKey('expire_time')) {
      context.handle(
        _expireTimeMeta,
        expireTime.isAcceptableOrUnknown(data['expire_time']!, _expireTimeMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    if (data.containsKey('deleted_at')) {
      context.handle(
        _deletedAtMeta,
        deletedAt.isAcceptableOrUnknown(data['deleted_at']!, _deletedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  TPasswordData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return TPasswordData(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      type: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}type'],
      )!,
      categoryId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}category_id'],
      )!,
      classification: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}classification'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      ),
      encryptedDataId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}encrypted_data_id'],
      )!,
      expireTime: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}expire_time'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
      deletedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}deleted_at'],
      ),
    );
  }

  @override
  TPassword createAlias(String alias) {
    return TPassword(attachedDatabase, alias);
  }

  @override
  List<String> get customConstraints => const [
    'FOREIGN KEY(category_id)REFERENCES categories(id)',
    'FOREIGN KEY(encrypted_data_id)REFERENCES t_encrypted_data(id)',
  ];
  @override
  bool get dontWriteConstraints => true;
}

class TPasswordData extends DataClass implements Insertable<TPasswordData> {
  final String id;
  final int type;
  final int categoryId;
  final String classification;
  final String? title;
  final String encryptedDataId;
  final DateTime? expireTime;
  final DateTime createdAt;
  final DateTime updatedAt;
  final DateTime? deletedAt;
  const TPasswordData({
    required this.id,
    required this.type,
    required this.categoryId,
    required this.classification,
    this.title,
    required this.encryptedDataId,
    this.expireTime,
    required this.createdAt,
    required this.updatedAt,
    this.deletedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['type'] = Variable<int>(type);
    map['category_id'] = Variable<int>(categoryId);
    map['classification'] = Variable<String>(classification);
    if (!nullToAbsent || title != null) {
      map['title'] = Variable<String>(title);
    }
    map['encrypted_data_id'] = Variable<String>(encryptedDataId);
    if (!nullToAbsent || expireTime != null) {
      map['expire_time'] = Variable<DateTime>(expireTime);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    if (!nullToAbsent || deletedAt != null) {
      map['deleted_at'] = Variable<DateTime>(deletedAt);
    }
    return map;
  }

  TPasswordCompanion toCompanion(bool nullToAbsent) {
    return TPasswordCompanion(
      id: Value(id),
      type: Value(type),
      categoryId: Value(categoryId),
      classification: Value(classification),
      title: title == null && nullToAbsent
          ? const Value.absent()
          : Value(title),
      encryptedDataId: Value(encryptedDataId),
      expireTime: expireTime == null && nullToAbsent
          ? const Value.absent()
          : Value(expireTime),
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
      deletedAt: deletedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(deletedAt),
    );
  }

  factory TPasswordData.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return TPasswordData(
      id: serializer.fromJson<String>(json['id']),
      type: serializer.fromJson<int>(json['type']),
      categoryId: serializer.fromJson<int>(json['category_id']),
      classification: serializer.fromJson<String>(json['classification']),
      title: serializer.fromJson<String?>(json['title']),
      encryptedDataId: serializer.fromJson<String>(json['encrypted_data_id']),
      expireTime: serializer.fromJson<DateTime?>(json['expire_time']),
      createdAt: serializer.fromJson<DateTime>(json['created_at']),
      updatedAt: serializer.fromJson<DateTime>(json['updated_at']),
      deletedAt: serializer.fromJson<DateTime?>(json['deleted_at']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'type': serializer.toJson<int>(type),
      'category_id': serializer.toJson<int>(categoryId),
      'classification': serializer.toJson<String>(classification),
      'title': serializer.toJson<String?>(title),
      'encrypted_data_id': serializer.toJson<String>(encryptedDataId),
      'expire_time': serializer.toJson<DateTime?>(expireTime),
      'created_at': serializer.toJson<DateTime>(createdAt),
      'updated_at': serializer.toJson<DateTime>(updatedAt),
      'deleted_at': serializer.toJson<DateTime?>(deletedAt),
    };
  }

  TPasswordData copyWith({
    String? id,
    int? type,
    int? categoryId,
    String? classification,
    Value<String?> title = const Value.absent(),
    String? encryptedDataId,
    Value<DateTime?> expireTime = const Value.absent(),
    DateTime? createdAt,
    DateTime? updatedAt,
    Value<DateTime?> deletedAt = const Value.absent(),
  }) => TPasswordData(
    id: id ?? this.id,
    type: type ?? this.type,
    categoryId: categoryId ?? this.categoryId,
    classification: classification ?? this.classification,
    title: title.present ? title.value : this.title,
    encryptedDataId: encryptedDataId ?? this.encryptedDataId,
    expireTime: expireTime.present ? expireTime.value : this.expireTime,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
    deletedAt: deletedAt.present ? deletedAt.value : this.deletedAt,
  );
  TPasswordData copyWithCompanion(TPasswordCompanion data) {
    return TPasswordData(
      id: data.id.present ? data.id.value : this.id,
      type: data.type.present ? data.type.value : this.type,
      categoryId: data.categoryId.present
          ? data.categoryId.value
          : this.categoryId,
      classification: data.classification.present
          ? data.classification.value
          : this.classification,
      title: data.title.present ? data.title.value : this.title,
      encryptedDataId: data.encryptedDataId.present
          ? data.encryptedDataId.value
          : this.encryptedDataId,
      expireTime: data.expireTime.present
          ? data.expireTime.value
          : this.expireTime,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      deletedAt: data.deletedAt.present ? data.deletedAt.value : this.deletedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('TPasswordData(')
          ..write('id: $id, ')
          ..write('type: $type, ')
          ..write('categoryId: $categoryId, ')
          ..write('classification: $classification, ')
          ..write('title: $title, ')
          ..write('encryptedDataId: $encryptedDataId, ')
          ..write('expireTime: $expireTime, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('deletedAt: $deletedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    type,
    categoryId,
    classification,
    title,
    encryptedDataId,
    expireTime,
    createdAt,
    updatedAt,
    deletedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is TPasswordData &&
          other.id == this.id &&
          other.type == this.type &&
          other.categoryId == this.categoryId &&
          other.classification == this.classification &&
          other.title == this.title &&
          other.encryptedDataId == this.encryptedDataId &&
          other.expireTime == this.expireTime &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt &&
          other.deletedAt == this.deletedAt);
}

class TPasswordCompanion extends UpdateCompanion<TPasswordData> {
  final Value<String> id;
  final Value<int> type;
  final Value<int> categoryId;
  final Value<String> classification;
  final Value<String?> title;
  final Value<String> encryptedDataId;
  final Value<DateTime?> expireTime;
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  final Value<DateTime?> deletedAt;
  final Value<int> rowid;
  const TPasswordCompanion({
    this.id = const Value.absent(),
    this.type = const Value.absent(),
    this.categoryId = const Value.absent(),
    this.classification = const Value.absent(),
    this.title = const Value.absent(),
    this.encryptedDataId = const Value.absent(),
    this.expireTime = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.deletedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  TPasswordCompanion.insert({
    required String id,
    this.type = const Value.absent(),
    this.categoryId = const Value.absent(),
    required String classification,
    this.title = const Value.absent(),
    required String encryptedDataId,
    this.expireTime = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.deletedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       classification = Value(classification),
       encryptedDataId = Value(encryptedDataId);
  static Insertable<TPasswordData> custom({
    Expression<String>? id,
    Expression<int>? type,
    Expression<int>? categoryId,
    Expression<String>? classification,
    Expression<String>? title,
    Expression<String>? encryptedDataId,
    Expression<DateTime>? expireTime,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<DateTime>? deletedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (type != null) 'type': type,
      if (categoryId != null) 'category_id': categoryId,
      if (classification != null) 'classification': classification,
      if (title != null) 'title': title,
      if (encryptedDataId != null) 'encrypted_data_id': encryptedDataId,
      if (expireTime != null) 'expire_time': expireTime,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (deletedAt != null) 'deleted_at': deletedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  TPasswordCompanion copyWith({
    Value<String>? id,
    Value<int>? type,
    Value<int>? categoryId,
    Value<String>? classification,
    Value<String?>? title,
    Value<String>? encryptedDataId,
    Value<DateTime?>? expireTime,
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
    Value<DateTime?>? deletedAt,
    Value<int>? rowid,
  }) {
    return TPasswordCompanion(
      id: id ?? this.id,
      type: type ?? this.type,
      categoryId: categoryId ?? this.categoryId,
      classification: classification ?? this.classification,
      title: title ?? this.title,
      encryptedDataId: encryptedDataId ?? this.encryptedDataId,
      expireTime: expireTime ?? this.expireTime,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      deletedAt: deletedAt ?? this.deletedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (type.present) {
      map['type'] = Variable<int>(type.value);
    }
    if (categoryId.present) {
      map['category_id'] = Variable<int>(categoryId.value);
    }
    if (classification.present) {
      map['classification'] = Variable<String>(classification.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (encryptedDataId.present) {
      map['encrypted_data_id'] = Variable<String>(encryptedDataId.value);
    }
    if (expireTime.present) {
      map['expire_time'] = Variable<DateTime>(expireTime.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (deletedAt.present) {
      map['deleted_at'] = Variable<DateTime>(deletedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TPasswordCompanion(')
          ..write('id: $id, ')
          ..write('type: $type, ')
          ..write('categoryId: $categoryId, ')
          ..write('classification: $classification, ')
          ..write('title: $title, ')
          ..write('encryptedDataId: $encryptedDataId, ')
          ..write('expireTime: $expireTime, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('deletedAt: $deletedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class TPasswordAttribute extends Table
    with TableInfo<TPasswordAttribute, TPasswordAttributeData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  TPasswordAttribute(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL PRIMARY KEY AUTOINCREMENT',
  );
  static const VerificationMeta _passwordIdMeta = const VerificationMeta(
    'passwordId',
  );
  late final GeneratedColumn<String> passwordId = GeneratedColumn<String>(
    'password_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _classificationMeta = const VerificationMeta(
    'classification',
  );
  late final GeneratedColumn<String> classification = GeneratedColumn<String>(
    'classification',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints:
        'NOT NULL CHECK (classification IN (\'C\', \'S\', \'T\'))',
  );
  static const VerificationMeta _nameMeta = const VerificationMeta('name');
  late final GeneratedColumn<String> name = GeneratedColumn<String>(
    'name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _valueMeta = const VerificationMeta('value');
  late final GeneratedColumn<String> value = GeneratedColumn<String>(
    'value',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    $customConstraints: '',
  );
  static const VerificationMeta _encryptedDataIdMeta = const VerificationMeta(
    'encryptedDataId',
  );
  late final GeneratedColumn<String> encryptedDataId = GeneratedColumn<String>(
    'encrypted_data_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    $customConstraints: '',
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL DEFAULT CURRENT_TIMESTAMP',
    defaultValue: const CustomExpression('CURRENT_TIMESTAMP'),
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL DEFAULT CURRENT_TIMESTAMP',
    defaultValue: const CustomExpression('CURRENT_TIMESTAMP'),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    passwordId,
    classification,
    name,
    value,
    encryptedDataId,
    createdAt,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 't_password_attribute';
  @override
  VerificationContext validateIntegrity(
    Insertable<TPasswordAttributeData> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('password_id')) {
      context.handle(
        _passwordIdMeta,
        passwordId.isAcceptableOrUnknown(data['password_id']!, _passwordIdMeta),
      );
    } else if (isInserting) {
      context.missing(_passwordIdMeta);
    }
    if (data.containsKey('classification')) {
      context.handle(
        _classificationMeta,
        classification.isAcceptableOrUnknown(
          data['classification']!,
          _classificationMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_classificationMeta);
    }
    if (data.containsKey('name')) {
      context.handle(
        _nameMeta,
        name.isAcceptableOrUnknown(data['name']!, _nameMeta),
      );
    } else if (isInserting) {
      context.missing(_nameMeta);
    }
    if (data.containsKey('value')) {
      context.handle(
        _valueMeta,
        value.isAcceptableOrUnknown(data['value']!, _valueMeta),
      );
    }
    if (data.containsKey('encrypted_data_id')) {
      context.handle(
        _encryptedDataIdMeta,
        encryptedDataId.isAcceptableOrUnknown(
          data['encrypted_data_id']!,
          _encryptedDataIdMeta,
        ),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  TPasswordAttributeData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return TPasswordAttributeData(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      passwordId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}password_id'],
      )!,
      classification: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}classification'],
      )!,
      name: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}name'],
      )!,
      value: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}value'],
      ),
      encryptedDataId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}encrypted_data_id'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  TPasswordAttribute createAlias(String alias) {
    return TPasswordAttribute(attachedDatabase, alias);
  }

  @override
  List<String> get customConstraints => const [
    'FOREIGN KEY(encrypted_data_id)REFERENCES t_encrypted_data(id)',
  ];
  @override
  bool get dontWriteConstraints => true;
}

class TPasswordAttributeData extends DataClass
    implements Insertable<TPasswordAttributeData> {
  final int id;
  final String passwordId;
  final String classification;
  final String name;
  final String? value;
  final String? encryptedDataId;
  final DateTime createdAt;
  final DateTime updatedAt;
  const TPasswordAttributeData({
    required this.id,
    required this.passwordId,
    required this.classification,
    required this.name,
    this.value,
    this.encryptedDataId,
    required this.createdAt,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['password_id'] = Variable<String>(passwordId);
    map['classification'] = Variable<String>(classification);
    map['name'] = Variable<String>(name);
    if (!nullToAbsent || value != null) {
      map['value'] = Variable<String>(value);
    }
    if (!nullToAbsent || encryptedDataId != null) {
      map['encrypted_data_id'] = Variable<String>(encryptedDataId);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  TPasswordAttributeCompanion toCompanion(bool nullToAbsent) {
    return TPasswordAttributeCompanion(
      id: Value(id),
      passwordId: Value(passwordId),
      classification: Value(classification),
      name: Value(name),
      value: value == null && nullToAbsent
          ? const Value.absent()
          : Value(value),
      encryptedDataId: encryptedDataId == null && nullToAbsent
          ? const Value.absent()
          : Value(encryptedDataId),
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
    );
  }

  factory TPasswordAttributeData.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return TPasswordAttributeData(
      id: serializer.fromJson<int>(json['id']),
      passwordId: serializer.fromJson<String>(json['password_id']),
      classification: serializer.fromJson<String>(json['classification']),
      name: serializer.fromJson<String>(json['name']),
      value: serializer.fromJson<String?>(json['value']),
      encryptedDataId: serializer.fromJson<String?>(json['encrypted_data_id']),
      createdAt: serializer.fromJson<DateTime>(json['created_at']),
      updatedAt: serializer.fromJson<DateTime>(json['updated_at']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'password_id': serializer.toJson<String>(passwordId),
      'classification': serializer.toJson<String>(classification),
      'name': serializer.toJson<String>(name),
      'value': serializer.toJson<String?>(value),
      'encrypted_data_id': serializer.toJson<String?>(encryptedDataId),
      'created_at': serializer.toJson<DateTime>(createdAt),
      'updated_at': serializer.toJson<DateTime>(updatedAt),
    };
  }

  TPasswordAttributeData copyWith({
    int? id,
    String? passwordId,
    String? classification,
    String? name,
    Value<String?> value = const Value.absent(),
    Value<String?> encryptedDataId = const Value.absent(),
    DateTime? createdAt,
    DateTime? updatedAt,
  }) => TPasswordAttributeData(
    id: id ?? this.id,
    passwordId: passwordId ?? this.passwordId,
    classification: classification ?? this.classification,
    name: name ?? this.name,
    value: value.present ? value.value : this.value,
    encryptedDataId: encryptedDataId.present
        ? encryptedDataId.value
        : this.encryptedDataId,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  TPasswordAttributeData copyWithCompanion(TPasswordAttributeCompanion data) {
    return TPasswordAttributeData(
      id: data.id.present ? data.id.value : this.id,
      passwordId: data.passwordId.present
          ? data.passwordId.value
          : this.passwordId,
      classification: data.classification.present
          ? data.classification.value
          : this.classification,
      name: data.name.present ? data.name.value : this.name,
      value: data.value.present ? data.value.value : this.value,
      encryptedDataId: data.encryptedDataId.present
          ? data.encryptedDataId.value
          : this.encryptedDataId,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('TPasswordAttributeData(')
          ..write('id: $id, ')
          ..write('passwordId: $passwordId, ')
          ..write('classification: $classification, ')
          ..write('name: $name, ')
          ..write('value: $value, ')
          ..write('encryptedDataId: $encryptedDataId, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    passwordId,
    classification,
    name,
    value,
    encryptedDataId,
    createdAt,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is TPasswordAttributeData &&
          other.id == this.id &&
          other.passwordId == this.passwordId &&
          other.classification == this.classification &&
          other.name == this.name &&
          other.value == this.value &&
          other.encryptedDataId == this.encryptedDataId &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt);
}

class TPasswordAttributeCompanion
    extends UpdateCompanion<TPasswordAttributeData> {
  final Value<int> id;
  final Value<String> passwordId;
  final Value<String> classification;
  final Value<String> name;
  final Value<String?> value;
  final Value<String?> encryptedDataId;
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  const TPasswordAttributeCompanion({
    this.id = const Value.absent(),
    this.passwordId = const Value.absent(),
    this.classification = const Value.absent(),
    this.name = const Value.absent(),
    this.value = const Value.absent(),
    this.encryptedDataId = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
  });
  TPasswordAttributeCompanion.insert({
    this.id = const Value.absent(),
    required String passwordId,
    required String classification,
    required String name,
    this.value = const Value.absent(),
    this.encryptedDataId = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
  }) : passwordId = Value(passwordId),
       classification = Value(classification),
       name = Value(name);
  static Insertable<TPasswordAttributeData> custom({
    Expression<int>? id,
    Expression<String>? passwordId,
    Expression<String>? classification,
    Expression<String>? name,
    Expression<String>? value,
    Expression<String>? encryptedDataId,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (passwordId != null) 'password_id': passwordId,
      if (classification != null) 'classification': classification,
      if (name != null) 'name': name,
      if (value != null) 'value': value,
      if (encryptedDataId != null) 'encrypted_data_id': encryptedDataId,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
    });
  }

  TPasswordAttributeCompanion copyWith({
    Value<int>? id,
    Value<String>? passwordId,
    Value<String>? classification,
    Value<String>? name,
    Value<String?>? value,
    Value<String?>? encryptedDataId,
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
  }) {
    return TPasswordAttributeCompanion(
      id: id ?? this.id,
      passwordId: passwordId ?? this.passwordId,
      classification: classification ?? this.classification,
      name: name ?? this.name,
      value: value ?? this.value,
      encryptedDataId: encryptedDataId ?? this.encryptedDataId,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (passwordId.present) {
      map['password_id'] = Variable<String>(passwordId.value);
    }
    if (classification.present) {
      map['classification'] = Variable<String>(classification.value);
    }
    if (name.present) {
      map['name'] = Variable<String>(name.value);
    }
    if (value.present) {
      map['value'] = Variable<String>(value.value);
    }
    if (encryptedDataId.present) {
      map['encrypted_data_id'] = Variable<String>(encryptedDataId.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TPasswordAttributeCompanion(')
          ..write('id: $id, ')
          ..write('passwordId: $passwordId, ')
          ..write('classification: $classification, ')
          ..write('name: $name, ')
          ..write('value: $value, ')
          ..write('encryptedDataId: $encryptedDataId, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }
}

class TPasswordHistory extends Table
    with TableInfo<TPasswordHistory, TPasswordHistoryData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  TPasswordHistory(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL PRIMARY KEY AUTOINCREMENT',
  );
  static const VerificationMeta _passwordIdMeta = const VerificationMeta(
    'passwordId',
  );
  late final GeneratedColumn<String> passwordId = GeneratedColumn<String>(
    'password_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _encryptedDataIdMeta = const VerificationMeta(
    'encryptedDataId',
  );
  late final GeneratedColumn<String> encryptedDataId = GeneratedColumn<String>(
    'encrypted_data_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL DEFAULT CURRENT_TIMESTAMP',
    defaultValue: const CustomExpression('CURRENT_TIMESTAMP'),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    passwordId,
    encryptedDataId,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 't_password_history';
  @override
  VerificationContext validateIntegrity(
    Insertable<TPasswordHistoryData> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('password_id')) {
      context.handle(
        _passwordIdMeta,
        passwordId.isAcceptableOrUnknown(data['password_id']!, _passwordIdMeta),
      );
    } else if (isInserting) {
      context.missing(_passwordIdMeta);
    }
    if (data.containsKey('encrypted_data_id')) {
      context.handle(
        _encryptedDataIdMeta,
        encryptedDataId.isAcceptableOrUnknown(
          data['encrypted_data_id']!,
          _encryptedDataIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_encryptedDataIdMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  TPasswordHistoryData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return TPasswordHistoryData(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      passwordId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}password_id'],
      )!,
      encryptedDataId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}encrypted_data_id'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  TPasswordHistory createAlias(String alias) {
    return TPasswordHistory(attachedDatabase, alias);
  }

  @override
  List<String> get customConstraints => const [
    'FOREIGN KEY(password_id)REFERENCES t_password(id)',
    'FOREIGN KEY(encrypted_data_id)REFERENCES t_encrypted_data(id)',
  ];
  @override
  bool get dontWriteConstraints => true;
}

class TPasswordHistoryData extends DataClass
    implements Insertable<TPasswordHistoryData> {
  final int id;
  final String passwordId;
  final String encryptedDataId;
  final DateTime createdAt;
  const TPasswordHistoryData({
    required this.id,
    required this.passwordId,
    required this.encryptedDataId,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['password_id'] = Variable<String>(passwordId);
    map['encrypted_data_id'] = Variable<String>(encryptedDataId);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  TPasswordHistoryCompanion toCompanion(bool nullToAbsent) {
    return TPasswordHistoryCompanion(
      id: Value(id),
      passwordId: Value(passwordId),
      encryptedDataId: Value(encryptedDataId),
      createdAt: Value(createdAt),
    );
  }

  factory TPasswordHistoryData.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return TPasswordHistoryData(
      id: serializer.fromJson<int>(json['id']),
      passwordId: serializer.fromJson<String>(json['password_id']),
      encryptedDataId: serializer.fromJson<String>(json['encrypted_data_id']),
      createdAt: serializer.fromJson<DateTime>(json['created_at']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'password_id': serializer.toJson<String>(passwordId),
      'encrypted_data_id': serializer.toJson<String>(encryptedDataId),
      'created_at': serializer.toJson<DateTime>(createdAt),
    };
  }

  TPasswordHistoryData copyWith({
    int? id,
    String? passwordId,
    String? encryptedDataId,
    DateTime? createdAt,
  }) => TPasswordHistoryData(
    id: id ?? this.id,
    passwordId: passwordId ?? this.passwordId,
    encryptedDataId: encryptedDataId ?? this.encryptedDataId,
    createdAt: createdAt ?? this.createdAt,
  );
  TPasswordHistoryData copyWithCompanion(TPasswordHistoryCompanion data) {
    return TPasswordHistoryData(
      id: data.id.present ? data.id.value : this.id,
      passwordId: data.passwordId.present
          ? data.passwordId.value
          : this.passwordId,
      encryptedDataId: data.encryptedDataId.present
          ? data.encryptedDataId.value
          : this.encryptedDataId,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('TPasswordHistoryData(')
          ..write('id: $id, ')
          ..write('passwordId: $passwordId, ')
          ..write('encryptedDataId: $encryptedDataId, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(id, passwordId, encryptedDataId, createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is TPasswordHistoryData &&
          other.id == this.id &&
          other.passwordId == this.passwordId &&
          other.encryptedDataId == this.encryptedDataId &&
          other.createdAt == this.createdAt);
}

class TPasswordHistoryCompanion extends UpdateCompanion<TPasswordHistoryData> {
  final Value<int> id;
  final Value<String> passwordId;
  final Value<String> encryptedDataId;
  final Value<DateTime> createdAt;
  const TPasswordHistoryCompanion({
    this.id = const Value.absent(),
    this.passwordId = const Value.absent(),
    this.encryptedDataId = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  TPasswordHistoryCompanion.insert({
    this.id = const Value.absent(),
    required String passwordId,
    required String encryptedDataId,
    this.createdAt = const Value.absent(),
  }) : passwordId = Value(passwordId),
       encryptedDataId = Value(encryptedDataId);
  static Insertable<TPasswordHistoryData> custom({
    Expression<int>? id,
    Expression<String>? passwordId,
    Expression<String>? encryptedDataId,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (passwordId != null) 'password_id': passwordId,
      if (encryptedDataId != null) 'encrypted_data_id': encryptedDataId,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  TPasswordHistoryCompanion copyWith({
    Value<int>? id,
    Value<String>? passwordId,
    Value<String>? encryptedDataId,
    Value<DateTime>? createdAt,
  }) {
    return TPasswordHistoryCompanion(
      id: id ?? this.id,
      passwordId: passwordId ?? this.passwordId,
      encryptedDataId: encryptedDataId ?? this.encryptedDataId,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (passwordId.present) {
      map['password_id'] = Variable<String>(passwordId.value);
    }
    if (encryptedDataId.present) {
      map['encrypted_data_id'] = Variable<String>(encryptedDataId.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TPasswordHistoryCompanion(')
          ..write('id: $id, ')
          ..write('passwordId: $passwordId, ')
          ..write('encryptedDataId: $encryptedDataId, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class TNote extends Table with TableInfo<TNote, TNoteData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  TNote(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL PRIMARY KEY',
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    $customConstraints: '',
  );
  static const VerificationMeta _contentJsonMeta = const VerificationMeta(
    'contentJson',
  );
  late final GeneratedColumn<String> contentJson = GeneratedColumn<String>(
    'content_json',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _contentChecksumMeta = const VerificationMeta(
    'contentChecksum',
  );
  late final GeneratedColumn<String> contentChecksum = GeneratedColumn<String>(
    'content_checksum',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _contentPlainMeta = const VerificationMeta(
    'contentPlain',
  );
  late final GeneratedColumn<String> contentPlain = GeneratedColumn<String>(
    'content_plain',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    $customConstraints: 'NULL',
  );
  static const VerificationMeta _abstractMeta = const VerificationMeta(
    'abstract',
  );
  late final GeneratedColumn<String> abstract = GeneratedColumn<String>(
    'abstract',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    $customConstraints: '',
  );
  static const VerificationMeta _classificationMeta = const VerificationMeta(
    'classification',
  );
  late final GeneratedColumn<String> classification = GeneratedColumn<String>(
    'classification',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints:
        'NOT NULL CHECK (classification IN (\'C\', \'S\', \'T\'))',
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL DEFAULT CURRENT_TIMESTAMP',
    defaultValue: const CustomExpression('CURRENT_TIMESTAMP'),
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL DEFAULT CURRENT_TIMESTAMP',
    defaultValue: const CustomExpression('CURRENT_TIMESTAMP'),
  );
  static const VerificationMeta _deletedAtMeta = const VerificationMeta(
    'deletedAt',
  );
  late final GeneratedColumn<DateTime> deletedAt = GeneratedColumn<DateTime>(
    'deleted_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: 'DEFAULT NULL',
    defaultValue: const CustomExpression('NULL'),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    title,
    contentJson,
    contentChecksum,
    contentPlain,
    abstract,
    classification,
    createdAt,
    updatedAt,
    deletedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 't_note';
  @override
  VerificationContext validateIntegrity(
    Insertable<TNoteData> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    }
    if (data.containsKey('content_json')) {
      context.handle(
        _contentJsonMeta,
        contentJson.isAcceptableOrUnknown(
          data['content_json']!,
          _contentJsonMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_contentJsonMeta);
    }
    if (data.containsKey('content_checksum')) {
      context.handle(
        _contentChecksumMeta,
        contentChecksum.isAcceptableOrUnknown(
          data['content_checksum']!,
          _contentChecksumMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_contentChecksumMeta);
    }
    if (data.containsKey('content_plain')) {
      context.handle(
        _contentPlainMeta,
        contentPlain.isAcceptableOrUnknown(
          data['content_plain']!,
          _contentPlainMeta,
        ),
      );
    }
    if (data.containsKey('abstract')) {
      context.handle(
        _abstractMeta,
        abstract.isAcceptableOrUnknown(data['abstract']!, _abstractMeta),
      );
    }
    if (data.containsKey('classification')) {
      context.handle(
        _classificationMeta,
        classification.isAcceptableOrUnknown(
          data['classification']!,
          _classificationMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_classificationMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    if (data.containsKey('deleted_at')) {
      context.handle(
        _deletedAtMeta,
        deletedAt.isAcceptableOrUnknown(data['deleted_at']!, _deletedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  TNoteData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return TNoteData(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      ),
      contentJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}content_json'],
      )!,
      contentChecksum: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}content_checksum'],
      )!,
      contentPlain: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}content_plain'],
      ),
      abstract: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}abstract'],
      ),
      classification: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}classification'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
      deletedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}deleted_at'],
      ),
    );
  }

  @override
  TNote createAlias(String alias) {
    return TNote(attachedDatabase, alias);
  }

  @override
  bool get dontWriteConstraints => true;
}

class TNoteData extends DataClass implements Insertable<TNoteData> {
  final String id;
  final String? title;
  final String contentJson;
  final String contentChecksum;
  final String? contentPlain;
  final String? abstract;
  final String classification;
  final DateTime createdAt;
  final DateTime updatedAt;
  final DateTime? deletedAt;
  const TNoteData({
    required this.id,
    this.title,
    required this.contentJson,
    required this.contentChecksum,
    this.contentPlain,
    this.abstract,
    required this.classification,
    required this.createdAt,
    required this.updatedAt,
    this.deletedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    if (!nullToAbsent || title != null) {
      map['title'] = Variable<String>(title);
    }
    map['content_json'] = Variable<String>(contentJson);
    map['content_checksum'] = Variable<String>(contentChecksum);
    if (!nullToAbsent || contentPlain != null) {
      map['content_plain'] = Variable<String>(contentPlain);
    }
    if (!nullToAbsent || abstract != null) {
      map['abstract'] = Variable<String>(abstract);
    }
    map['classification'] = Variable<String>(classification);
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    if (!nullToAbsent || deletedAt != null) {
      map['deleted_at'] = Variable<DateTime>(deletedAt);
    }
    return map;
  }

  TNoteCompanion toCompanion(bool nullToAbsent) {
    return TNoteCompanion(
      id: Value(id),
      title: title == null && nullToAbsent
          ? const Value.absent()
          : Value(title),
      contentJson: Value(contentJson),
      contentChecksum: Value(contentChecksum),
      contentPlain: contentPlain == null && nullToAbsent
          ? const Value.absent()
          : Value(contentPlain),
      abstract: abstract == null && nullToAbsent
          ? const Value.absent()
          : Value(abstract),
      classification: Value(classification),
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
      deletedAt: deletedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(deletedAt),
    );
  }

  factory TNoteData.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return TNoteData(
      id: serializer.fromJson<String>(json['id']),
      title: serializer.fromJson<String?>(json['title']),
      contentJson: serializer.fromJson<String>(json['content_json']),
      contentChecksum: serializer.fromJson<String>(json['content_checksum']),
      contentPlain: serializer.fromJson<String?>(json['content_plain']),
      abstract: serializer.fromJson<String?>(json['abstract']),
      classification: serializer.fromJson<String>(json['classification']),
      createdAt: serializer.fromJson<DateTime>(json['created_at']),
      updatedAt: serializer.fromJson<DateTime>(json['updated_at']),
      deletedAt: serializer.fromJson<DateTime?>(json['deleted_at']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'title': serializer.toJson<String?>(title),
      'content_json': serializer.toJson<String>(contentJson),
      'content_checksum': serializer.toJson<String>(contentChecksum),
      'content_plain': serializer.toJson<String?>(contentPlain),
      'abstract': serializer.toJson<String?>(abstract),
      'classification': serializer.toJson<String>(classification),
      'created_at': serializer.toJson<DateTime>(createdAt),
      'updated_at': serializer.toJson<DateTime>(updatedAt),
      'deleted_at': serializer.toJson<DateTime?>(deletedAt),
    };
  }

  TNoteData copyWith({
    String? id,
    Value<String?> title = const Value.absent(),
    String? contentJson,
    String? contentChecksum,
    Value<String?> contentPlain = const Value.absent(),
    Value<String?> abstract = const Value.absent(),
    String? classification,
    DateTime? createdAt,
    DateTime? updatedAt,
    Value<DateTime?> deletedAt = const Value.absent(),
  }) => TNoteData(
    id: id ?? this.id,
    title: title.present ? title.value : this.title,
    contentJson: contentJson ?? this.contentJson,
    contentChecksum: contentChecksum ?? this.contentChecksum,
    contentPlain: contentPlain.present ? contentPlain.value : this.contentPlain,
    abstract: abstract.present ? abstract.value : this.abstract,
    classification: classification ?? this.classification,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
    deletedAt: deletedAt.present ? deletedAt.value : this.deletedAt,
  );
  TNoteData copyWithCompanion(TNoteCompanion data) {
    return TNoteData(
      id: data.id.present ? data.id.value : this.id,
      title: data.title.present ? data.title.value : this.title,
      contentJson: data.contentJson.present
          ? data.contentJson.value
          : this.contentJson,
      contentChecksum: data.contentChecksum.present
          ? data.contentChecksum.value
          : this.contentChecksum,
      contentPlain: data.contentPlain.present
          ? data.contentPlain.value
          : this.contentPlain,
      abstract: data.abstract.present ? data.abstract.value : this.abstract,
      classification: data.classification.present
          ? data.classification.value
          : this.classification,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      deletedAt: data.deletedAt.present ? data.deletedAt.value : this.deletedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('TNoteData(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('contentJson: $contentJson, ')
          ..write('contentChecksum: $contentChecksum, ')
          ..write('contentPlain: $contentPlain, ')
          ..write('abstract: $abstract, ')
          ..write('classification: $classification, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('deletedAt: $deletedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    title,
    contentJson,
    contentChecksum,
    contentPlain,
    abstract,
    classification,
    createdAt,
    updatedAt,
    deletedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is TNoteData &&
          other.id == this.id &&
          other.title == this.title &&
          other.contentJson == this.contentJson &&
          other.contentChecksum == this.contentChecksum &&
          other.contentPlain == this.contentPlain &&
          other.abstract == this.abstract &&
          other.classification == this.classification &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt &&
          other.deletedAt == this.deletedAt);
}

class TNoteCompanion extends UpdateCompanion<TNoteData> {
  final Value<String> id;
  final Value<String?> title;
  final Value<String> contentJson;
  final Value<String> contentChecksum;
  final Value<String?> contentPlain;
  final Value<String?> abstract;
  final Value<String> classification;
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  final Value<DateTime?> deletedAt;
  final Value<int> rowid;
  const TNoteCompanion({
    this.id = const Value.absent(),
    this.title = const Value.absent(),
    this.contentJson = const Value.absent(),
    this.contentChecksum = const Value.absent(),
    this.contentPlain = const Value.absent(),
    this.abstract = const Value.absent(),
    this.classification = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.deletedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  TNoteCompanion.insert({
    required String id,
    this.title = const Value.absent(),
    required String contentJson,
    required String contentChecksum,
    this.contentPlain = const Value.absent(),
    this.abstract = const Value.absent(),
    required String classification,
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.deletedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       contentJson = Value(contentJson),
       contentChecksum = Value(contentChecksum),
       classification = Value(classification);
  static Insertable<TNoteData> custom({
    Expression<String>? id,
    Expression<String>? title,
    Expression<String>? contentJson,
    Expression<String>? contentChecksum,
    Expression<String>? contentPlain,
    Expression<String>? abstract,
    Expression<String>? classification,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<DateTime>? deletedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (title != null) 'title': title,
      if (contentJson != null) 'content_json': contentJson,
      if (contentChecksum != null) 'content_checksum': contentChecksum,
      if (contentPlain != null) 'content_plain': contentPlain,
      if (abstract != null) 'abstract': abstract,
      if (classification != null) 'classification': classification,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (deletedAt != null) 'deleted_at': deletedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  TNoteCompanion copyWith({
    Value<String>? id,
    Value<String?>? title,
    Value<String>? contentJson,
    Value<String>? contentChecksum,
    Value<String?>? contentPlain,
    Value<String?>? abstract,
    Value<String>? classification,
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
    Value<DateTime?>? deletedAt,
    Value<int>? rowid,
  }) {
    return TNoteCompanion(
      id: id ?? this.id,
      title: title ?? this.title,
      contentJson: contentJson ?? this.contentJson,
      contentChecksum: contentChecksum ?? this.contentChecksum,
      contentPlain: contentPlain ?? this.contentPlain,
      abstract: abstract ?? this.abstract,
      classification: classification ?? this.classification,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      deletedAt: deletedAt ?? this.deletedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (contentJson.present) {
      map['content_json'] = Variable<String>(contentJson.value);
    }
    if (contentChecksum.present) {
      map['content_checksum'] = Variable<String>(contentChecksum.value);
    }
    if (contentPlain.present) {
      map['content_plain'] = Variable<String>(contentPlain.value);
    }
    if (abstract.present) {
      map['abstract'] = Variable<String>(abstract.value);
    }
    if (classification.present) {
      map['classification'] = Variable<String>(classification.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (deletedAt.present) {
      map['deleted_at'] = Variable<DateTime>(deletedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TNoteCompanion(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('contentJson: $contentJson, ')
          ..write('contentChecksum: $contentChecksum, ')
          ..write('contentPlain: $contentPlain, ')
          ..write('abstract: $abstract, ')
          ..write('classification: $classification, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('deletedAt: $deletedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class TNoteHistory extends Table
    with TableInfo<TNoteHistory, TNoteHistoryData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  TNoteHistory(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    $customConstraints: 'PRIMARY KEY AUTOINCREMENT',
  );
  static const VerificationMeta _noteIdMeta = const VerificationMeta('noteId');
  late final GeneratedColumn<String> noteId = GeneratedColumn<String>(
    'note_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _contentJsonMeta = const VerificationMeta(
    'contentJson',
  );
  late final GeneratedColumn<String> contentJson = GeneratedColumn<String>(
    'content_json',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _contentChecksumMeta = const VerificationMeta(
    'contentChecksum',
  );
  late final GeneratedColumn<String> contentChecksum = GeneratedColumn<String>(
    'content_checksum',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _contentPlainMeta = const VerificationMeta(
    'contentPlain',
  );
  late final GeneratedColumn<String> contentPlain = GeneratedColumn<String>(
    'content_plain',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    $customConstraints: 'NOT NULL',
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    $customConstraints: 'NOT NULL DEFAULT CURRENT_TIMESTAMP',
    defaultValue: const CustomExpression('CURRENT_TIMESTAMP'),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    noteId,
    contentJson,
    contentChecksum,
    contentPlain,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 't_note_history';
  @override
  VerificationContext validateIntegrity(
    Insertable<TNoteHistoryData> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('note_id')) {
      context.handle(
        _noteIdMeta,
        noteId.isAcceptableOrUnknown(data['note_id']!, _noteIdMeta),
      );
    } else if (isInserting) {
      context.missing(_noteIdMeta);
    }
    if (data.containsKey('content_json')) {
      context.handle(
        _contentJsonMeta,
        contentJson.isAcceptableOrUnknown(
          data['content_json']!,
          _contentJsonMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_contentJsonMeta);
    }
    if (data.containsKey('content_checksum')) {
      context.handle(
        _contentChecksumMeta,
        contentChecksum.isAcceptableOrUnknown(
          data['content_checksum']!,
          _contentChecksumMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_contentChecksumMeta);
    }
    if (data.containsKey('content_plain')) {
      context.handle(
        _contentPlainMeta,
        contentPlain.isAcceptableOrUnknown(
          data['content_plain']!,
          _contentPlainMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_contentPlainMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  TNoteHistoryData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return TNoteHistoryData(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      noteId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}note_id'],
      )!,
      contentJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}content_json'],
      )!,
      contentChecksum: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}content_checksum'],
      )!,
      contentPlain: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}content_plain'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  TNoteHistory createAlias(String alias) {
    return TNoteHistory(attachedDatabase, alias);
  }

  @override
  List<String> get customConstraints => const [
    'FOREIGN KEY(note_id)REFERENCES t_note(id)',
  ];
  @override
  bool get dontWriteConstraints => true;
}

class TNoteHistoryData extends DataClass
    implements Insertable<TNoteHistoryData> {
  final int id;
  final String noteId;
  final String contentJson;
  final String contentChecksum;
  final String contentPlain;
  final DateTime createdAt;
  const TNoteHistoryData({
    required this.id,
    required this.noteId,
    required this.contentJson,
    required this.contentChecksum,
    required this.contentPlain,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['note_id'] = Variable<String>(noteId);
    map['content_json'] = Variable<String>(contentJson);
    map['content_checksum'] = Variable<String>(contentChecksum);
    map['content_plain'] = Variable<String>(contentPlain);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  TNoteHistoryCompanion toCompanion(bool nullToAbsent) {
    return TNoteHistoryCompanion(
      id: Value(id),
      noteId: Value(noteId),
      contentJson: Value(contentJson),
      contentChecksum: Value(contentChecksum),
      contentPlain: Value(contentPlain),
      createdAt: Value(createdAt),
    );
  }

  factory TNoteHistoryData.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return TNoteHistoryData(
      id: serializer.fromJson<int>(json['id']),
      noteId: serializer.fromJson<String>(json['note_id']),
      contentJson: serializer.fromJson<String>(json['content_json']),
      contentChecksum: serializer.fromJson<String>(json['content_checksum']),
      contentPlain: serializer.fromJson<String>(json['content_plain']),
      createdAt: serializer.fromJson<DateTime>(json['created_at']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'note_id': serializer.toJson<String>(noteId),
      'content_json': serializer.toJson<String>(contentJson),
      'content_checksum': serializer.toJson<String>(contentChecksum),
      'content_plain': serializer.toJson<String>(contentPlain),
      'created_at': serializer.toJson<DateTime>(createdAt),
    };
  }

  TNoteHistoryData copyWith({
    int? id,
    String? noteId,
    String? contentJson,
    String? contentChecksum,
    String? contentPlain,
    DateTime? createdAt,
  }) => TNoteHistoryData(
    id: id ?? this.id,
    noteId: noteId ?? this.noteId,
    contentJson: contentJson ?? this.contentJson,
    contentChecksum: contentChecksum ?? this.contentChecksum,
    contentPlain: contentPlain ?? this.contentPlain,
    createdAt: createdAt ?? this.createdAt,
  );
  TNoteHistoryData copyWithCompanion(TNoteHistoryCompanion data) {
    return TNoteHistoryData(
      id: data.id.present ? data.id.value : this.id,
      noteId: data.noteId.present ? data.noteId.value : this.noteId,
      contentJson: data.contentJson.present
          ? data.contentJson.value
          : this.contentJson,
      contentChecksum: data.contentChecksum.present
          ? data.contentChecksum.value
          : this.contentChecksum,
      contentPlain: data.contentPlain.present
          ? data.contentPlain.value
          : this.contentPlain,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('TNoteHistoryData(')
          ..write('id: $id, ')
          ..write('noteId: $noteId, ')
          ..write('contentJson: $contentJson, ')
          ..write('contentChecksum: $contentChecksum, ')
          ..write('contentPlain: $contentPlain, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    noteId,
    contentJson,
    contentChecksum,
    contentPlain,
    createdAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is TNoteHistoryData &&
          other.id == this.id &&
          other.noteId == this.noteId &&
          other.contentJson == this.contentJson &&
          other.contentChecksum == this.contentChecksum &&
          other.contentPlain == this.contentPlain &&
          other.createdAt == this.createdAt);
}

class TNoteHistoryCompanion extends UpdateCompanion<TNoteHistoryData> {
  final Value<int> id;
  final Value<String> noteId;
  final Value<String> contentJson;
  final Value<String> contentChecksum;
  final Value<String> contentPlain;
  final Value<DateTime> createdAt;
  const TNoteHistoryCompanion({
    this.id = const Value.absent(),
    this.noteId = const Value.absent(),
    this.contentJson = const Value.absent(),
    this.contentChecksum = const Value.absent(),
    this.contentPlain = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  TNoteHistoryCompanion.insert({
    this.id = const Value.absent(),
    required String noteId,
    required String contentJson,
    required String contentChecksum,
    required String contentPlain,
    this.createdAt = const Value.absent(),
  }) : noteId = Value(noteId),
       contentJson = Value(contentJson),
       contentChecksum = Value(contentChecksum),
       contentPlain = Value(contentPlain);
  static Insertable<TNoteHistoryData> custom({
    Expression<int>? id,
    Expression<String>? noteId,
    Expression<String>? contentJson,
    Expression<String>? contentChecksum,
    Expression<String>? contentPlain,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (noteId != null) 'note_id': noteId,
      if (contentJson != null) 'content_json': contentJson,
      if (contentChecksum != null) 'content_checksum': contentChecksum,
      if (contentPlain != null) 'content_plain': contentPlain,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  TNoteHistoryCompanion copyWith({
    Value<int>? id,
    Value<String>? noteId,
    Value<String>? contentJson,
    Value<String>? contentChecksum,
    Value<String>? contentPlain,
    Value<DateTime>? createdAt,
  }) {
    return TNoteHistoryCompanion(
      id: id ?? this.id,
      noteId: noteId ?? this.noteId,
      contentJson: contentJson ?? this.contentJson,
      contentChecksum: contentChecksum ?? this.contentChecksum,
      contentPlain: contentPlain ?? this.contentPlain,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (noteId.present) {
      map['note_id'] = Variable<String>(noteId.value);
    }
    if (contentJson.present) {
      map['content_json'] = Variable<String>(contentJson.value);
    }
    if (contentChecksum.present) {
      map['content_checksum'] = Variable<String>(contentChecksum.value);
    }
    if (contentPlain.present) {
      map['content_plain'] = Variable<String>(contentPlain.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TNoteHistoryCompanion(')
          ..write('id: $id, ')
          ..write('noteId: $noteId, ')
          ..write('contentJson: $contentJson, ')
          ..write('contentChecksum: $contentChecksum, ')
          ..write('contentPlain: $contentPlain, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

abstract class _$SqliteDb extends GeneratedDatabase {
  _$SqliteDb(QueryExecutor e) : super(e);
  $SqliteDbManager get managers => $SqliteDbManager(this);
  late final Categories categories = Categories(this);
  late final TDataEncryptKey tDataEncryptKey = TDataEncryptKey(this);
  late final TEncryptedData tEncryptedData = TEncryptedData(this);
  late final TPassword tPassword = TPassword(this);
  late final TPasswordAttribute tPasswordAttribute = TPasswordAttribute(this);
  late final TPasswordHistory tPasswordHistory = TPasswordHistory(this);
  late final TNote tNote = TNote(this);
  late final TNoteHistory tNoteHistory = TNoteHistory(this);
  Selectable<SelectPasswordsResult> selectPasswords(
    SelectPasswords$order order,
  ) {
    var $arrayStartIndex = 1;
    final generatedorder = $write(
      order?.call(this.tPassword) ?? const OrderBy.nothing(),
      startIndex: $arrayStartIndex,
    );
    $arrayStartIndex += generatedorder.amountOfVariables;
    return customSelect(
      'SELECT id, type, classification, title, expire_time, category_id, created_at, updated_at FROM t_password WHERE deleted_at IS NULL ${generatedorder.sql}',
      variables: [...generatedorder.introducedVariables],
      readsFrom: {tPassword, ...generatedorder.watchedTables},
    ).map(
      (QueryRow row) => SelectPasswordsResult(
        id: row.read<String>('id'),
        type: row.read<int>('type'),
        classification: row.read<String>('classification'),
        title: row.readNullable<String>('title'),
        expireTime: row.readNullable<DateTime>('expire_time'),
        categoryId: row.read<int>('category_id'),
        createdAt: row.read<DateTime>('created_at'),
        updatedAt: row.read<DateTime>('updated_at'),
      ),
    );
  }

  Selectable<SelectNotesResult> selectNotes(SelectNotes$order order) {
    var $arrayStartIndex = 1;
    final generatedorder = $write(
      order?.call(this.tNote) ?? const OrderBy.nothing(),
      startIndex: $arrayStartIndex,
    );
    $arrayStartIndex += generatedorder.amountOfVariables;
    return customSelect(
      'SELECT id, classification, title, abstract, created_at, updated_at FROM t_note WHERE deleted_at IS NULL ${generatedorder.sql}',
      variables: [...generatedorder.introducedVariables],
      readsFrom: {tNote, ...generatedorder.watchedTables},
    ).map(
      (QueryRow row) => SelectNotesResult(
        id: row.read<String>('id'),
        classification: row.read<String>('classification'),
        title: row.readNullable<String>('title'),
        abstract: row.readNullable<String>('abstract'),
        createdAt: row.read<DateTime>('created_at'),
        updatedAt: row.read<DateTime>('updated_at'),
      ),
    );
  }

  Selectable<SearchNotesResult> searchNotes(String var1) {
    return customSelect(
      'SELECT n.id, n.title, n.classification, n.abstract, n.created_at, n.updated_at FROM t_note_idx AS i JOIN t_note AS n ON i."rowid" = n."rowid" WHERE t_note_idx MATCH ?1 AND n.deleted_at IS NULL ORDER BY rank',
      variables: [Variable<String>(var1)],
      readsFrom: {tNote},
    ).map(
      (QueryRow row) => SearchNotesResult(
        id: row.read<String>('id'),
        title: row.readNullable<String>('title'),
        classification: row.read<String>('classification'),
        abstract: row.readNullable<String>('abstract'),
        createdAt: row.read<DateTime>('created_at'),
        updatedAt: row.read<DateTime>('updated_at'),
      ),
    );
  }

  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
    categories,
    tDataEncryptKey,
    tEncryptedData,
    tPassword,
    tPasswordAttribute,
    tPasswordHistory,
    tNote,
    tNoteHistory,
  ];
}

typedef $CategoriesCreateCompanionBuilder =
    CategoriesCompanion Function({
      Value<int> id,
      required String name,
      required int accessLevel,
      required String createTime,
      required String lastUpdateTime,
    });
typedef $CategoriesUpdateCompanionBuilder =
    CategoriesCompanion Function({
      Value<int> id,
      Value<String> name,
      Value<int> accessLevel,
      Value<String> createTime,
      Value<String> lastUpdateTime,
    });

class $CategoriesFilterComposer extends Composer<_$SqliteDb, Categories> {
  $CategoriesFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get accessLevel => $composableBuilder(
    column: $table.accessLevel,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get createTime => $composableBuilder(
    column: $table.createTime,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get lastUpdateTime => $composableBuilder(
    column: $table.lastUpdateTime,
    builder: (column) => ColumnFilters(column),
  );
}

class $CategoriesOrderingComposer extends Composer<_$SqliteDb, Categories> {
  $CategoriesOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get accessLevel => $composableBuilder(
    column: $table.accessLevel,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get createTime => $composableBuilder(
    column: $table.createTime,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get lastUpdateTime => $composableBuilder(
    column: $table.lastUpdateTime,
    builder: (column) => ColumnOrderings(column),
  );
}

class $CategoriesAnnotationComposer extends Composer<_$SqliteDb, Categories> {
  $CategoriesAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get name =>
      $composableBuilder(column: $table.name, builder: (column) => column);

  GeneratedColumn<int> get accessLevel => $composableBuilder(
    column: $table.accessLevel,
    builder: (column) => column,
  );

  GeneratedColumn<String> get createTime => $composableBuilder(
    column: $table.createTime,
    builder: (column) => column,
  );

  GeneratedColumn<String> get lastUpdateTime => $composableBuilder(
    column: $table.lastUpdateTime,
    builder: (column) => column,
  );
}

class $CategoriesTableManager
    extends
        RootTableManager<
          _$SqliteDb,
          Categories,
          CategoryEntity,
          $CategoriesFilterComposer,
          $CategoriesOrderingComposer,
          $CategoriesAnnotationComposer,
          $CategoriesCreateCompanionBuilder,
          $CategoriesUpdateCompanionBuilder,
          (
            CategoryEntity,
            BaseReferences<_$SqliteDb, Categories, CategoryEntity>,
          ),
          CategoryEntity,
          PrefetchHooks Function()
        > {
  $CategoriesTableManager(_$SqliteDb db, Categories table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $CategoriesFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $CategoriesOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $CategoriesAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> name = const Value.absent(),
                Value<int> accessLevel = const Value.absent(),
                Value<String> createTime = const Value.absent(),
                Value<String> lastUpdateTime = const Value.absent(),
              }) => CategoriesCompanion(
                id: id,
                name: name,
                accessLevel: accessLevel,
                createTime: createTime,
                lastUpdateTime: lastUpdateTime,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String name,
                required int accessLevel,
                required String createTime,
                required String lastUpdateTime,
              }) => CategoriesCompanion.insert(
                id: id,
                name: name,
                accessLevel: accessLevel,
                createTime: createTime,
                lastUpdateTime: lastUpdateTime,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $CategoriesProcessedTableManager =
    ProcessedTableManager<
      _$SqliteDb,
      Categories,
      CategoryEntity,
      $CategoriesFilterComposer,
      $CategoriesOrderingComposer,
      $CategoriesAnnotationComposer,
      $CategoriesCreateCompanionBuilder,
      $CategoriesUpdateCompanionBuilder,
      (CategoryEntity, BaseReferences<_$SqliteDb, Categories, CategoryEntity>),
      CategoryEntity,
      PrefetchHooks Function()
    >;
typedef $TDataEncryptKeyCreateCompanionBuilder =
    TDataEncryptKeyCompanion Function({
      required String id,
      required String data,
      required String nonce,
      required String authTag,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<int> rowid,
    });
typedef $TDataEncryptKeyUpdateCompanionBuilder =
    TDataEncryptKeyCompanion Function({
      Value<String> id,
      Value<String> data,
      Value<String> nonce,
      Value<String> authTag,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<int> rowid,
    });

class $TDataEncryptKeyFilterComposer
    extends Composer<_$SqliteDb, TDataEncryptKey> {
  $TDataEncryptKeyFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get data => $composableBuilder(
    column: $table.data,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get nonce => $composableBuilder(
    column: $table.nonce,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get authTag => $composableBuilder(
    column: $table.authTag,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $TDataEncryptKeyOrderingComposer
    extends Composer<_$SqliteDb, TDataEncryptKey> {
  $TDataEncryptKeyOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get data => $composableBuilder(
    column: $table.data,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get nonce => $composableBuilder(
    column: $table.nonce,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get authTag => $composableBuilder(
    column: $table.authTag,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $TDataEncryptKeyAnnotationComposer
    extends Composer<_$SqliteDb, TDataEncryptKey> {
  $TDataEncryptKeyAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get data =>
      $composableBuilder(column: $table.data, builder: (column) => column);

  GeneratedColumn<String> get nonce =>
      $composableBuilder(column: $table.nonce, builder: (column) => column);

  GeneratedColumn<String> get authTag =>
      $composableBuilder(column: $table.authTag, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);
}

class $TDataEncryptKeyTableManager
    extends
        RootTableManager<
          _$SqliteDb,
          TDataEncryptKey,
          TDataEncryptKeyData,
          $TDataEncryptKeyFilterComposer,
          $TDataEncryptKeyOrderingComposer,
          $TDataEncryptKeyAnnotationComposer,
          $TDataEncryptKeyCreateCompanionBuilder,
          $TDataEncryptKeyUpdateCompanionBuilder,
          (
            TDataEncryptKeyData,
            BaseReferences<_$SqliteDb, TDataEncryptKey, TDataEncryptKeyData>,
          ),
          TDataEncryptKeyData,
          PrefetchHooks Function()
        > {
  $TDataEncryptKeyTableManager(_$SqliteDb db, TDataEncryptKey table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $TDataEncryptKeyFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $TDataEncryptKeyOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $TDataEncryptKeyAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> data = const Value.absent(),
                Value<String> nonce = const Value.absent(),
                Value<String> authTag = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TDataEncryptKeyCompanion(
                id: id,
                data: data,
                nonce: nonce,
                authTag: authTag,
                createdAt: createdAt,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String data,
                required String nonce,
                required String authTag,
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TDataEncryptKeyCompanion.insert(
                id: id,
                data: data,
                nonce: nonce,
                authTag: authTag,
                createdAt: createdAt,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $TDataEncryptKeyProcessedTableManager =
    ProcessedTableManager<
      _$SqliteDb,
      TDataEncryptKey,
      TDataEncryptKeyData,
      $TDataEncryptKeyFilterComposer,
      $TDataEncryptKeyOrderingComposer,
      $TDataEncryptKeyAnnotationComposer,
      $TDataEncryptKeyCreateCompanionBuilder,
      $TDataEncryptKeyUpdateCompanionBuilder,
      (
        TDataEncryptKeyData,
        BaseReferences<_$SqliteDb, TDataEncryptKey, TDataEncryptKeyData>,
      ),
      TDataEncryptKeyData,
      PrefetchHooks Function()
    >;
typedef $TEncryptedDataCreateCompanionBuilder =
    TEncryptedDataCompanion Function({
      required String id,
      required String dekId,
      required Uint8List content,
      required String algorithmId,
      required String authTag,
      required String nonce,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<DateTime?> deletedAt,
      Value<int> rowid,
    });
typedef $TEncryptedDataUpdateCompanionBuilder =
    TEncryptedDataCompanion Function({
      Value<String> id,
      Value<String> dekId,
      Value<Uint8List> content,
      Value<String> algorithmId,
      Value<String> authTag,
      Value<String> nonce,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<DateTime?> deletedAt,
      Value<int> rowid,
    });

class $TEncryptedDataFilterComposer
    extends Composer<_$SqliteDb, TEncryptedData> {
  $TEncryptedDataFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get dekId => $composableBuilder(
    column: $table.dekId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<Uint8List> get content => $composableBuilder(
    column: $table.content,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get algorithmId => $composableBuilder(
    column: $table.algorithmId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get authTag => $composableBuilder(
    column: $table.authTag,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get nonce => $composableBuilder(
    column: $table.nonce,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get deletedAt => $composableBuilder(
    column: $table.deletedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $TEncryptedDataOrderingComposer
    extends Composer<_$SqliteDb, TEncryptedData> {
  $TEncryptedDataOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get dekId => $composableBuilder(
    column: $table.dekId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<Uint8List> get content => $composableBuilder(
    column: $table.content,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get algorithmId => $composableBuilder(
    column: $table.algorithmId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get authTag => $composableBuilder(
    column: $table.authTag,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get nonce => $composableBuilder(
    column: $table.nonce,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get deletedAt => $composableBuilder(
    column: $table.deletedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $TEncryptedDataAnnotationComposer
    extends Composer<_$SqliteDb, TEncryptedData> {
  $TEncryptedDataAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get dekId =>
      $composableBuilder(column: $table.dekId, builder: (column) => column);

  GeneratedColumn<Uint8List> get content =>
      $composableBuilder(column: $table.content, builder: (column) => column);

  GeneratedColumn<String> get algorithmId => $composableBuilder(
    column: $table.algorithmId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get authTag =>
      $composableBuilder(column: $table.authTag, builder: (column) => column);

  GeneratedColumn<String> get nonce =>
      $composableBuilder(column: $table.nonce, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  GeneratedColumn<DateTime> get deletedAt =>
      $composableBuilder(column: $table.deletedAt, builder: (column) => column);
}

class $TEncryptedDataTableManager
    extends
        RootTableManager<
          _$SqliteDb,
          TEncryptedData,
          TEncryptedDataData,
          $TEncryptedDataFilterComposer,
          $TEncryptedDataOrderingComposer,
          $TEncryptedDataAnnotationComposer,
          $TEncryptedDataCreateCompanionBuilder,
          $TEncryptedDataUpdateCompanionBuilder,
          (
            TEncryptedDataData,
            BaseReferences<_$SqliteDb, TEncryptedData, TEncryptedDataData>,
          ),
          TEncryptedDataData,
          PrefetchHooks Function()
        > {
  $TEncryptedDataTableManager(_$SqliteDb db, TEncryptedData table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $TEncryptedDataFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $TEncryptedDataOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $TEncryptedDataAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> dekId = const Value.absent(),
                Value<Uint8List> content = const Value.absent(),
                Value<String> algorithmId = const Value.absent(),
                Value<String> authTag = const Value.absent(),
                Value<String> nonce = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<DateTime?> deletedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TEncryptedDataCompanion(
                id: id,
                dekId: dekId,
                content: content,
                algorithmId: algorithmId,
                authTag: authTag,
                nonce: nonce,
                createdAt: createdAt,
                updatedAt: updatedAt,
                deletedAt: deletedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String dekId,
                required Uint8List content,
                required String algorithmId,
                required String authTag,
                required String nonce,
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<DateTime?> deletedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TEncryptedDataCompanion.insert(
                id: id,
                dekId: dekId,
                content: content,
                algorithmId: algorithmId,
                authTag: authTag,
                nonce: nonce,
                createdAt: createdAt,
                updatedAt: updatedAt,
                deletedAt: deletedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $TEncryptedDataProcessedTableManager =
    ProcessedTableManager<
      _$SqliteDb,
      TEncryptedData,
      TEncryptedDataData,
      $TEncryptedDataFilterComposer,
      $TEncryptedDataOrderingComposer,
      $TEncryptedDataAnnotationComposer,
      $TEncryptedDataCreateCompanionBuilder,
      $TEncryptedDataUpdateCompanionBuilder,
      (
        TEncryptedDataData,
        BaseReferences<_$SqliteDb, TEncryptedData, TEncryptedDataData>,
      ),
      TEncryptedDataData,
      PrefetchHooks Function()
    >;
typedef $TPasswordCreateCompanionBuilder =
    TPasswordCompanion Function({
      required String id,
      Value<int> type,
      Value<int> categoryId,
      required String classification,
      Value<String?> title,
      required String encryptedDataId,
      Value<DateTime?> expireTime,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<DateTime?> deletedAt,
      Value<int> rowid,
    });
typedef $TPasswordUpdateCompanionBuilder =
    TPasswordCompanion Function({
      Value<String> id,
      Value<int> type,
      Value<int> categoryId,
      Value<String> classification,
      Value<String?> title,
      Value<String> encryptedDataId,
      Value<DateTime?> expireTime,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<DateTime?> deletedAt,
      Value<int> rowid,
    });

class $TPasswordFilterComposer extends Composer<_$SqliteDb, TPassword> {
  $TPasswordFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get type => $composableBuilder(
    column: $table.type,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get categoryId => $composableBuilder(
    column: $table.categoryId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get classification => $composableBuilder(
    column: $table.classification,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get encryptedDataId => $composableBuilder(
    column: $table.encryptedDataId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get expireTime => $composableBuilder(
    column: $table.expireTime,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get deletedAt => $composableBuilder(
    column: $table.deletedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $TPasswordOrderingComposer extends Composer<_$SqliteDb, TPassword> {
  $TPasswordOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get type => $composableBuilder(
    column: $table.type,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get categoryId => $composableBuilder(
    column: $table.categoryId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get classification => $composableBuilder(
    column: $table.classification,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get encryptedDataId => $composableBuilder(
    column: $table.encryptedDataId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get expireTime => $composableBuilder(
    column: $table.expireTime,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get deletedAt => $composableBuilder(
    column: $table.deletedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $TPasswordAnnotationComposer extends Composer<_$SqliteDb, TPassword> {
  $TPasswordAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<int> get type =>
      $composableBuilder(column: $table.type, builder: (column) => column);

  GeneratedColumn<int> get categoryId => $composableBuilder(
    column: $table.categoryId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get classification => $composableBuilder(
    column: $table.classification,
    builder: (column) => column,
  );

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get encryptedDataId => $composableBuilder(
    column: $table.encryptedDataId,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get expireTime => $composableBuilder(
    column: $table.expireTime,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  GeneratedColumn<DateTime> get deletedAt =>
      $composableBuilder(column: $table.deletedAt, builder: (column) => column);
}

class $TPasswordTableManager
    extends
        RootTableManager<
          _$SqliteDb,
          TPassword,
          TPasswordData,
          $TPasswordFilterComposer,
          $TPasswordOrderingComposer,
          $TPasswordAnnotationComposer,
          $TPasswordCreateCompanionBuilder,
          $TPasswordUpdateCompanionBuilder,
          (TPasswordData, BaseReferences<_$SqliteDb, TPassword, TPasswordData>),
          TPasswordData,
          PrefetchHooks Function()
        > {
  $TPasswordTableManager(_$SqliteDb db, TPassword table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $TPasswordFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $TPasswordOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $TPasswordAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<int> type = const Value.absent(),
                Value<int> categoryId = const Value.absent(),
                Value<String> classification = const Value.absent(),
                Value<String?> title = const Value.absent(),
                Value<String> encryptedDataId = const Value.absent(),
                Value<DateTime?> expireTime = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<DateTime?> deletedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TPasswordCompanion(
                id: id,
                type: type,
                categoryId: categoryId,
                classification: classification,
                title: title,
                encryptedDataId: encryptedDataId,
                expireTime: expireTime,
                createdAt: createdAt,
                updatedAt: updatedAt,
                deletedAt: deletedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                Value<int> type = const Value.absent(),
                Value<int> categoryId = const Value.absent(),
                required String classification,
                Value<String?> title = const Value.absent(),
                required String encryptedDataId,
                Value<DateTime?> expireTime = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<DateTime?> deletedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TPasswordCompanion.insert(
                id: id,
                type: type,
                categoryId: categoryId,
                classification: classification,
                title: title,
                encryptedDataId: encryptedDataId,
                expireTime: expireTime,
                createdAt: createdAt,
                updatedAt: updatedAt,
                deletedAt: deletedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $TPasswordProcessedTableManager =
    ProcessedTableManager<
      _$SqliteDb,
      TPassword,
      TPasswordData,
      $TPasswordFilterComposer,
      $TPasswordOrderingComposer,
      $TPasswordAnnotationComposer,
      $TPasswordCreateCompanionBuilder,
      $TPasswordUpdateCompanionBuilder,
      (TPasswordData, BaseReferences<_$SqliteDb, TPassword, TPasswordData>),
      TPasswordData,
      PrefetchHooks Function()
    >;
typedef $TPasswordAttributeCreateCompanionBuilder =
    TPasswordAttributeCompanion Function({
      Value<int> id,
      required String passwordId,
      required String classification,
      required String name,
      Value<String?> value,
      Value<String?> encryptedDataId,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
    });
typedef $TPasswordAttributeUpdateCompanionBuilder =
    TPasswordAttributeCompanion Function({
      Value<int> id,
      Value<String> passwordId,
      Value<String> classification,
      Value<String> name,
      Value<String?> value,
      Value<String?> encryptedDataId,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
    });

class $TPasswordAttributeFilterComposer
    extends Composer<_$SqliteDb, TPasswordAttribute> {
  $TPasswordAttributeFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get passwordId => $composableBuilder(
    column: $table.passwordId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get classification => $composableBuilder(
    column: $table.classification,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get value => $composableBuilder(
    column: $table.value,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get encryptedDataId => $composableBuilder(
    column: $table.encryptedDataId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $TPasswordAttributeOrderingComposer
    extends Composer<_$SqliteDb, TPasswordAttribute> {
  $TPasswordAttributeOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get passwordId => $composableBuilder(
    column: $table.passwordId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get classification => $composableBuilder(
    column: $table.classification,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get value => $composableBuilder(
    column: $table.value,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get encryptedDataId => $composableBuilder(
    column: $table.encryptedDataId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $TPasswordAttributeAnnotationComposer
    extends Composer<_$SqliteDb, TPasswordAttribute> {
  $TPasswordAttributeAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get passwordId => $composableBuilder(
    column: $table.passwordId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get classification => $composableBuilder(
    column: $table.classification,
    builder: (column) => column,
  );

  GeneratedColumn<String> get name =>
      $composableBuilder(column: $table.name, builder: (column) => column);

  GeneratedColumn<String> get value =>
      $composableBuilder(column: $table.value, builder: (column) => column);

  GeneratedColumn<String> get encryptedDataId => $composableBuilder(
    column: $table.encryptedDataId,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);
}

class $TPasswordAttributeTableManager
    extends
        RootTableManager<
          _$SqliteDb,
          TPasswordAttribute,
          TPasswordAttributeData,
          $TPasswordAttributeFilterComposer,
          $TPasswordAttributeOrderingComposer,
          $TPasswordAttributeAnnotationComposer,
          $TPasswordAttributeCreateCompanionBuilder,
          $TPasswordAttributeUpdateCompanionBuilder,
          (
            TPasswordAttributeData,
            BaseReferences<
              _$SqliteDb,
              TPasswordAttribute,
              TPasswordAttributeData
            >,
          ),
          TPasswordAttributeData,
          PrefetchHooks Function()
        > {
  $TPasswordAttributeTableManager(_$SqliteDb db, TPasswordAttribute table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $TPasswordAttributeFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $TPasswordAttributeOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $TPasswordAttributeAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> passwordId = const Value.absent(),
                Value<String> classification = const Value.absent(),
                Value<String> name = const Value.absent(),
                Value<String?> value = const Value.absent(),
                Value<String?> encryptedDataId = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
              }) => TPasswordAttributeCompanion(
                id: id,
                passwordId: passwordId,
                classification: classification,
                name: name,
                value: value,
                encryptedDataId: encryptedDataId,
                createdAt: createdAt,
                updatedAt: updatedAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String passwordId,
                required String classification,
                required String name,
                Value<String?> value = const Value.absent(),
                Value<String?> encryptedDataId = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
              }) => TPasswordAttributeCompanion.insert(
                id: id,
                passwordId: passwordId,
                classification: classification,
                name: name,
                value: value,
                encryptedDataId: encryptedDataId,
                createdAt: createdAt,
                updatedAt: updatedAt,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $TPasswordAttributeProcessedTableManager =
    ProcessedTableManager<
      _$SqliteDb,
      TPasswordAttribute,
      TPasswordAttributeData,
      $TPasswordAttributeFilterComposer,
      $TPasswordAttributeOrderingComposer,
      $TPasswordAttributeAnnotationComposer,
      $TPasswordAttributeCreateCompanionBuilder,
      $TPasswordAttributeUpdateCompanionBuilder,
      (
        TPasswordAttributeData,
        BaseReferences<_$SqliteDb, TPasswordAttribute, TPasswordAttributeData>,
      ),
      TPasswordAttributeData,
      PrefetchHooks Function()
    >;
typedef $TPasswordHistoryCreateCompanionBuilder =
    TPasswordHistoryCompanion Function({
      Value<int> id,
      required String passwordId,
      required String encryptedDataId,
      Value<DateTime> createdAt,
    });
typedef $TPasswordHistoryUpdateCompanionBuilder =
    TPasswordHistoryCompanion Function({
      Value<int> id,
      Value<String> passwordId,
      Value<String> encryptedDataId,
      Value<DateTime> createdAt,
    });

class $TPasswordHistoryFilterComposer
    extends Composer<_$SqliteDb, TPasswordHistory> {
  $TPasswordHistoryFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get passwordId => $composableBuilder(
    column: $table.passwordId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get encryptedDataId => $composableBuilder(
    column: $table.encryptedDataId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $TPasswordHistoryOrderingComposer
    extends Composer<_$SqliteDb, TPasswordHistory> {
  $TPasswordHistoryOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get passwordId => $composableBuilder(
    column: $table.passwordId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get encryptedDataId => $composableBuilder(
    column: $table.encryptedDataId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $TPasswordHistoryAnnotationComposer
    extends Composer<_$SqliteDb, TPasswordHistory> {
  $TPasswordHistoryAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get passwordId => $composableBuilder(
    column: $table.passwordId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get encryptedDataId => $composableBuilder(
    column: $table.encryptedDataId,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);
}

class $TPasswordHistoryTableManager
    extends
        RootTableManager<
          _$SqliteDb,
          TPasswordHistory,
          TPasswordHistoryData,
          $TPasswordHistoryFilterComposer,
          $TPasswordHistoryOrderingComposer,
          $TPasswordHistoryAnnotationComposer,
          $TPasswordHistoryCreateCompanionBuilder,
          $TPasswordHistoryUpdateCompanionBuilder,
          (
            TPasswordHistoryData,
            BaseReferences<_$SqliteDb, TPasswordHistory, TPasswordHistoryData>,
          ),
          TPasswordHistoryData,
          PrefetchHooks Function()
        > {
  $TPasswordHistoryTableManager(_$SqliteDb db, TPasswordHistory table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $TPasswordHistoryFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $TPasswordHistoryOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $TPasswordHistoryAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> passwordId = const Value.absent(),
                Value<String> encryptedDataId = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => TPasswordHistoryCompanion(
                id: id,
                passwordId: passwordId,
                encryptedDataId: encryptedDataId,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String passwordId,
                required String encryptedDataId,
                Value<DateTime> createdAt = const Value.absent(),
              }) => TPasswordHistoryCompanion.insert(
                id: id,
                passwordId: passwordId,
                encryptedDataId: encryptedDataId,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $TPasswordHistoryProcessedTableManager =
    ProcessedTableManager<
      _$SqliteDb,
      TPasswordHistory,
      TPasswordHistoryData,
      $TPasswordHistoryFilterComposer,
      $TPasswordHistoryOrderingComposer,
      $TPasswordHistoryAnnotationComposer,
      $TPasswordHistoryCreateCompanionBuilder,
      $TPasswordHistoryUpdateCompanionBuilder,
      (
        TPasswordHistoryData,
        BaseReferences<_$SqliteDb, TPasswordHistory, TPasswordHistoryData>,
      ),
      TPasswordHistoryData,
      PrefetchHooks Function()
    >;
typedef $TNoteCreateCompanionBuilder =
    TNoteCompanion Function({
      required String id,
      Value<String?> title,
      required String contentJson,
      required String contentChecksum,
      Value<String?> contentPlain,
      Value<String?> abstract,
      required String classification,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<DateTime?> deletedAt,
      Value<int> rowid,
    });
typedef $TNoteUpdateCompanionBuilder =
    TNoteCompanion Function({
      Value<String> id,
      Value<String?> title,
      Value<String> contentJson,
      Value<String> contentChecksum,
      Value<String?> contentPlain,
      Value<String?> abstract,
      Value<String> classification,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<DateTime?> deletedAt,
      Value<int> rowid,
    });

class $TNoteFilterComposer extends Composer<_$SqliteDb, TNote> {
  $TNoteFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get contentJson => $composableBuilder(
    column: $table.contentJson,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get contentChecksum => $composableBuilder(
    column: $table.contentChecksum,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get contentPlain => $composableBuilder(
    column: $table.contentPlain,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get abstract => $composableBuilder(
    column: $table.abstract,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get classification => $composableBuilder(
    column: $table.classification,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get deletedAt => $composableBuilder(
    column: $table.deletedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $TNoteOrderingComposer extends Composer<_$SqliteDb, TNote> {
  $TNoteOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get contentJson => $composableBuilder(
    column: $table.contentJson,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get contentChecksum => $composableBuilder(
    column: $table.contentChecksum,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get contentPlain => $composableBuilder(
    column: $table.contentPlain,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get abstract => $composableBuilder(
    column: $table.abstract,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get classification => $composableBuilder(
    column: $table.classification,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get deletedAt => $composableBuilder(
    column: $table.deletedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $TNoteAnnotationComposer extends Composer<_$SqliteDb, TNote> {
  $TNoteAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get contentJson => $composableBuilder(
    column: $table.contentJson,
    builder: (column) => column,
  );

  GeneratedColumn<String> get contentChecksum => $composableBuilder(
    column: $table.contentChecksum,
    builder: (column) => column,
  );

  GeneratedColumn<String> get contentPlain => $composableBuilder(
    column: $table.contentPlain,
    builder: (column) => column,
  );

  GeneratedColumn<String> get abstract =>
      $composableBuilder(column: $table.abstract, builder: (column) => column);

  GeneratedColumn<String> get classification => $composableBuilder(
    column: $table.classification,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  GeneratedColumn<DateTime> get deletedAt =>
      $composableBuilder(column: $table.deletedAt, builder: (column) => column);
}

class $TNoteTableManager
    extends
        RootTableManager<
          _$SqliteDb,
          TNote,
          TNoteData,
          $TNoteFilterComposer,
          $TNoteOrderingComposer,
          $TNoteAnnotationComposer,
          $TNoteCreateCompanionBuilder,
          $TNoteUpdateCompanionBuilder,
          (TNoteData, BaseReferences<_$SqliteDb, TNote, TNoteData>),
          TNoteData,
          PrefetchHooks Function()
        > {
  $TNoteTableManager(_$SqliteDb db, TNote table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $TNoteFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $TNoteOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $TNoteAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String?> title = const Value.absent(),
                Value<String> contentJson = const Value.absent(),
                Value<String> contentChecksum = const Value.absent(),
                Value<String?> contentPlain = const Value.absent(),
                Value<String?> abstract = const Value.absent(),
                Value<String> classification = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<DateTime?> deletedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TNoteCompanion(
                id: id,
                title: title,
                contentJson: contentJson,
                contentChecksum: contentChecksum,
                contentPlain: contentPlain,
                abstract: abstract,
                classification: classification,
                createdAt: createdAt,
                updatedAt: updatedAt,
                deletedAt: deletedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                Value<String?> title = const Value.absent(),
                required String contentJson,
                required String contentChecksum,
                Value<String?> contentPlain = const Value.absent(),
                Value<String?> abstract = const Value.absent(),
                required String classification,
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<DateTime?> deletedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TNoteCompanion.insert(
                id: id,
                title: title,
                contentJson: contentJson,
                contentChecksum: contentChecksum,
                contentPlain: contentPlain,
                abstract: abstract,
                classification: classification,
                createdAt: createdAt,
                updatedAt: updatedAt,
                deletedAt: deletedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $TNoteProcessedTableManager =
    ProcessedTableManager<
      _$SqliteDb,
      TNote,
      TNoteData,
      $TNoteFilterComposer,
      $TNoteOrderingComposer,
      $TNoteAnnotationComposer,
      $TNoteCreateCompanionBuilder,
      $TNoteUpdateCompanionBuilder,
      (TNoteData, BaseReferences<_$SqliteDb, TNote, TNoteData>),
      TNoteData,
      PrefetchHooks Function()
    >;
typedef $TNoteHistoryCreateCompanionBuilder =
    TNoteHistoryCompanion Function({
      Value<int> id,
      required String noteId,
      required String contentJson,
      required String contentChecksum,
      required String contentPlain,
      Value<DateTime> createdAt,
    });
typedef $TNoteHistoryUpdateCompanionBuilder =
    TNoteHistoryCompanion Function({
      Value<int> id,
      Value<String> noteId,
      Value<String> contentJson,
      Value<String> contentChecksum,
      Value<String> contentPlain,
      Value<DateTime> createdAt,
    });

class $TNoteHistoryFilterComposer extends Composer<_$SqliteDb, TNoteHistory> {
  $TNoteHistoryFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get noteId => $composableBuilder(
    column: $table.noteId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get contentJson => $composableBuilder(
    column: $table.contentJson,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get contentChecksum => $composableBuilder(
    column: $table.contentChecksum,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get contentPlain => $composableBuilder(
    column: $table.contentPlain,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $TNoteHistoryOrderingComposer extends Composer<_$SqliteDb, TNoteHistory> {
  $TNoteHistoryOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get noteId => $composableBuilder(
    column: $table.noteId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get contentJson => $composableBuilder(
    column: $table.contentJson,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get contentChecksum => $composableBuilder(
    column: $table.contentChecksum,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get contentPlain => $composableBuilder(
    column: $table.contentPlain,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $TNoteHistoryAnnotationComposer
    extends Composer<_$SqliteDb, TNoteHistory> {
  $TNoteHistoryAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get noteId =>
      $composableBuilder(column: $table.noteId, builder: (column) => column);

  GeneratedColumn<String> get contentJson => $composableBuilder(
    column: $table.contentJson,
    builder: (column) => column,
  );

  GeneratedColumn<String> get contentChecksum => $composableBuilder(
    column: $table.contentChecksum,
    builder: (column) => column,
  );

  GeneratedColumn<String> get contentPlain => $composableBuilder(
    column: $table.contentPlain,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);
}

class $TNoteHistoryTableManager
    extends
        RootTableManager<
          _$SqliteDb,
          TNoteHistory,
          TNoteHistoryData,
          $TNoteHistoryFilterComposer,
          $TNoteHistoryOrderingComposer,
          $TNoteHistoryAnnotationComposer,
          $TNoteHistoryCreateCompanionBuilder,
          $TNoteHistoryUpdateCompanionBuilder,
          (
            TNoteHistoryData,
            BaseReferences<_$SqliteDb, TNoteHistory, TNoteHistoryData>,
          ),
          TNoteHistoryData,
          PrefetchHooks Function()
        > {
  $TNoteHistoryTableManager(_$SqliteDb db, TNoteHistory table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $TNoteHistoryFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $TNoteHistoryOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $TNoteHistoryAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> noteId = const Value.absent(),
                Value<String> contentJson = const Value.absent(),
                Value<String> contentChecksum = const Value.absent(),
                Value<String> contentPlain = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => TNoteHistoryCompanion(
                id: id,
                noteId: noteId,
                contentJson: contentJson,
                contentChecksum: contentChecksum,
                contentPlain: contentPlain,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String noteId,
                required String contentJson,
                required String contentChecksum,
                required String contentPlain,
                Value<DateTime> createdAt = const Value.absent(),
              }) => TNoteHistoryCompanion.insert(
                id: id,
                noteId: noteId,
                contentJson: contentJson,
                contentChecksum: contentChecksum,
                contentPlain: contentPlain,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $TNoteHistoryProcessedTableManager =
    ProcessedTableManager<
      _$SqliteDb,
      TNoteHistory,
      TNoteHistoryData,
      $TNoteHistoryFilterComposer,
      $TNoteHistoryOrderingComposer,
      $TNoteHistoryAnnotationComposer,
      $TNoteHistoryCreateCompanionBuilder,
      $TNoteHistoryUpdateCompanionBuilder,
      (
        TNoteHistoryData,
        BaseReferences<_$SqliteDb, TNoteHistory, TNoteHistoryData>,
      ),
      TNoteHistoryData,
      PrefetchHooks Function()
    >;

class $SqliteDbManager {
  final _$SqliteDb _db;
  $SqliteDbManager(this._db);
  $CategoriesTableManager get categories =>
      $CategoriesTableManager(_db, _db.categories);
  $TDataEncryptKeyTableManager get tDataEncryptKey =>
      $TDataEncryptKeyTableManager(_db, _db.tDataEncryptKey);
  $TEncryptedDataTableManager get tEncryptedData =>
      $TEncryptedDataTableManager(_db, _db.tEncryptedData);
  $TPasswordTableManager get tPassword =>
      $TPasswordTableManager(_db, _db.tPassword);
  $TPasswordAttributeTableManager get tPasswordAttribute =>
      $TPasswordAttributeTableManager(_db, _db.tPasswordAttribute);
  $TPasswordHistoryTableManager get tPasswordHistory =>
      $TPasswordHistoryTableManager(_db, _db.tPasswordHistory);
  $TNoteTableManager get tNote => $TNoteTableManager(_db, _db.tNote);
  $TNoteHistoryTableManager get tNoteHistory =>
      $TNoteHistoryTableManager(_db, _db.tNoteHistory);
}

class SelectPasswordsResult {
  final String id;
  final int type;
  final String classification;
  final String? title;
  final DateTime? expireTime;
  final int categoryId;
  final DateTime createdAt;
  final DateTime updatedAt;
  SelectPasswordsResult({
    required this.id,
    required this.type,
    required this.classification,
    this.title,
    this.expireTime,
    required this.categoryId,
    required this.createdAt,
    required this.updatedAt,
  });
}

typedef SelectPasswords$order = OrderBy Function(TPassword t_password);

class SelectNotesResult {
  final String id;
  final String classification;
  final String? title;
  final String? abstract;
  final DateTime createdAt;
  final DateTime updatedAt;
  SelectNotesResult({
    required this.id,
    required this.classification,
    this.title,
    this.abstract,
    required this.createdAt,
    required this.updatedAt,
  });
}

typedef SelectNotes$order = OrderBy Function(TNote t_note);

class SearchNotesResult {
  final String id;
  final String? title;
  final String classification;
  final String? abstract;
  final DateTime createdAt;
  final DateTime updatedAt;
  SearchNotesResult({
    required this.id,
    this.title,
    required this.classification,
    this.abstract,
    required this.createdAt,
    required this.updatedAt,
  });
}
