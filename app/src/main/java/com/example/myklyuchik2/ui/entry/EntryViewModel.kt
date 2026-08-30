package com.example.myklyuchik2.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Base64
import com.example.myklyuchik2.data.model.PasswordEntry
import com.example.myklyuchik2.data.storage.SecureStorage
import com.example.myklyuchik2.utils.Constants
import com.example.myklyuchik2.ui.main.MainViewModel
import com.example.myklyuchik2.ui.main.model.UiState
import com.example.myklyuchik2.utils.AppInitializer
import com.example.myklyuchik2.data.storage.DataState
import com.example.myklyuchik2.data.encryption.CryptoService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
// ==================== Состояние формы ====================
data class EntryFormState(
	val id: String = UUID.randomUUID().toString(),
	val resourceName: String = "",
	val login: String = "",
	val password: String = "",
	val changeDate: String = Date().toString(), // Не меняется автоматически
	val url: String = "",
	val email: String = "",
	val authCode: String = "",
	val notes: String = "",
	val tags: List<String> = emptyList(),
	// Служебные поля
	val isLoading: Boolean = false,
	val error: String? = null,
	val isPasswordVisible: Boolean = false,
	val tagInput: String = "",
	// Валидация
	val resourceNameError: String? = null,
	val loginError: String? = null,
	val passwordError: String? = null
) {
	fun isValid(): Boolean = resourceName.isNotBlank() && login.isNotBlank() && password.isNotBlank()

	fun toPasswordEntry(): PasswordEntry = PasswordEntry(
		id = id,
		resourceName = resourceName.trim(),
		login = login.trim(),
		password = password, // Не trim — пароль может содержать пробелы
		changeDate = changeDate,
		url = url.trim(),
		email = email.trim(),
		authCode = authCode.trim(),
		notes = notes.trim(),
		tags = tags,
		createdAt = if (id == UUID.randomUUID().toString()) Date().toString() else "", // Заполнится при создании
		updatedAt = Date().toString()
	)
}

// ==================== События UI ====================
sealed class EntryUiEvent {
	object SaveSuccess : EntryUiEvent()
	object Discard : EntryUiEvent()
	data class ShowError(val message: String) : EntryUiEvent()
}

