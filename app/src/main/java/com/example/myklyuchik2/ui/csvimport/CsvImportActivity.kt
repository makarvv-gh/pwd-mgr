package com.example.myklyuchik2.ui.csvimport

import android.os.Bundle
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.LifecycleOwner
import com.example.myklyuchik2.data.model.PasswordEntry
import com.example.myklyuchik2.data.repository.PasswordRepository
import com.example.myklyuchik2.ui.main.MainViewModel
import com.example.myklyuchik2.ui.theme.MyKlyuchikTheme
import kotlinx.coroutines.delay

sealed class CsvImportResult {
	data class Success(val entries: List<PasswordEntry>) : CsvImportResult()
	data class Error(val message: String) : CsvImportResult()
}
class CsvImportActivity : ComponentActivity() {
	private val csvImportViewModel: CsvImportViewModel by lazy {
		CsvImportViewModel()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContent {
			MyKlyuchikTheme {
				val mainViewModel = viewModel<MainViewModel>(
					factory = MainViewModel.Factory(
						context = this,
						assetManager = assets
					)
				)
				val passwordRepository = PasswordRepository.getInstance(this, mainViewModel)
				csvImportViewModel.setDependencies(mainViewModel, passwordRepository)

				Surface(modifier = Modifier.fillMaxSize()) {
					CsvImportLauncher(
						viewModel = csvImportViewModel,
						onImportComplete = { finish() }
					)
				}
			}
		}
	}
}

@Composable
fun CsvImportLauncher(
	viewModel: CsvImportViewModel,
	onImportComplete: () -> Unit
) {
	val context = LocalContext.current

	val launcher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenDocument()
	) { uri ->
		if (uri != null) {
			Log.d("CsvImportLauncher", "File picked: $uri")
			context.contentResolver.takePersistableUriPermission(
				uri,
				Intent.FLAG_GRANT_READ_URI_PERMISSION
			)
			viewModel.processCsvFile(uri, context)
		} else {
			Log.d("CsvImportLauncher", "No file selected")
			onImportComplete()
		}
	}

	var isPickerLaunched by remember { mutableStateOf(false) }

	if (!isPickerLaunched) {
		LaunchedEffect(Unit) {
			isPickerLaunched = true
			viewModel.resetImportState()
			launcher.launch(arrayOf("text/comma-separated-values", "text/csv"))
		}
	}

	Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		CircularProgressIndicator()
	}

	// Observe result only after file is processed
	//val isProcessingComplete by remember {
	val isProcessingComplete by remember(viewModel.importResult) {
		derivedStateOf {
			when (val result = viewModel.importResult.value) {
				is CsvImportResult.Success -> result.entries.isNotEmpty()
				is CsvImportResult.Error -> true
				else -> false
			}
		}
	}

	if (isProcessingComplete) {
		LaunchedEffect(Unit) {
			delay(100) // Small delay to ensure data is ready
			onImportComplete()
			/*viewModel.importResult.collect { result ->
				when (result) {
					is CsvImportResult.Success -> onImportComplete()
					is CsvImportResult.Error -> onImportComplete()
				}
			}*/
		}
	}
}

