package com.example.myklyuchik2.ui.csvimport

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myklyuchik2.data.storage.SecureStorage
import com.example.myklyuchik2.data.model.PasswordEntry
import com.example.myklyuchik2.data.repository.PasswordRepository
import com.example.myklyuchik2.ui.main.MainViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CsvImportViewModel : ViewModel() {
	private val _importResult = MutableStateFlow<CsvImportResult>(CsvImportResult.Success(emptyList()))
	val importResult: StateFlow<CsvImportResult> = _importResult

	private lateinit var mainViewModel: MainViewModel
	private lateinit var passwordRepository: PasswordRepository

	fun setDependencies(mainViewModel: MainViewModel, passwordRepository: PasswordRepository) {
		this.mainViewModel = mainViewModel
		this.passwordRepository = passwordRepository
	}

	fun selectCsvFile() {
		// This will be implemented in the activity using Intent
	}

	fun processCsvFile(uri: Uri, context: Context) {
		viewModelScope.launch {
			try {
				val inputStream = context.contentResolver.openInputStream(uri)
				val reader = inputStream?.bufferedReader()

				if (reader == null) {
					_importResult.value = CsvImportResult.Error("Не удалось открыть файл")
					return@launch // ✅ Correct early return in coroutine scope
				}

				val lines = reader.readLines()
				if (lines.isEmpty()) {
					_importResult.value = CsvImportResult.Error("Файл пуст")
					return@launch // ✅ Correct early return in coroutine scope
				}

				val header = lines.first().split(",").map { it.trim().lowercase() }
				val indexOfResource = header.indexOf("resource_name")
				val indexOfLogin = header.indexOf("login")
				val indexOfPassword = header.indexOf("password")

				// Basic validation
				if (indexOfResource == -1 || indexOfLogin == -1 || indexOfPassword == -1) {
					_importResult.value = CsvImportResult.Error("Неверный формат CSV файла")
					return@launch // ✅ Correct early return in coroutine scope
				}

				val entries = mutableListOf<PasswordEntry>()
				for (i in 1 until lines.size) {
					val line = lines[i]
					val columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
						.map { it.trim().removeSurrounding("\"") }

					fun get(idx: Int) = if (idx in columns.indices) columns[idx] else ""

					val resourceName = get(indexOfResource)
					if (resourceName.isBlank()) continue

					val tagsStr = get(header.indexOf("tags"))
					val tags = if (tagsStr.isNotBlank()) tagsStr.split(";").map { it.trim() } else emptyList()

					entries.add(
						PasswordEntry(
							resourceName = resourceName,
							login = get(indexOfLogin),
							password = get(indexOfPassword),
							url = get(header.indexOf("url")),
							email = get(header.indexOf("email")),
							authCode = get(header.indexOf("auth_code")),
							notes = get(header.indexOf("notes")),
							tags = tags
						)
					)
				}

				// Append imported entries to existing entries
				val currentEntries = mainViewModel.uiState.value.allEntries
				val newEntries = currentEntries + entries

				// Save updated entries with re-encryption
				try {
					// Get the current password
					val decryptedPassword = passwordRepository.getCurrentPassword() ?: throw Exception("Не удалось получить мастер-пароль")

					// Get the data path
					val dataPath = File(context.filesDir, "passwords.enc").absolutePath

					// Get the container to access the existing salt
					val container = SecureStorage.readContainer(dataPath)
					val salt = Base64.decode(container.salt, Base64.URL_SAFE or Base64.NO_WRAP)

					// Re-encrypt with the same salt but new password
					SecureStorage.saveEncryptedWithSalt(newEntries, decryptedPassword, dataPath, salt)

					// Update the UI
					mainViewModel.saveAndReload(newEntries)

					_importResult.value = CsvImportResult.Success(entries)
				} catch (e: Exception) {
					_importResult.value = CsvImportResult.Error("Ошибка сохранения данных: ${e.message}")
				}
			} catch (e: Exception) {
				_importResult.value = CsvImportResult.Error("Ошибка чтения файла: ${e.message}")
			}
		}
	}
}
