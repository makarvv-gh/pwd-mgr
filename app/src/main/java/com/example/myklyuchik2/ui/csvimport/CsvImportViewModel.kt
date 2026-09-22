package com.example.myklyuchik2.ui.csvimport

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myklyuchik2.data.encryption.CryptoService
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

	fun resetImportState() {
		_importResult.value = CsvImportResult.Success(emptyList())
	}
	fun processCsvFile(uri: Uri, context: Context) {
		viewModelScope.launch {
			try {
				val inputStream = context.contentResolver.openInputStream(uri)
					?: run {
						_importResult.value = CsvImportResult.Error("Не удалось открыть файл")
						return@launch
					}

				val rawBytes = inputStream.readBytes()
				if (rawBytes.isEmpty()) {
					_importResult.value = CsvImportResult.Error("Файл пуст")
					return@launch
				}

				val decodedText = String(rawBytes, charset("windows-1251"))
				val lines = decodedText.split("\r\n", "\n", "\r").map { it.trim() }

				if (lines.size <= 1) {
					_importResult.value = CsvImportResult.Error("Файл содержит только заголовок или отсутствуют данные")
					return@launch
				}

				Log.d("CsvImportViewModel", "First line: ${lines.first()}")
				Log.d("CsvImportViewModel", "Decoded text sample: ${decodedText.take(200)}")

				val header = lines.first().split(",").map { it.trim().lowercase() }
				val indexOfResource = header.indexOf("resource_name")
				val indexOfLogin = header.indexOf("login")
				val indexOfPassword = header.indexOf("password")

				if (indexOfResource == -1 || indexOfLogin == -1 || indexOfPassword == -1) {
					_importResult.value = CsvImportResult.Error("Неверный формат CSV файла")
					return@launch
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
				if (entries.isEmpty()) {
					_importResult.value = CsvImportResult.Error("Файл не содержит данных")
					return@launch
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

					// Get the container to access the existing salt (or generate new if missing)
					val container = SecureStorage.readContainer(dataPath)
					val salt = if (container.salt.isNullOrEmpty()) {
						// No existing data file — generate a new random salt
						CryptoService.generateSalt()
					} else {
						// Use existing salt
						Base64.decode(container.salt, Base64.URL_SAFE or Base64.NO_WRAP)
					}

					// Re-encrypt with the salt (existing or new)
					Log.d("CsvImportViewModel", "Using salt: ${salt.joinToString(":") { "%02x".format(it) }}")

					SecureStorage.saveEncryptedWithSalt(newEntries, decryptedPassword, dataPath, salt)

					// Update the UI
					mainViewModel.saveAndReload(newEntries)

					_importResult.value = CsvImportResult.Success(entries)
				} catch (e: Exception) {
					_importResult.value = CsvImportResult.Error("Ошибка сохранения данных: ${e.message}")
				}
			} catch (e: Exception) {
				Log.d("ProcessCsvFile", "ERROR Processing file: $uri")
				_importResult.value = CsvImportResult.Error("Ошибка чтения файла: ${e.message}")
			}
		}
	}
}
