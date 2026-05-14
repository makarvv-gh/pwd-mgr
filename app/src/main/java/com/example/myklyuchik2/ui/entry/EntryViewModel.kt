package com.example.myklyuchik2.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myklyuchik2.data.model.PasswordEntry
import com.example.myklyuchik2.data.storage.SecureStorage
import com.example.myklyuchik2.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

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
	private val masterPassword: String,
	private val onEvent: (EntryUiEvent) -> Unit
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
	fun saveEntry() {
		val state = _formState.value

		// Валидация
		if (!state.isValid()) {
			_formState.update {
				it.copy(
					resourceNameError = if (it.resourceName.isBlank()) "Обязательное поле" else null,
					loginError = if (it.login.isBlank()) "Обязательное поле" else null,
					passwordError = if (it.password.isBlank()) "Обязательное поле" else null
				)
			}
			onEvent(EntryUiEvent.ShowError("Заполните обязательные поля"))
			return
		}

		viewModelScope.launch {
			_formState.update { it.copy(isLoading = true, error = null) }

			try {
				// 1. Загружаем текущий список
				val entries = SecureStorage.loadEncrypted(dataPath, masterPassword).toMutableList()

				// 2. Создаём или обновляем запись
				val newEntry = _formState.value.toPasswordEntry()
				val existingIndex = entries.indexOfFirst { it.id == newEntry.id }

				if (existingIndex >= 0) {
					// Редактирование: сохраняем createdAt, обновляем updatedAt
					entries[existingIndex] = newEntry.copy(
						createdAt = entries[existingIndex].createdAt,
						updatedAt = Date().toString()
					)
				} else {
					// Создание: устанавливаем createdAt
					entries.add(0, newEntry.copy(createdAt = Date().toString(), updatedAt = Date().toString()))
				}

				// 3. Сохраняем обратно
				SecureStorage.saveEncrypted(entries, masterPassword, dataPath)

				_formState.update { it.copy(isLoading = false) }
				onEvent(EntryUiEvent.SaveSuccess)

			} catch (e: Exception) {
				_formState.update {
					it.copy(
						isLoading = false,
						error = "Ошибка сохранения: ${e.message}"
					)
				}
				onEvent(EntryUiEvent.ShowError("Не удалось сохранить: ${e.message}"))
			}
		}
	}

	// Отмена без сохранения
	fun discardChanges() {
		onEvent(EntryUiEvent.Discard)
	}

	// Factory для создания ViewModel с параметрами
	class Factory(
		private val dataPath: String,
		private val masterPassword: String,
		private val onEvent: (EntryUiEvent) -> Unit
	) : androidx.lifecycle.ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return EntryViewModel(dataPath, masterPassword, onEvent) as T
		}
	}
}