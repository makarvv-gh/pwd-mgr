package com.example.myklyuchik2.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import com.example.myklyuchik2.ui.entry.EntryScreen
import com.example.myklyuchik2.ui.main.model.EntryMode
import com.example.myklyuchik2.ui.main.MainScreen
import com.example.myklyuchik2.ui.splash.SplashScreen
import com.example.myklyuchik2.ui.settings.SettingsScreen
import com.example.myklyuchik2.ui.main.MainViewModel
import com.example.myklyuchik2.ui.settings.ChangePasswordScreen
import com.example.myklyuchik2.ui.splash.FirstTimeSetupScreen
import com.example.myklyuchik2.utils.Constants
import com.example.myklyuchik2.ui.splash.ErrorScreen

sealed class Screen(val route: String) {
	object Splash : Screen("splash")
	object Main : Screen("main")
	object Settings : Screen("settings")
	object Entry : Screen("entry/{entryId}?mode={mode}") {
		fun createRoute(entryId: String? = null, mode: EntryMode = EntryMode.CREATE) =
			"entry/${entryId ?: "new"}?mode=${mode.name}"
	}
}

//enum class EntryMode { CREATE, EDIT }

@Composable
fun AppNavHost(
	navController: NavHostController = rememberNavController(),
	modifier: Modifier = Modifier,
	isFirstUse: Boolean = false
) {
	val startDestination = if (isFirstUse) "first_time" else "splash"
// ✅ Declare it first
	val mainViewModel: MainViewModel = viewModel(
		factory = MainViewModel.Factory(
			context = LocalContext.current.applicationContext,
			assetManager = LocalContext.current.assets
		)
	)
	NavHost(
		navController = navController,
		startDestination = startDestination,
		modifier = modifier
	) {
		composable(Screen.Splash.route) {
			SplashScreen(
				onAuthenticated = { navController.navigate(Screen.Main.route) {
					popUpTo(Screen.Splash.route) { inclusive = true }
				} }
			)
		}

		/*composable("first_time") {
			FirstTimeSetupScreen(onPasswordCreated = { password ->
				navController.navigate(Screen.Main.route) {
					popUpTo("first_time") { inclusive = true }
				}
			})
		}*/
		composable("first_time") {
			FirstTimeSetupScreen { password ->
				navController.navigate(Screen.Main.route) {
					popUpTo("first_time") { inclusive = true }
				}
			}
		}
		composable("error") {
			ErrorScreen(navController = navController)
		}

		composable(Screen.Main.route) {
			MainScreen(
				navController = navController,
				onAddEntry = { navController.navigate(Screen.Entry.createRoute()) },
				onEditEntry = { entry ->
					navController.navigate(Screen.Entry.createRoute(entry.id, EntryMode.EDIT))
				},
				onDeleteEntry = { /* handled in VM */ },
				//onSettingsClick = { /* show dialog */ }
				onSettingsClick = {
					navController.navigate(Screen.Settings.route) {
						// This ensures the current screen is kept in back stack
						launchSingleTop = true
					}
				}
			)
		}
		composable(Screen.Settings.route) {
			SettingsScreen(
				navController = navController,
				onNavigateBack = { navController.popBackStack() },
				onImportCsv = { /* Handle import CSV action */ },
				onExportCsv = { /* Handle export CSV action */ },
				onChangePassword = { navController.navigate("change-password") },
				onCloudClick = { /* Handle cloud sync action */ }
			)
		}

		composable("change-password") {
			ChangePasswordScreen(
				navController = navController,
				mainViewModel = mainViewModel
			)
		}

		// В AppNavHost.kt, в комментах к Screen.Entry:
		composable(
			route = Screen.Entry.route,
			arguments = listOf(
				navArgument("entryId") { defaultValue = "new" },
				navArgument("mode") { defaultValue = EntryMode.CREATE.name }
			)
		) { backStackEntry ->
			val entryId = backStackEntry.arguments?.getString("entryId")
			val mode = EntryMode.valueOf(
				backStackEntry.arguments?.getString("mode") ?: EntryMode.CREATE.name
			)
			/*/ ✅ Declare it first
			val mainViewModel: MainViewModel = viewModel(
				factory = MainViewModel.Factory(
					context = LocalContext.current.applicationContext,
					assetManager = LocalContext.current.assets
				)
			)*/
			EntryScreen(
				mode = mode,
				entryId = if (entryId == "new") null else entryId,
				//onSaved = { navController.popBackStack() } 2026-06-15 changed as refresh fix
				onSaved = {
					navController.popBackStack()
					navController.navigate(Screen.Main.route) {
						launchSingleTop = false
						restoreState = false
					}
				},
				onDiscard = { navController.popBackStack() },
				mainViewModel = mainViewModel // ✅ Now passed
			)
		}
	}
}