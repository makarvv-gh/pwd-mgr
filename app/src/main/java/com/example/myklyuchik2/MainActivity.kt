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
import androidx.compose.runtime.LaunchedEffect

//class MainActivity : FragmentActivity() {
class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val isFreshInstall = AppInitializer.isFirstUse(this)
		val dataValid = AppInitializer.isDataValid(this)

		var shouldShowFirstTimeSetup = false
		var shouldShowError = false

		when {
			isFreshInstall -> {
				shouldShowFirstTimeSetup = true
			}

			!dataValid -> {
				// Data file missing or corrupt
				AppInitializer.clearInstallMarker(this)
				shouldShowError = true
			}

			else -> {
				// Normal launch
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
