package com.example.myklyuchik2.ui.splash

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import com.example.myklyuchik2.R
import com.example.myklyuchik2.data.storage.SecurePasswordStorage
import com.example.myklyuchik2.ui.theme.MyKlyuchikTheme
import com.example.myklyuchik2.utils.Constants
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner

@Composable
fun SplashScreen(
	onAuthenticated: () -> Unit,
	modifier: Modifier = Modifier
) {
	var password by remember { mutableStateOf("") }
	var isError by remember { mutableStateOf(false) }
	val focusRequester = remember { FocusRequester() }
	val coroutineScope = rememberCoroutineScope()
	val lifecycleOwner = LocalLifecycleOwner.current

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

		val context = LocalContext.current
		Button(
			onClick = {
				// Pass context to the non-composable function
				launchAndAuthenticate(
					password = password,
					coroutineScope = coroutineScope,
					context = context, // Pass context here
					onAuthenticated = onAuthenticated,
					isError = isError,
					setIsError = { newError -> isError = newError }
				)
			},
			modifier = Modifier.fillMaxWidth(0.8f)
		) {
			Text("Войти")
		}
	}
}
private fun launchAndAuthenticate(
	password: String,
	coroutineScope: CoroutineScope,
	context: android.content.Context, // Accept context as a parameter
	onAuthenticated: () -> Unit,
	isError: Boolean,
	setIsError: (Boolean) -> Unit
) {
	coroutineScope.launch {
		// Use the passed-in context directly
		if (context == null) {
			setIsError(true)
			return@launch
		}

		// Get the storage instance
		val storage = SecurePasswordStorage.getInstance(context)
		val decryptedPassword = storage.decryptPassword()
		Log.d("SplashScreen", "decryptedPassword returned password: $decryptedPassword")

		// Handle authentication
		if (password == decryptedPassword) {
			onAuthenticated()
		} else {
			setIsError(true)
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