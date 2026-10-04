package com.example.myklyuchik2.ui.csvimport

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myklyuchik2.data.encryption.CryptoService
import com.example.myklyuchik2.data.storage.DataState
import com.example.myklyuchik2.data.storage.SecureStorage
import com.example.myklyuchik2.data.model.PasswordEntry
import com.example.myklyuchik2.ui.main.MainViewModel
import com.example.myklyuchik2.utils.AppInitializer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

sealed class CsvImportResult {
/** Import is running (or waiting for the user to pick a file). */
object InProgress : CsvImportResult()
/** The user dismissed the file picker without choosing a file. */
object Cancelled : CsvImportResult()
data class Success(val entries: List<PasswordEntry>) : CsvImportResult()
data class Error(val message: String) : CsvImportResult()
}

/**
 * Parses a CSV picked via SAF, re-encrypts and persists all entries, and — crucially —
 * updates the SAME MainViewModel instance that MainActivity's screens observe.
 *
 * Sharing happens through [sharedMainViewModel], installed by MainActivity when its
 * MainViewModel is created. Previously CsvImportActivity built a second, private
 * MainViewModel; the import updated that orphan copy, so the visible UI stayed stale
 * until a process restart forced a reload from disk.
 */
class CsvImportViewModel : ViewModel() {

companion object {
@Volatile
private var sharedMainViewModel: MainViewModel? = null

/** Called by MainActivity right after it creates its MainViewModel. */
fun attachSharedMainViewModel(vm: MainViewModel) {
sharedMainViewModel = vm
}
}

private val _importResult = MutableStateFlow<CsvImportResult>(CsvImportResult.InProgress)
val importResult: StateFlow<CsvImportResult> = _importResult

fun resetImportState() {
_importResult.value = CsvImportResult.InProgress
}

fun markCancelled() {
_importResult.value = CsvImportResult.Cancelled
}

fun processCsvFile(uri: Uri, context: Context) {
viewModelScope.launch {
try {
val mainViewModel = sharedMainViewModel ?: run {
_importResult.value = CsvImportResult.Error(
"Основной экран недоступен: импортированные данные появятся после перезапуска приложения"
)
return@launch
}

val inputStream = withContext(Dispatchers.IO) {
context.contentResolver.openInputStream(uri)
} ?: run {
_importResult.value = CsvImportResult.Error("Не удалось открыть файл")
return@launch
}

val rawBytes = withContext(Dispatchers.IO) { inputStream.use { it.readBytes() } }
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

// ==================== State-aware persistence ====================
// Mirror the logic used when saving a new entry (EntryViewModel): before writing
// the data file we must check the app's DataState. If the app has never been
// initialized (FirstTimeUse - no marker file), the freshly written data file
// would be treated as 'spurious' by AppInitializer.determineDataState() on the
// next launch and silently deleted. So in that case we must create the data
// file with the imported entries AND create the install marker file.
try {
val appContext = mainViewModel.getContext()
val dataPath = File(appContext.filesDir, "passwords.enc").absolutePath
val dataState = AppInitializer.determineDataState(appContext)

when (dataState) {
DataState.FirstTimeUse -> {
// App not yet initialized - initialize it now:
// create the data file from scratch (new salt), then the marker file.
val decryptedPassword = mainViewModel.getDecryptedPassword().getOrThrow()
val salt = CryptoService.generateSalt()
withContext(Dispatchers.IO) {
SecureStorage.saveEncryptedWithSalt(entries, decryptedPassword, dataPath, salt)
}
AppInitializer.markAppInitialized(appContext)
mainViewModel.saveAndReload(entries)
}

DataState.NormalUse -> {
if (!SecureStorage.hasValidData(dataPath)) {
// Very unlikely, but just in case the data file became corrupted:
// delete both data and marker files so the app resets to FirstTimeUse.
AppInitializer.clearInstallMarker(appContext)
SecureStorage.deleteDataFile(dataPath)
_importResult.value = CsvImportResult.Error(
"Файл данных повреждён и удалён. Импортируйте файл ещё раз."
)
return@launch
}
// App initialized, data file exists - append imported entries and re-encrypt,
// preserving the existing salt.
val decryptedPassword = mainViewModel.getDecryptedPassword().getOrThrow()
val currentEntries = mainViewModel.uiState.value.allEntries
val newEntries = currentEntries + entries
val container = SecureStorage.readContainer(dataPath)
val salt = if (container.salt.isNullOrEmpty()) {
CryptoService.generateSalt()
} else {
Base64.decode(container.salt, Base64.URL_SAFE or Base64.NO_WRAP)
}
withContext(Dispatchers.IO) {
SecureStorage.saveEncryptedWithSalt(newEntries, decryptedPassword, dataPath, salt)
}
// Update the SHARED MainViewModel so the visible UI refreshes immediately.
mainViewModel.saveAndReload(newEntries)
}

DataState.SpuriousData -> {
// determineDataState() already deleted the spurious file above; the app is
// effectively in FirstTimeUse now - initialize it like the branch above.
val decryptedPassword = mainViewModel.getDecryptedPassword().getOrThrow()
val salt = CryptoService.generateSalt()
withContext(Dispatchers.IO) {
SecureStorage.saveEncryptedWithSalt(entries, decryptedPassword, dataPath, salt)
}
AppInitializer.markAppInitialized(appContext)
mainViewModel.saveAndReload(entries)
}
}

_importResult.value = CsvImportResult.Success(entries)
} catch (e: Exception) {
_importResult.value = CsvImportResult.Error("Ошибка сохранения данных: ${e.message}")
}
} catch (e: Exception) {
Log.e("CsvImportViewModel", "ERROR processing file: $uri", e)
_importResult.value = CsvImportResult.Error("Ошибка чтения файла: ${e.message}")
}
}
}
