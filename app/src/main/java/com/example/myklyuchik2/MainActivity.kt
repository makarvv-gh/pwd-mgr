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

//class MainActivity : FragmentActivity() {
class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val file = File(filesDir, "passwords.enc")
		val isFirstUse = !file.exists()

		setContent {
			MyKlyuchikTheme {
				Surface(
					modifier = Modifier.fillMaxSize(),
					color = MaterialTheme.colorScheme.background
				) {
					AppNavHost(isFirstUse = isFirstUse)
				}
			}
		}
	}
}