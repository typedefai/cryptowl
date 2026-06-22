import 'dart:async';
import 'dart:typed_data';

import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:cryptowl/src/domain/password.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:logging/logging.dart';

import 'credentials.dart';
import 'repositories.dart';

final _logger = Logger("PasswordNotifiers");

class AsyncPasswordsNotifier extends AsyncNotifier<List<PasswordBasic>> {
  @override
  FutureOr<List<PasswordBasic>> build() {
    final filters = ref.watch(passwordFilterProvider);
    // FIXME: apply actual filters
    return ref.read(passwordRepositoryProvider).list();
  }
}

final passwordsProvider =
    AsyncNotifierProvider<AsyncPasswordsNotifier, List<PasswordBasic>>(() {
  return AsyncPasswordsNotifier();
});

final passwordDetailProvider =
    FutureProvider.family<Password, String>((ref, id) async {
  _logger.info("[Provider] passwordDetailProvider called for $id");
  final session = ref.watch(asyncLoginProvider).valueOrNull;
  if (session == null) {
    throw Exception("Not logged in");
  }
  final kek = ProtectedValue.fromBinary(
      Uint8List.sublistView(session.symmetricKey.binaryValue, 0, 32));
  _logger.info("[Provider] Fetching password detail from service for $id");
  final result = await ref.read(passwordServiceProvider).getPasswordDetail(id, kek,
      topSecretKek: session.secondaryKey);
  _logger.info("[Provider] Got password detail for $id: title=${result.title}, value length=${result.value.binaryValue.length}");
  return result;
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
