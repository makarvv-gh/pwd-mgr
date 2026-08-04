package com.example.myklyuchik2.ui.splash

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myklyuchik2.R
import com.example.myklyuchik2.ui.theme.MyKlyuchikTheme
import com.example.myklyuchik2.data.storage.SecurePasswordStorage
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun FirstTimeSetupScreen(
    modifier: Modifier = Modifier,
    onPasswordCreated: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    // Get the context in the composable scope
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
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
        Spacer(modifier = Modifier.height(20.dp))

        Image(
            painter = painterResource(id = R.drawable.splash200),
            contentDescription = "App Logo",
            modifier = Modifier.size(200.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Файл-хранилище не найден",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Будет создан новый файл",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Придумайте мастер-пароль",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                isError = false
            },
            label = { Text("Введите мастер-пароль") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                isError = false
            },
            label = { Text("Повторите ввод") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            isError = isError,
            supportingText = { if (isError) Text("Ошибка. Пароли не совпадают") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (password == confirmPassword && password.isNotBlank()) {
                    val storage = SecurePasswordStorage.getInstance(context)
                    if (storage.initializeWithPassword(password)) {
                        onPasswordCreated(password)
                    } else {
                        isError = true
                    }
                } else {
                    isError = true
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("Создать")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FirstTimeSetupScreenPreview() {
    MyKlyuchikTheme {
        FirstTimeSetupScreen(onPasswordCreated = {})
    }
}
