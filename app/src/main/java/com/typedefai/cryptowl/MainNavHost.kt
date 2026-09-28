package com.typedefai.cryptowl

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.typedefai.cryptowl.nav.Routes
import com.typedefai.cryptowl.nav.TopLevelDestination

/**
 * The authenticated shell: a bottom navigation bar over four top-level
 * destinations, with full-screen routes (chat, vault drill-downs) pushed on
 * top of the graph.
 */
@Composable
fun MainNavHost(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentDestination?.route }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MOMENTS,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.MOMENTS) {
                MomentsScreen(viewModel, onLock = viewModel::lockVault)
            }
            composable(Routes.VAULT) {
                VaultHubScreen(
                    onOpenNotes = { navController.navigate(Routes.NOTES) },
                    onOpenPasswords = { navController.navigate(Routes.PASSWORDS) },
                    onOpenTopSecret = { navController.navigate(Routes.TOP_SECRET) },
                    onOpenMedia = { navController.navigate(Routes.MEDIA) },
                )
            }
            composable(Routes.ASSISTANT) {
                ChatScreen(
                    viewModel = viewModel.chat,
                    agentName = stringResource(R.string.chat_agent_name),
                    onBack = null,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    onLock = viewModel::lockVault,
                    onRestore = { navController.navigate(Routes.RESTORE) },
                )
            }
            composable(Routes.NOTES) { FeaturePlaceholderScreen(Routes.NOTES) }
            composable(Routes.PASSWORDS) { FeaturePlaceholderScreen(Routes.PASSWORDS) }
            composable(Routes.TOP_SECRET) { FeaturePlaceholderScreen(Routes.TOP_SECRET) }
            composable(Routes.MEDIA) { FeaturePlaceholderScreen(Routes.MEDIA) }
            composable(Routes.RESTORE) {
                RestoreScreen(viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}
