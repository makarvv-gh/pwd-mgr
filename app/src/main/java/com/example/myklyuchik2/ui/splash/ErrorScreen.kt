package com.example.myklyuchik2.ui.splash

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@Composable
fun ErrorScreen(navController: NavHostController) {
	val context = LocalContext.current

	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(24.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		Text(
			text = "Error",
			fontSize = 24.sp,
			fontWeight = FontWeight.Bold,
			color = MaterialTheme.colorScheme.error
		)
		Spacer(modifier = Modifier.height(16.dp))
		Text(
			text = "Data file is missing or corrupted.\nPlease reinstall the app.",
			fontSize = 18.sp,
			textAlign = TextAlign.Center
		)
		Spacer(modifier = Modifier.height(24.dp))
		Button(
			onClick = {
				Toast.makeText(context, "Exiting app", Toast.LENGTH_SHORT).show()
				android.os.Process.killProcess(android.os.Process.myPid())
			}
		) {
			Text("Exit")
		}
	}
}
