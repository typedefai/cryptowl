import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class CategoryItem extends ConsumerWidget {
  final String name;
  final IconData icon;
  final int category;
  const CategoryItem(
      {super.key, required this.name, required this.icon, this.category = 0});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // final selectedCategory = ref.watch(selectedCategoryProvider);
    // final selectedCategoryNotifier =
    //     ref.read(selectedCategoryProvider.notifier);
    // final isActive = selectedCategory == category;
    return ListTile(
      dense: true,
      contentPadding: EdgeInsets.only(right: 10),
      visualDensity: VisualDensity(horizontal: 0, vertical: -4),
      leading: Icon(icon),
      title: Text(name),
      //tileColor: isActive ? Theme.of(context).highlightColor : null,
      onTap: () {
        //selectedCategoryNotifier.setSelectedCategory(category);
      },
    );
  }
}
