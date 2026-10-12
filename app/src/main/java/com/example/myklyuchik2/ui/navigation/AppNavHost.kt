package com.example.myklyuchik2.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
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
import com.example.myklyuchik2.ui.csvimport.CsvPendingImport
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
	val activity = context.findActivity()
	val csvImportHost: CsvImportHost = viewModel(
		viewModelStoreOwner = activity,
		factory = CsvImportHost.factory(mainViewModel)
	)
	// ROOT CAUSE OF THE CRASH ("Can only use lower 16 bits for requestCode"):
	// MainActivity extends FragmentActivity (it must — biometric prompts go through
	// androidx.biometric, which requires a FragmentActivity). ComponentActivity overrides
	// startActivityForResult() and delegates to FragmentActivity.startActivityForResult(),
	// which enforces the classic fragment request-code window [0, 0xFFFF]. But since
	// androidx.activity 1.9, ActivityResultRegistry generates its internal request codes as
	// RANDOM numbers starting from INITIAL_REQUEST_CODE_VALUE = 0x10000 (65536) — i.e. always
	// ABOVE that window (verified in activity-1.13.0 sources: ActivityResultRegistry.kt, line
	// "private const val INITIAL_REQUEST_CODE_VALUE = 0x00010000"). So every launcher
	// registered against a FragmentActivity's registry crashes inside launch() -> onLaunch()
	// -> ActivityCompat.startActivityForResult(), before the picker UI ever appears. That is
	// why none of the previous fixes helped: moving the registration from the NavBackStackEntry
	// up to the Activity level fixed the lifecycle problem but could not fix this arithmetic
	// incompatibility between androidx.activity and androidx.fragment.
	// FIX: bypass the ActivityResultRegistry entirely and start ACTION_OPEN_DOCUMENT directly
	// with a plain, valid fragment request code via FragmentActivity.startActivityForResult().
	// The result comes back through MainActivity.onActivityResult (see MainActivity.kt) and is
	// handed to CsvImportHost there. One fixed request code is never recycled per navigation
	// destination, so nothing can ever exceed 16 bits.
	val csvPickerLauncher = rememberCsvPickerLauncher(
		activity = activity,
		csvImportHost = csvImportHost,
		appContext = appContext
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
internal fun android.content.Context.findActivity(): androidx.fragment.app.FragmentActivity {
	var ctx: android.content.Context = this
	while (ctx is android.content.ContextWrapper) {
		if (ctx is androidx.fragment.app.FragmentActivity) return ctx
		ctx = ctx.baseContext
	}
	error("No FragmentActivity found in context chain")
}

/**
 * Builds the CSV file-picker launcher used by [com.example.myklyuchik2.ui.settings.SettingsScreen].
 *
 * Deliberately does NOT use ActivityResultRegistry / rememberLauncherForActivityResult:
 * this app's MainActivity is a FragmentActivity, and registry-generated request codes are
 * >= 0x10000, which FragmentActivity.startActivityForResult() rejects with
 * "Can only use lower 16 bits for requestCode" (see the long comment at the call site).
 * Instead we start ACTION_OPEN_DOCUMENT with a fixed small request code; the result is
 * delivered to MainActivity.onActivityResult(), which routes it to CsvImportHost via
 * CsvPendingImport (see csvimport/CsvImportHost.kt).
 */
@Composable
internal fun rememberCsvPickerLauncher(
	activity: androidx.fragment.app.FragmentActivity,
	csvImportHost: CsvImportHost,
	appContext: android.content.Context,
): ActivityResultLauncher<Array<String>> {
	// Keep the latest host/context in a remembered state so the stable launcher object
	// never captures a stale reference after recomposition.
	val currentHost = androidx.compose.runtime.rememberUpdatedState(csvImportHost)
	val currentContext = androidx.compose.runtime.rememberUpdatedState(appContext)
	val currentActivity = androidx.compose.runtime.rememberUpdatedState(activity)

	// Register the pending callback once per composition; it survives because it lives in
	// the process-wide CsvPendingImport holder, not tied to any lifecycle owner.
	androidx.compose.runtime.DisposableEffect(Unit) {
		CsvPendingImport.callback = { uri ->
			if (uri != null) {
				CsvImportHost.persistReadPermission(currentContext.value, uri)
				currentHost.value.importCsv(uri, currentContext.value)
			}
		}
		onDispose { CsvPendingImport.callback = null }
	}

	return remember(activity) {
		object : ActivityResultLauncher<Array<String>>() {
			override fun launch(input: Array<String>, options: ActivityOptionsCompat?) {
				val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
					.addCategory(android.content.Intent.CATEGORY_OPENABLE)
					.setType("*/*")
					.putExtra(android.content.Intent.EXTRA_MIME_TYPES, input)
				// Fixed, tiny request code — always valid for FragmentActivity.
				currentActivity.value.startActivityForResult(
					intent, Constants.CSV_IMPORT_REQUEST_CODE
				)
			}

			override fun unregister() { /* no-op: nothing was registered in the registry */ }

			override val contract: ActivityResultContract<Array<String>, Uri?>
				get() = ActivityResultContracts.OpenDocument()
		}
	}
}