package com.typedefai.cryptowl

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.typedefai.cryptowl.nav.Routes
import com.typedefai.cryptowl.nav.TopLevelDestination

/**
 * The authenticated shell: a bottom navigation bar over Items / Media /
 * Settings, with the item editor, history and restore screens pushed on top.
 */
@Composable
fun MainNavHost(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentDestination?.route }

    val itemsViewModel: ItemsViewModel = viewModel()
    val editorViewModel: ItemEditorViewModel = viewModel()
    val historyViewModel: HistoryViewModel = viewModel()
    val mediaViewModel: MediaViewModel = viewModel()
    val topSecretViewModel: TopSecretViewModel = viewModel()
    val session by viewModel.session.collectAsState()

    LaunchedEffect(session) {
        session?.let {
            itemsViewModel.attach(it)
            editorViewModel.attach(it)
            historyViewModel.attach(it)
            mediaViewModel.attach(it)
            topSecretViewModel.attach(it)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            itemsViewModel.detach()
            editorViewModel.clearKeys()
            mediaViewModel.detach()
            topSecretViewModel.detach()
        }
    }

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
            startDestination = Routes.ITEMS,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.ITEMS) {
                LaunchedEffect(Unit) { itemsViewModel.refresh() }
                ItemsScreen(
                    viewModel = itemsViewModel,
                    onOpenItem = { id, type ->
                        if (type == "photo" || type == "video") {
                            navController.navigate(Routes.MEDIA)
                        } else {
                            navController.navigate(Routes.itemEditor(id))
                        }
                    },
                    onCreate = { type -> navController.navigate(Routes.itemEditor(type = type)) },
                    onLock = viewModel::lockVault,
                )
            }
            composable(Routes.MEDIA) {
                MediaScreen(mediaViewModel, onLock = viewModel::lockVault)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    topSecretViewModel = topSecretViewModel,
                    onLock = viewModel::lockVault,
                    onRestore = { navController.navigate(Routes.RESTORE) },
                )
            }
            composable(
                route = Routes.ITEM_EDITOR,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                ItemEditorScreen(
                    viewModel = editorViewModel,
                    itemId = entry.arguments?.getString("id"),
                    typeId = entry.arguments?.getString("type"),
                    onBack = {
                        itemsViewModel.refresh()
                        navController.popBackStack()
                    },
                    onHistory = { id -> navController.navigate(Routes.history(id)) },
                )
            }
            composable(
                route = Routes.HISTORY,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                HistoryScreen(
                    viewModel = historyViewModel,
                    itemId = id,
                    onBack = {
                        itemsViewModel.refresh()
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.RESTORE) {
                RestoreScreen(viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}
