import 'dart:async';

import 'package:cryptowl/src/domain/password.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:logging/logging.dart';

import 'repositories.dart';

final _logger = Logger("PasswordNotifiers");

class AsyncPasswordsNotifier extends AsyncNotifier<List<PasswordBasic>> {
  @override
  FutureOr<List<PasswordBasic>> build() {
    final filters = ref.watch(passwordFilterProvider);
    final includeDeleted = filters.contains(PasswordFilter.deleted);
    final classifications = <int>[];
    if (filters.contains(PasswordFilter.topSecret)) {
      classifications.add(1);
    }
    if (filters.contains(PasswordFilter.secret)) {
      classifications.add(1);
    }
    if (filters.contains(PasswordFilter.confidential)) {
      classifications.add(1);
    }
    // FIXME:
    return ref.read(passwordRepositoryProvider).list();
  }
}

final passwordsProvider =
    AsyncNotifierProvider<AsyncPasswordsNotifier, List<PasswordBasic>>(() {
  return AsyncPasswordsNotifier();
});

final passwordDetailProvider =
    FutureProvider.autoDispose.family<Password, String>((ref, id) async {
  _logger.fine("Fetching password detail for $id");
  return ref.read(passwordServiceProvider).getPasswordDetail(id);
});

enum PasswordFilter { topSecret, secret, confidential, deleted }

const defaultFilters = [
  PasswordFilter.topSecret,
  PasswordFilter.secret,
  PasswordFilter.confidential
];

class PasswordFilterNotifier extends StateNotifier<List<PasswordFilter>> {
  PasswordFilterNotifier() : super(defaultFilters);

  Future<void> select(PasswordFilter option) async {
    state = check(state, option);
  }

  Future<void> clear() async {
    state = [...defaultFilters];
  }

  List<PasswordFilter> check(
      List<PasswordFilter> selected, PasswordFilter option) {
    if (selected.contains(option)) {
      final tmp = List<PasswordFilter>.from(selected);
      tmp.remove(option);
      return [...tmp];
    } else {
      return [option, ...selected];
    }
  }
}

final passwordFilterProvider =
    StateNotifierProvider<PasswordFilterNotifier, List<PasswordFilter>>((ref) {
  return PasswordFilterNotifier();
});
