package com.typedefai.cryptowl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.typedefai.cryptowl.onboarding.BiometricSetupScreen
import com.typedefai.cryptowl.onboarding.IntroScreen
import com.typedefai.cryptowl.onboarding.MasterPasswordScreen
import com.typedefai.cryptowl.ui.theme.CryptowlTheme
import com.typedefai.cryptowl.nav.Routes

/**
 * Root of the Compose tree: brands the app, then switches between the three
 * top-level states (AppState). Each state owns its own NavHost, so the main
 * shell is simply absent while the vault is locked (its state is dropped).
 */
@Composable
fun CryptowlApp(viewModel: MainViewModel) {
    CryptowlTheme {
        val appState by viewModel.appState.collectAsState()
        when (appState) {
            AppState.ONBOARDING -> OnboardingNavHost(viewModel)
            AppState.LOCKED -> LockedNavHost(viewModel)
            AppState.UNLOCKED -> MainNavHost(viewModel)
        }
    }
}

@Composable
private fun OnboardingNavHost(viewModel: MainViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.ONBOARDING_INTRO) {
        composable(Routes.ONBOARDING_INTRO) {
            IntroScreen(
                onStart = { navController.navigate(Routes.ONBOARDING_PASSWORD) },
                onRestore = { navController.navigate(Routes.RESTORE) },
            )
        }
        composable(Routes.ONBOARDING_PASSWORD) {
            MasterPasswordScreen(viewModel) {
                navController.navigate(Routes.ONBOARDING_BIOMETRIC)
            }
        }
        composable(Routes.ONBOARDING_BIOMETRIC) {
            BiometricSetupScreen(viewModel, onDone = viewModel::markVaultCreated)
        }
        composable(Routes.RESTORE) {
            RestoreScreen(viewModel, onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun LockedNavHost(viewModel: MainViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.LOCK) {
        composable(Routes.LOCK) {
            LockScreen(viewModel)
        }
        composable(Routes.RESTORE) {
            RestoreScreen(viewModel, onBack = { navController.popBackStack() })
        }
    }
}
