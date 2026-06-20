import 'package:cryptowl/src/providers/repositories.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:logging/logging.dart';

import '../crypto/protected_value.dart';
import '../domain/user.dart';

class OnboardingNotifier extends StateNotifier<AsyncValue<bool>> {
  final Ref ref;
  final logger = Logger("OnboardingNotifier");

  OnboardingNotifier(this.ref) : super(const AsyncValue.loading()) {
    check();
  }

  Future<void> check() async {
    logger.fine("check() called");
    state = const AsyncValue.loading();
    state = await AsyncValue.guard(() async {
      final result = await ref.read(appServiceProvider).isInitialized();
      logger.fine("check() result: isInitialized=$result");
      return result;
    });
    if (state.hasError) {
      logger.severe("check() error: ${state.error}");
    }
  }

  Future<void> completeOnboarding(ProtectedValue password, String? hint,
      {ProtectedValue? secondaryPassword}) async {
    logger.info("completeOnboarding() called");
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      logger.fine("completeOnboarding: calling initialize...");
      await ref.read(appServiceProvider).initialize(password, hint,
          secondaryPassword: secondaryPassword);
      logger.fine("completeOnboarding: initialize done, checking isInitialized...");
      final result = await ref.read(appServiceProvider).isInitialized();
      logger.fine("completeOnboarding: isInitialized=$result");
      return result;
    });
    if (state.hasError) {
      logger.severe("completeOnboarding() error: ${state.error}");
    } else {
      logger.info("completeOnboarding() success: ${state.value}");
    }
  }
}

final onboardingProvider =
    StateNotifierProvider<OnboardingNotifier, AsyncValue<bool>>((ref) {
  return OnboardingNotifier(ref);
});

class AsyncLoginNotifier extends AsyncNotifier<Session?> {
  final logger = Logger("AsyncLoginNotifier");

  @override
  Future<Session?> build() async {
    return null;
  }

  Future<void> login(String instanceId, ProtectedValue password) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final session =
          await ref.read(appServiceProvider).login(instanceId, password);

      ref.onDispose(() {
        logger.fine("Disposing db...");
        session.sqliteDb.close();
      });
      return session;
    });
  }

  Future<void> unlockTopSecret(ProtectedValue secondaryPassword) async {
    final currentSession = state.valueOrNull;
    if (currentSession == null) {
      throw Exception("Not logged in");
    }
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      return ref
          .read(appServiceProvider)
          .unlockSecondaryKey(currentSession, secondaryPassword);
    });
  }

  Future<void> logout() async {
    logger.fine("Logging out...");
    final currentSession = state.valueOrNull;
    if (currentSession != null) {
      logger.fine("Disposing db...");
      await currentSession.sqliteDb.close();
    }
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      return null;
    });
  }
}

final asyncLoginProvider =
    AsyncNotifierProvider<AsyncLoginNotifier, Session?>(() {
  return AsyncLoginNotifier();
});
