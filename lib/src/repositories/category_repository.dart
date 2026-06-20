import 'package:cryptowl/src/repositories/base_repository.dart';
import 'package:drift/drift.dart';

import '../domain/category.dart';

class CategoryRepository extends SqlcipherRepository {
  CategoryRepository(super.ref);

  Future<List<Category>> list() async {
    final db = await requireDb();
    final items = await db.categories.select().get();
    return items.map((item) => Category.fromEntity(item)).toList();
  }
}
