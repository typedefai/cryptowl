import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../providers/providers.dart';

class FilterDrawer extends ConsumerWidget {
  const FilterDrawer({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final selectedFilters = ref.watch(passwordFilterProvider);
    final filterNotifier = ref.read(passwordFilterProvider.notifier);

    return Drawer(
      child: ListView(
        padding: EdgeInsets.fromLTRB(8, 16, 8, 16),
        children: [
          ListTile(
            leading: Icon(Icons.filter_alt),
            title: Text("Filters"),
          ),
          Divider(),
          CheckboxListTile(
            dense: true,
            title: const Text('Top secret'),
            value: selectedFilters.contains(PasswordFilter.topSecret),
            onChanged: (bool? value) {
              filterNotifier.select(PasswordFilter.topSecret);
            },
          ),
          CheckboxListTile(
            dense: true,
            title: const Text('Secret'),
            value: selectedFilters.contains(PasswordFilter.secret),
            onChanged: (bool? value) {
              filterNotifier.select(PasswordFilter.secret);
            },
          ),
          CheckboxListTile(
            dense: true,
            title: const Text('Confidential'),
            //value: filterState.confidential,
            value: selectedFilters.contains(PasswordFilter.confidential),
            onChanged: (bool? value) {
              filterNotifier.select(PasswordFilter.confidential);
            },
          ),
          Divider(),
          CheckboxListTile(
            dense: true,
            title: const Text('Show deleted items'),
            value: selectedFilters.contains(PasswordFilter.deleted),
            onChanged: (bool? value) {
              filterNotifier.select(PasswordFilter.deleted);
            },
          ),
          Divider(),
          TextButton(
              onPressed: () {
                filterNotifier.clear();
              },
              child: Text("Reset filters")),
        ],
      ),
    );
  }
}
