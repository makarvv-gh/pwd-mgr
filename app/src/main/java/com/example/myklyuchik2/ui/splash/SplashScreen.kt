package com.example.myklyuchik2.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myklyuchik2.R
import com.example.myklyuchik2.ui.theme.MyKlyuchikTheme
import com.example.myklyuchik2.utils.Constants

@Composable
fun SplashScreen(
	onAuthenticated: () -> Unit,
	modifier: Modifier = Modifier
) {
	var password by remember { mutableStateOf("") }
	var isError by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.fillMaxSize()
			.padding(24.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		Text(
			text = "Менеджер паролей",
			style = MaterialTheme.typography.titleLarge
		)
		Spacer(modifier = Modifier.height(4.dp))
		Text(
			text = "Мой Ключик",
			style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
			color = MaterialTheme.colorScheme.primary
		)
		Spacer(modifier = Modifier.height(24.dp))

		Image(
			painter = painterResource(id = R.drawable.splash),
			contentDescription = "App Logo",
			modifier = Modifier.size(300.dp)
		)

		Spacer(modifier = Modifier.height(32.dp))

		Text(
			text = "Введите мастер-пароль",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		Spacer(modifier = Modifier.height(8.dp))

		OutlinedTextField(
			value = password,
			onValueChange = {
				password = it
				isError = false
			},
			label = { Text("Мастер-пароль") },
			visualTransformation = PasswordVisualTransformation(),
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
			singleLine = true,
			isError = isError,
			supportingText = { if (isError) Text("Неверный пароль") },
			modifier = Modifier.fillMaxWidth()
		)

		Spacer(modifier = Modifier.height(24.dp))

		Button(
			onClick = {
				if (password == Constants.MASTER_PASSWORD) {
					onAuthenticated()
				} else {
					isError = true
				}
			},
			modifier = Modifier.fillMaxWidth(0.8f)
		) {
			Text("Войти")
		}
	}
}

@Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
	MyKlyuchikTheme {
		SplashScreen(onAuthenticated = {})
	}
}