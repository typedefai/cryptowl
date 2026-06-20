import '../database/database.dart';

class Category {
  int? id;
  String name;
  int accessLevel;
  DateTime createTime;
  DateTime lastUpdateTime;

  Category(
      {this.id,
      required this.name,
      required this.accessLevel,
      required this.createTime,
      required this.lastUpdateTime});

  static Category fromEntity(CategoryEntity entity) {
    return Category(
      id: entity.id,
      name: entity.name,
      accessLevel: entity.accessLevel,
      createTime: DateTime.parse(entity.createTime),
      lastUpdateTime: DateTime.parse(entity.lastUpdateTime),
    );
  }
}
