package com.example.myklyuchik2.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
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
import com.example.myklyuchik2.ui.csvimport.CsvImportHost
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
	//val context = LocalContext.current.applicationContext
	val context = LocalContext.current
	val appContext = context.applicationContext
	val mainViewModel: MainViewModel = MainViewModel.getInstance(
		//context = context,
		//assetManager = context.assets
		context = appContext,
		assetManager = appContext.assets
	)
	// Headless CSV import host: kept in the Activity's ViewModelStore so an import
	// survives recompositions, but it never draws a screen of its own.
	// IMPORTANT: the file-picker launcher below must be registered against this SAME
	// owner (the Activity). rememberLauncherForActivityResult picks its LifecycleOwner
	// from LocalLifecycleOwner; inside a composable() that is the NavBackStackEntry,
	// which gets DESTROYED when Settings is popped — its recycled request code then
	// reaches >= 65536 and launch() crashes with
	// "Can only use lower 16 bits for requestCode". Registering at NavHost level ties
	// the launcher to the Activity lifecycle, which is always STARTED before launch().

	val activity = context.findActivity()
	val csvImportHost: CsvImportHost = viewModel(
		viewModelStoreOwner = activity,
		factory = CsvImportHost.factory(mainViewModel)
	)
	val csvPickerLauncher = activity.activityResultRegistry.register(
		"csv-import-picker",
		ActivityResultContracts.OpenDocument()
	) { uri ->
		if (uri != null) {
			CsvImportHost.persistReadPermission(appContext, uri)
			csvImportHost.importCsv(uri, appContext)
		}
	}
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
				onExportCsv = { /* Handle export CSV action */ },
				csvImportHost = csvImportHost,
				csvPickerLauncher = csvPickerLauncher,
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
/** Resolves the Activity hosting a Compose view by walking up wrapped contexts. */
/*internal fun android.content.Context.findActivity(): android.app.Activity {
	var ctx: android.content.Context = this
	while (ctx is android.content.ContextWrapper) {
		if (ctx is android.app.Activity) return ctx
		ctx = ctx.baseContext
	}
	error("No Activity found in context chain")
}*/
internal fun android.content.Context.findActivity(): androidx.activity.ComponentActivity {
	var ctx: android.content.Context = this
	while (ctx is android.content.ContextWrapper) {
		if (ctx is androidx.activity.ComponentActivity) return ctx
		ctx = ctx.baseContext
	}
	error("No ComponentActivity found in context chain")
}