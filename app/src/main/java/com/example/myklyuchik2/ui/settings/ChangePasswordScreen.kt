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
//import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
//import com.example.myklyuchik2.utils.Constants

//import androidx.compose.ui.input.key.Key

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.example.myklyuchik2.data.repository.PasswordRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(navController: NavController) {
	val context = LocalContext.current
	val passwordRepository = remember { PasswordRepository.getInstance(context) }
	val focusManager = LocalFocusManager.current

	var oldPassword by remember { mutableStateOf(TextFieldValue("")) }
	var newPassword by remember { mutableStateOf(TextFieldValue("")) }
	var confirmPassword by remember { mutableStateOf(TextFieldValue("")) }

	var error by remember { mutableStateOf("") }
	var showSuccess by remember { mutableStateOf(false) }

	val snackbarHostState = remember { SnackbarHostState() }
	val scope = rememberCoroutineScope()

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
					/*onKeyboardAction = {
						focusManager.clearFocus()
					},*/
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

				Button(
					onClick = {
						// Validate inputs
						if (oldPassword.text.isEmpty() || newPassword.text.isEmpty() || confirmPassword.text.isEmpty()) {
							error = "Все поля должны быть заполнены"
							return@Button
						}

						/*if (oldPassword.text != Constants.MASTER_PASSWORD) {
							error = "Текущий пароль неверен"
							return@Button
						}*/

						val currentPassword = passwordRepository.getMasterPassword()
						if (oldPassword.text != currentPassword) {
							error = "Текущий пароль неверен"
							return@Button
						}

						if (newPassword.text != confirmPassword.text) {
							error = "Новые пароли не совпадают"
							return@Button
						}

						// In a real app, you would securely update the master password
						// For this example, we'll just show a success dialog
						//showSuccess = true
						//error = ""

						// Update the master password
						val success = passwordRepository.updateMasterPassword(newPassword.text)
						if (success) {
							showSuccess = true
							error = ""
						} else {
							error = "Не удалось обновить пароль"
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
			}
		}
	}
}
