package com.example.myklyuchik2.ui.entry

import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.input.key.Key
import android.view.KeyEvent
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myklyuchik2.ui.main.model.EntryMode
import com.example.myklyuchik2.ui.theme.MyKlyuchikTheme
import com.example.myklyuchik2.utils.Constants
import java.io.File

// ==================== Экран ====================
//@OptIn(ExperimentalMaterial3Api::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(
   mode: EntryMode,
    entryId: String?,
    onSaved: () -> Unit,
    onDiscard: () -> Unit,
    viewModel: EntryViewModel = viewModel(
        factory = EntryViewModel.Factory(
            dataPath = File(
                LocalContext.current.filesDir,
                "passwords.enc"
            ).absolutePath,
            masterPassword = Constants.MASTER_PASSWORD,
            onEvent = { event ->
                when (event) {
                    is EntryUiEvent.SaveSuccess -> onSaved()
                    is EntryUiEvent.Discard -> onDiscard()
                    is EntryUiEvent.ShowError -> {
                        // Можно показатьSnackbar через LocalSnackbarHostState
                        // Для простоты пока выводим в лог
                        Log.e("EntryScreen", event.message)
                    }
                }
            }
        )
    ),
    modifier: Modifier = Modifier
) {
    val state by viewModel.formState.collectAsStateWithLifecycle()
   val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // Инициализация в режиме редактирования
    /*LaunchedEffect(mode, entryId) {
        if (mode == EntryMode.EDIT && entryId != null) {
            viewModel.loadEntryForEdit(entryId)
        }
    }*/
    LaunchedEffect(mode, entryId) {
        if (mode == EntryMode.EDIT && entryId != null) {
            viewModel.loadEntryForEdit(entryId) // ← теперь вызываем функцию из ViewModel
        }
    }

   Scaffold(
modifier=modifier,
       topBar= {
           EntryTopAppBar(
               title=if(mode == EntryMode.CREATE) "Новая запись" else "Редактирование",
               onDiscard ={ viewModel.discardChanges() },
               onSave={
viewModel.saveEntry()
                   focusManager.clearFocus()
               },
isLoading=state.isLoading
           )
}
){padding->
       Column(
modifier=Modifier.fillMaxSize()
               .padding(padding)
               .verticalScroll(scrollState)
               .padding(16.dp),
           verticalArrangement = Arrangement.spacedBy(12.dp)
       ){
//===Ресурс (обязательно) ===
           OutlinedTextField(
               value=state.resourceName,
               onValueChange = viewModel::updateResourceName,
               label={Text("Ресурс *") },
               placeholder={Text("Например: Google, GitHub") },
               singleLine= true,
               isError=state.resourceNameError!= null,
               supportingText= { state.resourceNameError?.let { Text(it) } },
               keyboardOptions=KeyboardOptions(
                   imeAction=ImeAction.Next,
                   autoCorrectEnabled = false
               ),
keyboardActions=KeyboardActions(
                   onNext={focusManager.moveFocus(FocusDirection.Down) }
               ),
modifier=Modifier.fillMaxWidth()
           )

//=== Логин (обязательно) ===
           OutlinedTextField(
                value = state.login,
               onValueChange = viewModel::updateLogin,
               label= { Text("Логин *") },
               placeholder = { Text("user@example.com") },
               singleLine = true,
                isError = state.loginError!= null,
               supportingText = { state.loginError?.let { Text(it) } },
               keyboardOptions = KeyboardOptions(
                    keyboardType=KeyboardType.Email,
                   imeAction = ImeAction.Next
),
keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth()
            )

           // === Пароль (обязательно) ===
           OutlinedTextField(
               value = state.password,
               onValueChange = viewModel::updatePassword,
               label = { Text("Пароль *") },
               placeholder = { Text("••••••••") },
               visualTransformation = if (state.isPasswordVisible)
                   VisualTransformation.None
               else PasswordVisualTransformation(),
               trailingIcon = {
                   IconButton(onClick = viewModel::togglePasswordVisibility) {
                       Icon(
                           if (state.isPasswordVisible) Icons.Default.Visibility
                           else Icons.Default.VisibilityOff,
                           contentDescription = if (state.isPasswordVisible) "Скрыть пароль" else "Показать пароль"
                       )
                   }
               },
               singleLine = true,
               isError = state.passwordError != null,
               supportingText = { state.passwordError?.let { Text(it) } },
               keyboardOptions = KeyboardOptions(
                   keyboardType = KeyboardType.Password,
                   imeAction = ImeAction.Next
               ),
               keyboardActions = KeyboardActions(
                   onNext = { focusManager.moveFocus(FocusDirection.Down) }
               ),
               modifier = Modifier.fillMaxWidth()
           )

            // === Дата изменения пароля (только просмотр) ===
           OutlinedTextField(
                value = state.changeDate,
               onValueChange = viewModel::updateChangeDate,
               label= { Text("Датаизменения пароля") },
               placeholder = { Text("Например: 2024-01-15") },
               singleLine = true,
               //enabled=true,// Разрешаем редактирование — пользователь меняет вручную
               readOnly = false,
               keyboardOptions = KeyboardOptions(
                    keyboardType= KeyboardType.Text,
                   imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // === URL===
           OutlinedTextField(
                value = state.url,
               onValueChange = viewModel::updateUrl,
               label= { Text("URL") },
               placeholder = { Text("https://example.com") },
               singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType= KeyboardType.Uri,
                   imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // === Email===
           OutlinedTextField(
                value = state.email,
               onValueChange = viewModel::updateEmail,
               label= { Text("Email") },
               placeholder = { Text("backup@example.com") },
               singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // === Код 2FA ===
           OutlinedTextField(
                value = state.authCode,
               onValueChange = viewModel::updateAuthCode,
               label= { Text("Код 2FA / секрет") },
               placeholder = { Text("JBSWY3DPEHPK3PXP") },
               singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // === Заметки ===
           OutlinedTextField(
                value = state.notes,
               onValueChange = viewModel::updateNotes,
               label= { Text("Заметки") },
               placeholder = { Text("Дополнительная информация...") },
               minLines= 3,
               maxLines= 5,
               keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done),
                    modifier= Modifier.fillMaxWidth()
            )

            // === Теги ===
           Text(
               text="Теги",
               style=MaterialTheme.typography.titleMedium,
               modifier= Modifier.padding(top = 8.dp)
           )

           // Выбранные теги
           if (state.tags.isNotEmpty()) {
               FlowRow(
                   horizontalArrangement = Arrangement.spacedBy(4.dp),
                   verticalArrangement = Arrangement.spacedBy(4.dp),
                   modifier = Modifier.padding(bottom = 4.dp)
               ) {
                   state.tags.forEach { tag ->
                       InputChip(
                           selected = true,
                           onClick = { viewModel.removeTag(tag) },
                           label = { Text(tag) },
                           trailingIcon = {
                               Icon(
                                   Icons.Outlined.Close,
                                   contentDescription = "Удалить тег",
                                   modifier = Modifier.size(16.dp)
                               )
                           }
                       )
                   }
               }
           }

           // === Исправленный ввод нового тега ===
           OutlinedTextField(
               value = state.tagInput,
               onValueChange = viewModel::updateTagInput,
               label = { Text("Добавить тег") },
               placeholder = { Text("Например: work, personal") },
               singleLine = true,
               trailingIcon = {
                   if (state.tagInput.isNotBlank()) {
                       IconButton(onClick = viewModel::addTag) {
                           Icon(
                               Icons.Default.Add,
                               contentDescription = "Добавить тег",
                               modifier = Modifier.size(20.dp)
                           )
                       }
                   }
               },
               keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
               keyboardActions = KeyboardActions(
                   onDone = {
                       viewModel.addTag()
                       focusManager.clearFocus() // Скрываем клавиатуру после добавления
                   }
               ),
               modifier = Modifier.fillMaxWidth()
           )
            // Подсказка
           Text(
               text = "*Обязательные поля",
               style= MaterialTheme.typography.labelSmall,
               color=MaterialTheme.colorScheme.onSurfaceVariant,
               modifier= Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
       }
}
}

//==================== TopAppBar для экрана записи ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryTopAppBar(
    title:String,
    onDiscard: () -> Unit,
    onSave:()-> Unit,
    isLoading:Boolean,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title= {
        Text(
            title,
            maxLines = 1,
            overflow=TextOverflow.Ellipsis
            )
        },
        navigationIcon= {
            //Красный крестик для выхода без сохранения (как просили)
            IconButton(
                onClick= onDiscard,
                enabled= !isLoading
            ){
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Отменить без сохранения",
                    tint=MaterialTheme.colorScheme.error
                )
            }
        },
        actions={
            //Кнопка сохранения
            TextButton(
                onClick= onSave,
                enabled= !isLoading,
                colors=ButtonDefaults.textButtonColors(
                    contentColor= MaterialTheme.colorScheme.primary
                )
            ){
                if(isLoading) {
                    CircularProgressIndicator(
                        modifier=Modifier.size(20.dp),
                        color= MaterialTheme.colorScheme.onPrimary,
                        strokeWidth=2.dp
                   )
                }else{
                    Text("Сохранить")
                }
            }
        },
        modifier= modifier
    )
}

//==================== Режимы экрана ====================
//enum class EntryMode { CREATE, EDIT }

// ==================== Preview ====================
@Preview(showBackground = true, name = "EntryScreen Light")
@Composable
private fun EntryScreenPreviewLight() {
    MyKlyuchikTheme(darkTheme = false) {
        EntryScreen(
            mode=EntryMode.CREATE,
            entryId =null,
            onSaved={},
            onDiscard ={}
        )
    }
}

@Preview(showBackground = true, name = "EntryScreen Dark")
@Composable
private fun EntryScreenPreviewDark() {
    MyKlyuchikTheme(darkTheme = true) {
        EntryScreen(
            mode = EntryMode.CREATE,
            entryId = null,
            onSaved = {},
            onDiscard = {}
        )
    }
}