// ==================== ViewModel ====================
class EntryViewModel(
	private val dataPath: String,
	private val mainViewModel: MainViewModel // ← New parameter
) : ViewModel() {

	private val _formState = MutableStateFlow(EntryFormState())
	val formState: StateFlow<EntryFormState> = _formState.asStateFlow()

	// Инициализация в режиме редактирования
	fun initEdit(entry: PasswordEntry) {
		_formState.update {
			it.copy(
				id = entry.id,
				resourceName = entry.resourceName,
				login = entry.login,
				password = entry.password,
				changeDate = entry.changeDate, // Сохраняем исходную дату изменения пароля!
				url = entry.url,
				email = entry.email,
				authCode = entry.authCode,
				notes = entry.notes,
				tags = entry.tags
			)
		}
	}
	fun loadEntryForEdit(entryId: String) {
		viewModelScope.launch {
			try {
				// Get decrypted password from MainViewModel
				val passwordResult = withContext(Dispatchers.IO) {
					mainViewModel.getDecryptedPassword()
				}

				if (passwordResult.isFailure) {
					_formState.update { it.copy(isLoading = false, error = "Не удалось получить мастер-пароль: ${passwordResult.exceptionOrNull()?.message}") }
					return@launch
				}

				val decryptedPassword = passwordResult.getOrNull()!!

				// Load encrypted data
				val entry = withContext(Dispatchers.IO) {
					val entries = SecureStorage.loadEncrypted(dataPath, decryptedPassword)
					entries.find { it.id == entryId }
				}

				if (entry != null) {
					initEdit(entry) // Заполняем форму найденной записью
				} else {
					_formState.update { it.copy(error = "Запись не найдена") }
				}
			} catch (e: Exception) {
				_formState.update { it.copy(error = "Ошибка загрузки: ${e.message}") }
			}
		}
	}
	// Обновление полей формы
	fun updateResourceName(value: String) {
		_formState.update {
			it.copy(
				resourceName = value,
				resourceNameError = if (value.isBlank() && it.resourceName.isNotBlank()) "Обязательное поле" else null
			)
		}
	}

	fun updateLogin(value: String) {
		_formState.update {
			it.copy(
				login = value,
				loginError = if (value.isBlank() && it.login.isNotBlank()) "Обязательное поле" else null
			)
		}
	}

	fun updatePassword(value: String) {
		_formState.update {
			it.copy(
				password = value,
				passwordError = if (value.isBlank() && it.password.isNotBlank()) "Обязательное поле" else null
			)
		}
	}

	fun updateUrl(value: String) {
		_formState.update { it.copy(url = value) }
	}

	fun updateEmail(value: String) {
		_formState.update { it.copy(email = value) }
	}

	fun updateAuthCode(value: String) {
		_formState.update { it.copy(authCode = value) }
	}

	fun updateNotes(value: String) {
		_formState.update { it.copy(notes = value) }
	}

	fun updateChangeDate(value: String) {
		_formState.update { it.copy(changeDate = value) }
	}

	// Показ/скрытие пароля
	fun togglePasswordVisibility() {
		_formState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
	}

	// Работа с тегами
	fun updateTagInput(value: String) {
		_formState.update { it.copy(tagInput = value) }
	}

	fun addTag() {
		val tag = _formState.value.tagInput.trim()
		if (tag.isNotBlank() && tag !in _formState.value.tags) {
			_formState.update { it.copy(tags = it.tags + tag, tagInput = "") }
		}
	}

	fun removeTag(tag: String) {
		_formState.update { it.copy(tags = it.tags - tag) }
	}

	// Сохранение записи
	fun saveEntry(entry: PasswordEntry, masterPassword: String) {
		viewModelScope.launch {
			try {
				val context = mainViewModel.getContext() // ✅ From MainViewModel
				val dataFile = File(context.filesDir, "passwords.enc")
				val dataState = AppInitializer.determineDataState(context)

				when (dataState) {
					DataState.SpuriousData -> {
						// Delete spurious file if it somehow reappeared
						SecureStorage.deleteDataFile(dataFile.absolutePath)
						// Proceed with creating new file
						saveEntryWithNewDataFile(entry, masterPassword)
					}
					DataState.FirstTimeUse -> {
						// Create new data file
						saveEntryWithNewDataFile(entry, masterPassword)
					}
					DataState.NormalUse -> {
						// Append to existing data file or update existing entry
						appendEntryToExistingFile(entry, masterPassword)
					}
				}
			} catch (e: Exception) {
				_formState.update {
					it.copy(
						isLoading = false,
						error = "Ошибка сохранения: ${e.message}"
					)
				}
			}
		}
	}

	private suspend fun appendEntryToExistingFile(entry: PasswordEntry, masterPassword: String) {
		val context = mainViewModel.getContext()
		val dataFile = File(context.filesDir, "passwords.enc")

		try {
			// Load existing entries
			val existingEntries = SecureStorage.loadEncrypted(dataFile.absolutePath, masterPassword)

			// Determine if we're in CREATE or EDIT mode
			val isEditMode = entry.id.isNotBlank() && existingEntries.any { it.id == entry.id }

			val updatedEntries = if (isEditMode) {
				// Update existing entry: retain createdAt, update updatedAt
				existingEntries.map {
					if (it.id == entry.id) {
						entry.copy(
							createdAt = it.createdAt, // Retain original creation date
							updatedAt = Date().toString()
						)
					} else {
						it
					}
				}
			} else {
				// Create new entry: set both dates
				existingEntries + entry.copy(
					createdAt = Date().toString(),
					updatedAt = Date().toString()
				)
			}

			// Save updated entries
			val salt = SecureStorage.readContainer(dataFile.absolutePath).salt
			val decodedSalt = Base64.decode(salt, Base64.URL_SAFE or Base64.NO_WRAP)

			SecureStorage.saveEncryptedWithSalt(
				entries = updatedEntries,
				masterPassword = masterPassword,
				outputFile = dataFile.absolutePath,
				salt = decodedSalt
			)

			mainViewModel.saveAndReload(updatedEntries)
			_formState.update { it.copy(isLoading = false) }

		} catch (e: Exception) {
			_formState.update {
				it.copy(
					isLoading = false,
					error = "Ошибка обновления данных: ${e.message}"
				)
			}
		}
	}
	private suspend fun saveEntryWithNewDataFile(entry: PasswordEntry, masterPassword: String) {
		val context = mainViewModel.getContext()
		val dataFile = File(context.filesDir, "passwords.enc")

		try {
			// Generate new salt
			val salt = CryptoService.generateSalt()

			// Create new list with entry, set both dates
			val updatedEntry = entry.copy(
				id = entry.id.ifBlank { UUID.randomUUID().toString() },
				createdAt = Date().toString(),
				updatedAt = Date().toString()
			)

			// Save encrypted data
			SecureStorage.saveEncryptedWithSalt(
				entries = listOf(updatedEntry),
				masterPassword = masterPassword,
				outputFile = dataFile.absolutePath,
				salt = salt
			)

			// Mark app as initialized
			AppInitializer.markAppInitialized(context)

			mainViewModel.saveAndReload(listOf(updatedEntry))
			_formState.update { it.copy(isLoading = false) }

		} catch (e: Exception) {
			_formState.update {
				it.copy(
					isLoading = false,
					error = "Ошибка создания файла: ${e.message}"
				)
			}
		}
	}
	// Отмена без сохранения
	fun discardChanges() {
		_formState.update { it.copy(isLoading = false) }
	}
	fun updateFormState(update: (EntryFormState) -> EntryFormState) {
		_formState.value = update(_formState.value)
	}
	// Factory для создания ViewModel с параметрами
class Factory(
	private val dataPath: String,
	private val mainViewModel: MainViewModel // ← New parameter
	) : androidx.lifecycle.ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return EntryViewModel(dataPath, mainViewModel) as T
		}
	}
}