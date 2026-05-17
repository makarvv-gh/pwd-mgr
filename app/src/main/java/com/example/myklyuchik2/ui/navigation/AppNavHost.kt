package com.example.myklyuchik2.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import com.example.myklyuchik2.ui.entry.EntryScreen
import com.example.myklyuchik2.ui.main.model.EntryMode
import com.example.myklyuchik2.ui.main.MainScreen
import com.example.myklyuchik2.ui.splash.SplashScreen

sealed class Screen(val route: String) {
	object Splash : Screen("splash")
	object Main : Screen("main")
	object Entry : Screen("entry/{entryId}?mode={mode}") {
		fun createRoute(entryId: String? = null, mode: EntryMode = EntryMode.CREATE) =
			"entry/${entryId ?: "new"}?mode=${mode.name}"
	}
}

//enum class EntryMode { CREATE, EDIT }

@Composable
fun AppNavHost(
	navController: NavHostController,
	modifier: Modifier = Modifier,
	startDestination: String = Screen.Splash.route
) {
	NavHost(
		navController = navController,
		startDestination = startDestination,
		modifier = modifier
	) {
		composable(Screen.Splash.route) {
			SplashScreen(
				onAuthenticated = { navController.navigate(Screen.Main.route) { popUpTo(Screen.Splash.route) { inclusive = true } } }
			)
		}
		composable(Screen.Main.route) {
			MainScreen(
				onAddEntry = { navController.navigate(Screen.Entry.createRoute()) },
				onEditEntry = { entry ->
					navController.navigate(Screen.Entry.createRoute(entry.id, EntryMode.EDIT))
				},
				onDeleteEntry = { /* handled in VM */ },
				onSettingsClick = { /* show dialog */ }
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

			EntryScreen(
				mode = mode,
				entryId = if (entryId == "new") null else entryId,
				onSaved = { navController.popBackStack() },
				onDiscard = { navController.popBackStack() }
			)
		}
	}
}