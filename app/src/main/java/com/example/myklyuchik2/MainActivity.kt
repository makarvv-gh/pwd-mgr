package com.example.myklyuchik2

import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import androidx.activity.compose.setContent
import com.example.myklyuchik2.ui.theme.MyKlyuchikTheme
import com.example.myklyuchik2.ui.navigation.AppNavHost
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

class MainActivity : FragmentActivity() {
//class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

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
}
