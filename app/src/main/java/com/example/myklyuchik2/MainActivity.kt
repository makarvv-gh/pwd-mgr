package com.example.myklyuchik2

import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import androidx.activity.compose.setContent
import com.example.myklyuchik2.ui.theme.MyKlyuchikTheme
import com.example.myklyuchik2.ui.navigation.AppNavHost
import androidx.fragment.app.FragmentActivity
// MainActivity.kt — теперь только хост для Compose
//class MainActivity : ComponentActivity() {
class MainActivity : FragmentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			MyKlyuchikTheme {
				val navController = rememberNavController()
				AppNavHost(navController = navController)
			}
		}
	}
}