package com.example.myklyuchik2.ui.csvimport

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myklyuchik2.ui.theme.MyKlyuchikTheme

/**
 * Transparent "picker host" activity.
 *
 * Its only jobs are:
 *  1. Launch the system document picker and hand the picked URI to [CsvImportViewModel].
 *  2. Finish as soon as processing reaches a terminal state (success, error, or
 *     cancellation), returning the user to the Settings screen they came from.
 *
 * IMPORTANT: this Activity must NOT create its own MainViewModel instance.
 * A MainViewModel created here lives in CsvImportActivity's ViewModelStore;
 * MainActivity's screens observe the MainViewModel owned by MainActivity.
 * Updating the wrong copy leaves the file saved but the visible UI stale
 * (exactly the "entries appear only after relaunch" symptom).
 */
class CsvImportActivity : ComponentActivity() {

	// Activity-scoped ViewModel, shared with the Composables of this Activity.
	private val csvImportViewModel: CsvImportViewModel by lazy {
		ViewModelProvider(this)[CsvImportViewModel::class.java]
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContent {
			MyKlyuchikTheme {
				CsvImportLauncher(
					viewModel = csvImportViewModel,
					onFinished = { finish() }
				)
			}
		}
	}
}

@Composable
fun CsvImportLauncher(
	viewModel: CsvImportViewModel,
	onFinished: () -> Unit
) {
	val context = LocalContext.current
	var isPickerLaunched by remember { mutableStateOf(false) }

	val launcher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenDocument()
	) { uri: Uri? ->
		if (uri != null) {
			Log.d("CsvImportLauncher", "File picked: $uri")
			try {
// Persist read access so the URI stays readable beyond this callback.
				context.contentResolver.takePersistableUriPermission(
					uri,
					Intent.FLAG_GRANT_READ_URI_PERMISSION)
			} catch (e: SecurityException) {
// Some providers don't grant persistable permissions; the one-time
// grant is still enough to read the file while this Activity is alive.
				Log.w("CsvImportLauncher", "Persistable permission not available", e)
			}
			viewModel.processCsvFile(uri, context)
		} else {
			Log.d("CsvImportLauncher", "No file selected")
// User cancelled the picker: end immediately instead of spinning forever.
			viewModel.markCancelled()
			onFinished()
		}
	}

	if (!isPickerLaunched) {
		LaunchedEffect(Unit) {
			isPickerLaunched = true
			launcher.launch(arrayOf("text/comma-separated-values", "text/csv", "text/plain"))
		}
	}

// Collect the result StateFlow correctly. The previous code read
// viewModel.importResult.value inside derivedStateOf(...) keyed on the flow
// object itself — the flow reference never changes, so the snapshot was
// computed once and NEVER recomputed when a new result was emitted.
// That left isProcessingComplete permanently false => infinite spinner.
	val importResult by viewModel.importResult.collectAsStateWithLifecycle()

// As soon as the import reaches a terminal state, close this Activity and
// return to the Settings screen. Persistence and updating the SHARED
// MainViewModel state happen inside processCsvFile().
	if (importResult !is CsvImportResult.InProgress) {
		LaunchedEffect(importResult) {
			onFinished()
		}
	}

	Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		CircularProgressIndicator()
	}
}
