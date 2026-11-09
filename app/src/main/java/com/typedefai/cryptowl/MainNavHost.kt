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
import com.typedefai.cryptowl.ui.MediaLoader

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

    val passwordViewModel: PasswordViewModel = viewModel()
    val noteViewModel: NoteViewModel = viewModel()
    val mediaViewModel: MediaViewModel = viewModel()
    val topSecretViewModel: TopSecretViewModel = viewModel()
    val session by viewModel.session.collectAsState()
    val biometricUnlockAvailable by viewModel.biometricUnlockAvailable.collectAsState()

    LaunchedEffect(session) {
        session?.let {
            passwordViewModel.attach(it)
            noteViewModel.attach(it)
            mediaViewModel.attach(it)
            topSecretViewModel.attach(it)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            passwordViewModel.detach()
            noteViewModel.detach()
            mediaViewModel.detach()
            topSecretViewModel.detach()
            MediaLoader.clear()
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
            composable(Routes.NOTES) {
                NotesScreen(
                    viewModel = noteViewModel,
                    onOpen = { navController.navigate(Routes.noteEdit(it)) },
                    onAdd = { navController.navigate(Routes.noteEdit()) },
                )
            }
            composable(
                route = Routes.NOTE_EDIT,
                arguments = listOf(navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }),
            ) { entry ->
                NoteEditScreen(
                    viewModel = noteViewModel,
                    id = entry.arguments?.getString("id"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PASSWORDS) {
                PasswordsScreen(
                    viewModel = passwordViewModel,
                    biometricEnabled = biometricUnlockAvailable,
                    onOpen = { navController.navigate(Routes.passwordDetail(it)) },
                    onAdd = { navController.navigate(Routes.passwordEdit()) },
                )
            }
            composable(
                route = Routes.PASSWORD_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                PasswordDetailScreen(
                    viewModel = passwordViewModel,
                    id = id,
                    onEdit = { navController.navigate(Routes.passwordEdit(id)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.PASSWORD_EDIT,
                arguments = listOf(navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }),
            ) { entry ->
                PasswordEditScreen(
                    viewModel = passwordViewModel,
                    id = entry.arguments?.getString("id"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.TOP_SECRET) {
                TopSecretScreen(
                    viewModel = topSecretViewModel,
                    onOpen = { navController.navigate(Routes.topSecretDetail(it)) },
                    onAdd = { navController.navigate(Routes.topSecretEdit()) },
                )
            }
            composable(
                route = Routes.TOP_SECRET_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                TopSecretDetailScreen(
                    viewModel = topSecretViewModel,
                    id = id,
                    onEdit = { navController.navigate(Routes.topSecretEdit(id)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.TOP_SECRET_EDIT,
                arguments = listOf(navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }),
            ) { entry ->
                TopSecretEditScreen(
                    viewModel = topSecretViewModel,
                    id = entry.arguments?.getString("id"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.MEDIA) { MediaScreen(mediaViewModel, session) }
            composable(Routes.RESTORE) {
                RestoreScreen(viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}
