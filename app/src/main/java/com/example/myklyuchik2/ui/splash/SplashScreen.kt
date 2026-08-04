package com.example.myklyuchik2.ui.splash

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

		Button(
			onClick = {
				// Move the coroutine to the lifecycleOwner's scope
				launchAndAuthenticate(
					password = password,
					coroutineScope = coroutineScope,
					lifecycleOwner = lifecycleOwner,
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
	lifecycleOwner: LifecycleOwner,
	onAuthenticated: () -> Unit,
	isError: Boolean,
	setIsError: (Boolean) -> Unit
) {
	// Move the context access inside the coroutineScope.launch
	coroutineScope.launch {
		// Get the context from the lifecycleOwner's context
		val context = lifecycleOwner as? android.content.Context
			?: run {
				// Use a regular function to handle the case where context is not available
				setIsError(true)
				return@launch
			}

		// Get the storage instance
		val storage = SecurePasswordStorage.getInstance(context)
		val decryptedPassword = storage.decryptPassword()

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