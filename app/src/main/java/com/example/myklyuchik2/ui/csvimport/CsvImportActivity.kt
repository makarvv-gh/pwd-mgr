package com.example.myklyuchik2.ui.csvimport

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myklyuchik2.data.model.PasswordEntry
import com.example.myklyuchik2.data.repository.PasswordRepository
import com.example.myklyuchik2.ui.main.MainViewModel
import com.example.myklyuchik2.ui.theme.MyKlyuchikTheme

class CsvImportActivity : ComponentActivity() {
    private val csvImportViewModel = CsvImportViewModel()
    
    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            csvImportViewModel.processCsvFile(it, this)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set up dependencies for the ViewModel
        val mainViewModel = MainViewModel(
            context = applicationContext,
            assetManager = assets
        )
        val passwordRepository = PasswordRepository.getInstance(applicationContext, mainViewModel)
        csvImportViewModel.setDependencies(mainViewModel, passwordRepository)

        setContent {
            MyKlyuchikTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CsvImportScreen(
                        viewModel = csvImportViewModel,
                        onPickFile = { pickFileLauncher.launch("text/*") },
                        onImportComplete = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun CsvImportScreen(
    viewModel: CsvImportViewModel,
    onPickFile: () -> Unit,
    onImportComplete: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Выберите CSV-файл для импорта")

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                isLoading = true
                onPickFile()
            }) {
                Text("Выбрать файл")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (resultText.isNotEmpty()) {
                Text(resultText)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importResult.collect { result ->
            when (result) {
                is CsvImportResult.Success -> {
                    isLoading = false
                    resultText = "Импортировано ${result.entries.size} записей"
                    onImportComplete()
                }
                is CsvImportResult.Error -> {
                    isLoading = false
                    resultText = "Ошибка импорта: ${result.message}"
                }
                is CsvImportResult.Idle -> {
                    // Initial state, do nothing
                }
            }
        }
    }
}

sealed class CsvImportResult {
    data class Success(val entries: List<PasswordEntry>) : CsvImportResult()
    data class Error(val message: String) : CsvImportResult()
    object Idle : CsvImportResult()
}
