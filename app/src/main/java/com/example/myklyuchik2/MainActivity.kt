package com.example.myklyuchik2

// MainActivity.kt — теперь только хост для Compose
class MainActivity : ComponentActivity() {
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