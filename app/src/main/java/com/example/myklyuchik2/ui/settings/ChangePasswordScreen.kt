package com.example.myklyuchik2.ui.settings

import com.example.myklyuchik2.ui.theme.MyKlyuchikTheme
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myklyuchik2.data.storage.SecurePasswordStorage
import com.example.myklyuchik2.data.repository.PasswordRepository
import com.example.myklyuchik2.BiometricAuthManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.security.keystore.KeyProperties
import android.security.keystore.KeyGenParameterSpec
import android.os.Build
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.compose.material3.Button as BButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import android.content.Context
import com.example.myklyuchik2.ui.main.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
	navController: NavController,
	mainViewModel: MainViewModel
) {
	val context = LocalContext.current
	val passwordRepository = remember { PasswordRepository.getInstance(context, mainViewModel) }
	val focusManager = LocalFocusManager.current
	val biometricManager = remember { BiometricAuthManager(context) }
	val scope = rememberCoroutineScope()

	var oldPassword by remember { mutableStateOf(TextFieldValue("")) }
	var newPassword by remember { mutableStateOf(TextFieldValue("")) }
	var showSuccess by remember { mutableStateOf(false) }
	var error by remember { mutableStateOf("") }

	val biometricPrompt by remember {
		mutableStateOf(
			BiometricPrompt(
				context as FragmentActivity,
				ContextCompat.getMainExecutor(context),
				object : BiometricPrompt.AuthenticationCallback() {
					override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
						super.onAuthenticationSucceeded(result)
						// Handle success — e.g., proceed with changing the password
						scope.launch {
							val success = passwordRepository.changePassword(oldPassword.text, newPassword.text)
							if (success) {
								showSuccess = true
								error = ""
							} else {
								error = "Не удалось обновить пароль"
							}
						}
					}

					override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
						super.onAuthenticationError(errorCode, errString)
						// Handle error — e.g., show a message
						Log.e("BiometricPrompt", "Authentication error: $errString")
					}

					override fun onAuthenticationFailed() {
						super.onAuthenticationFailed()
						// Handle failure — e.g., fall back to password input
						Log.d("BiometricPrompt", "Authentication failed")
					}
				}
			)
		)
	}


	val promptInfo by remember {
		mutableStateOf(
			BiometricPrompt.PromptInfo.Builder()
				.setTitle("Биометрическая аутентификация")
				.setSubtitle("Используйте отпечаток пальца для аутентификации")
				//.setNegativeButtonText("Отмена")
				.setAllowedAuthenticators(
					BiometricManager.Authenticators.BIOMETRIC_STRONG or
							BiometricManager.Authenticators.DEVICE_CREDENTIAL
				)
				.build()
		)
	}

	//var oldPassword by remember { mutableStateOf(TextFieldValue("")) }
	//var newPassword by remember { mutableStateOf(TextFieldValue("")) }
	var confirmPassword by remember { mutableStateOf(TextFieldValue("")) }
	//var error by remember { mutableStateOf("") }
	//var showSuccess by remember { mutableStateOf(false) }
	var showBiometricPrompt by remember { mutableStateOf(false) }

	val snackbarHostState = remember { SnackbarHostState() }

	MyKlyuchikTheme {
		Scaffold(
			snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
			topBar = {
				TopAppBar(
					title = { Text("Смена мастер-пароля") },
					navigationIcon = {
						IconButton(onClick = { navController.popBackStack() }) {
							Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
						}
					}
				)
			}
		) { padding ->
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(padding)
					.padding(horizontal = 16.dp),
				verticalArrangement = Arrangement.spacedBy(16.dp)
			) {
				Text(
					text = "Текущий пароль",
					style = MaterialTheme.typography.labelMedium
				)
				OutlinedTextField(
					value = oldPassword,
					onValueChange = { oldPassword = it },
					modifier = Modifier.fillMaxWidth(),
					label = { Text("Текущий мастер-пароль") },
					visualTransformation = PasswordVisualTransformation(),
					keyboardOptions = KeyboardOptions(
						keyboardType = KeyboardType.Password,
						imeAction = ImeAction.Next
					),
					singleLine = true
				)

				Text(
					text = "Новый пароль",
					style = MaterialTheme.typography.labelMedium
				)
				OutlinedTextField(
					value = newPassword,
					onValueChange = { newPassword = it },
					modifier = Modifier.fillMaxWidth(),
					label = { Text("Новый мастер-пароль") },
					visualTransformation = PasswordVisualTransformation(),
					keyboardOptions = KeyboardOptions(
						keyboardType = KeyboardType.Password,
						imeAction = ImeAction.Next
					),
					singleLine = true
				)

				Text(
					text = "Подтверждение пароля",
					style = MaterialTheme.typography.labelMedium
				)
				OutlinedTextField(
					value = confirmPassword,
					onValueChange = { confirmPassword = it },
					modifier = Modifier.fillMaxWidth(),
					label = { Text("Подтвердите новый пароль") },
					visualTransformation = PasswordVisualTransformation(),
					keyboardOptions = KeyboardOptions(
						keyboardType = KeyboardType.Password,
						imeAction = ImeAction.Done
					),
					singleLine = true
				)

				if (error.isNotEmpty()) {
					Text(
						text = error,
						style = MaterialTheme.typography.bodyMedium,
						modifier = Modifier.fillMaxWidth(),
						color = MaterialTheme.colorScheme.error
					)
				}

				BButton(
					onClick = {
						// Validate inputs
						if (oldPassword.text.isEmpty() || newPassword.text.isEmpty() || confirmPassword.text.isEmpty()) {
							error = "Все поля должны быть заполнены"
							return@BButton
						}

						if (newPassword.text != confirmPassword.text) {
							error = "Новые пароли не совпадают"
							return@BButton
						}

						// Check biometric availability before showing prompt
						val canAuthenticate = biometricManager.canAuthenticate()

						if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
							// Biometric authentication is available, show the prompt
							showBiometricPrompt = true
						} else {
							// Biometric not available, proceed with regular password change flow
							scope.launch {
								val success = passwordRepository.changePassword(oldPassword.text, newPassword.text)
								if (success) {
									// Get the current entries from MainViewModel
									val currentEntries = mainViewModel.uiState.value.allEntries

									// Re-encrypt with the new password using existing saveAndReload()
									mainViewModel.saveAndReload(currentEntries)

									showSuccess = true
									error = ""
								} else {
									error = "Не удалось обновить пароль"
								}
							}
						}
					},
					modifier = Modifier.align(Alignment.End)
				) {
					Text("Сохранить изменения")
				}

				if (showSuccess) {
					AlertDialog(
						onDismissRequest = { showSuccess = false },
						icon = { Icon(Icons.Filled.Done, contentDescription = null) },
						title = { Text("Успех") },
						text = { Text("Мастер-пароль успешно изменен") },
						confirmButton = {
							TextButton(
								onClick = {
									showSuccess = false
									navController.popBackStack()
								}
							) {
								Text("OK")
							}
						}
					)
				}

				if (showBiometricPrompt) {
					LaunchedEffect(Unit) {
						try {
							biometricPrompt.authenticate(promptInfo)
						} catch (e: Exception) {
							Log.e("BiometricPrompt", "Authentication failed", e)
							// Handle error or fallback
						}
					}
				}
			}
		}
	}

}

