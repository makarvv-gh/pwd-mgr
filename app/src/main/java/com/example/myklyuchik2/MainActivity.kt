package com.example.myklyuchik2

import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import androidx.activity.compose.setContent
import com.example.myklyuchik2.ui.theme.MyKlyuchikTheme
import com.example.myklyuchik2.ui.navigation.AppNavHost
import com.example.myklyuchik2.ui.csvimport.CsvImportViewModel
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.myklyuchik2.ui.navigation.AppNavHost
import com.example.myklyuchik2.ui.theme.MyKlyuchikTheme
import java.io.File
import android.util.Log
import com.example.myklyuchik2.utils.AppInitializer
import com.example.myklyuchik2.data.storage.DataState
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import android.content.Intent
import androidx.activity.OnBackPressedCallback

class MainActivity : FragmentActivity() {
	private var backPressCount = 0
	private val csvImportViewModel = CsvImportViewModel()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		// Handle back press using the correct OnBackPressedDispatcher API
		val callback = object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				if (backPressCount == 1) {
					finishAffinity() // Close the app
					backPressCount = 0
				} else {
					backPressCount++
					Toast.makeText(
						this@MainActivity,
						"Повторно нажмите Назад для выхода",
						Toast.LENGTH_SHORT
					).show()
				}
			}
		}

		onBackPressedDispatcher.addCallback(this, callback)

		// Determine the current data state
		val dataState = AppInitializer.determineDataState(this)

		var shouldShowFirstTimeSetup = false
		var shouldShowError = false

		when (dataState) {
			DataState.SpuriousData -> {
				// Spurious data was already deleted, proceed as first time use
				shouldShowFirstTimeSetup = true
			}

			DataState.FirstTimeUse -> {
				shouldShowFirstTimeSetup = true
			}

			DataState.NormalUse -> {
				// Normal launch, data is valid
				shouldShowFirstTimeSetup = false
			}
		}

		setContent {
			MyKlyuchikTheme {
				Surface(
					modifier = Modifier.fillMaxSize(),
					color = MaterialTheme.colorScheme.background
				) {
					// ✅ Initialize navController here
					val navController = rememberNavController()
					if (shouldShowError) {
						// ✅ Navigate to error screen
						LaunchedEffect(Unit) {
							navController.navigate("error")
						}
					}

					AppNavHost(
						navController = navController,
						isFirstUse = shouldShowFirstTimeSetup
					)
				}
			}
		}

		if (shouldShowFirstTimeSetup) {
			AppInitializer.markAppInitialized(this)
		}
	}
	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == 1001 && resultCode == RESULT_OK) {
			data?.data?.let { uri ->
				csvImportViewModel.processCsvFile(uri, this)
			}
		}
	}
}
