package com.example.myklyuchik2.ui.csvimport

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myklyuchik2.data.encryption.CryptoService
import com.example.myklyuchik2.data.model.PasswordEntry
import com.example.myklyuchik2.data.storage.DataState
import com.example.myklyuchik2.data.storage.SecureStorage
import com.example.myklyuchik2.ui.main.MainViewModel
import com.example.myklyuchik2.utils.AppInitializer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Headless CSV import host.
 *
 * It replaces the old `CsvImportActivity`, which was a leftover from the times when the
 * app showed its own "Select a CSV file" screen instead of the system document picker.
 * The button on that screen is long gone, but the Activity kept drawing an empty themed
 * window for a moment before the user was returned to Settings — it did not follow the
 * day/night color scheme and simply slowed the app down.
 *
 * Now nothing is drawn at all: [SettingsScreen] registers a picker launcher with
 * [androidx.activity.compose.rememberLauncherForActivityResult] and launches the system
 * picker directly. When a file comes back, this ViewModel parses it, persists the entries
 * into the encrypted store, updates the shared [MainViewModel] state and reports the result
 * via its snackbar events — so the user never leaves the Settings screen.
 */
class CsvImportHost(private val mainViewModel: MainViewModel) : ViewModel() {

        companion object {
                private const val TAG = "CsvImportHost"

                /**
                 * Factory that hands out a ViewModel bound to the process-wide
                 * [MainViewModel] singleton, so the import always refreshes the state
                 * the visible screens observe.
                 */
                fun factory(mainViewModel: MainViewModel): ViewModelProvider.Factory =
                        object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        return CsvImportHost(mainViewModel) as T
                                }
                        }

                /**
                 * Keeps read access to [uri] after the result callback returns, so the
                 * (potentially suspending) parsing can't be interrupted by a revoked grant.
                 */
                fun persistReadPermission(context: Context, uri: Uri) {
                        try {
                                context.contentResolver.takePersistableUriPermission(
                                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e: SecurityException) {
                                // Some providers don't grant persistable permissions; the one-time
                                // grant is still enough to read the file while the app is alive.
                                Log.w(TAG, "Persistable permission not available", e)
                        }
                }
        }

        /**
         * Reads and imports the CSV file at [uri]. All failures are reported through the
         * [MainViewModel] event channel (a snackbar on the main screen) instead of a UI of
         * their own — again, so that no extra screen ever has to be drawn.
         */
        fun importCsv(uri: Uri, context: Context) {
                viewModelScope.launch {
                        try {
                                val entries = parseCsv(uri, context)

                                if (entries == null) {
                                        // The error was already reported by parseCsv() through
                                        // the MainViewModel event channel.
                                        return@launch
                                }

                                persistEntries(entries)
                                Log.d(TAG, "Imported ${entries.size} entries from $uri")
                                notifySuccess("Импортировано записей: ${entries.size}")
                        } catch (e: Exception) {
                                Log.e(TAG, "ERROR processing file: $uri", e)
                                notifyError("Ошибка импорта: ${e.message}")
                        }
                }
        }

        // ==================== Internals ====================

        /** Returns the parsed entries, or null when the file could not be imported. */
        private suspend fun parseCsv(uri: Uri, context: Context): List<PasswordEntry>? {
                val inputStream = withContext(Dispatchers.IO) {
                        try {
                                context.contentResolver.openInputStream(uri)
                        } catch (e: Exception) {
                                Log.e(TAG, "Cannot open $uri", e)
                                null
                        }
                } ?: run {
                        notifyError("Не удалось открыть файл")
                        return null
                }

                val rawBytes = withContext(Dispatchers.IO) { inputStream.use { it.readBytes() } }
                if (rawBytes.isEmpty()) {
                        notifyError("Файл пуст")
                        return null
                }

                val decodedText = String(rawBytes, charset("windows-1251"))
                val lines = decodedText.split("\r\n", "\n", "\r").map { it.trim() }

                if (lines.size <= 1) {
                        notifyError("Файл содержит только заголовок или отсутствуют данные")
                        return null
                }

                Log.d(TAG, "First line: ${lines.first()}")

                val header = lines.first().split(",").map { it.trim().lowercase() }
                val indexOfResource = header.indexOf("resource_name")
                val indexOfLogin = header.indexOf("login")
                val indexOfPassword = header.indexOf("password")

                if (indexOfResource == -1 || indexOfLogin == -1 || indexOfPassword == -1) {
                        notifyError("Неверный формат CSV файла")
                        return null
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
                        val tags = if (tagsStr.isNotBlank()) tagsStr.split(";")
                                .map { it.trim() } else emptyList()

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
                        notifyError("Файл не содержит данных")
                        return null
                }
                return entries
        }

        /**
         * State-aware persistence, mirroring what happens when a single entry is saved:
         * before writing the data file we check the app's [DataState]. If the app has never
         * been initialized (FirstTimeUse - no marker file), a freshly written data file would
         * be treated as 'spurious' by AppInitializer.determineDataState() on the next launch
         * and silently deleted, so in that case we create the data file AND the marker file.
         */
        private suspend fun persistEntries(entries: List<PasswordEntry>) {
                val appContext = mainViewModel.getContext()
                val dataPath = File(appContext.filesDir, "passwords.enc").absolutePath
                val decryptedPassword = mainViewModel.getDecryptedPassword().getOrThrow()

                when (AppInitializer.determineDataState(appContext)) {
                        DataState.FirstTimeUse, DataState.NoDataYet -> {
                                // App not yet initialized (or initialized but no data file yet) - initialize
                                // it now: create the data file from scratch (new salt), then the marker file.
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
                                        withContext(Dispatchers.IO) { SecureStorage.deleteDataFile(dataPath) }
                                        notifyError("Файл данных повреждён и удалён. Импортируйте файл ещё раз.")
                                        return
                                }
                                // App initialized, data file exists - append imported entries and re-encrypt,
                                // preserving the existing salt.
                                val currentEntries = mainViewModel.uiState.value.allEntries
                                val newEntries = currentEntries + entries
                                val container = withContext(Dispatchers.IO) { SecureStorage.readContainer(dataPath) }
                                val salt = android.util.Base64.decode(container.salt, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                                withContext(Dispatchers.IO) {
                                        SecureStorage.saveEncryptedWithSalt(newEntries, decryptedPassword, dataPath, salt)
                                }
                                // Update the SHARED MainViewModel so the visible UI refreshes immediately.
                                mainViewModel.saveAndReload(newEntries)
                        }

                        DataState.SpuriousData -> {
                                // determineDataState() already deleted the spurious file above; the app is
                                // effectively in FirstTimeUse now - initialize it like the branch above.
                                val salt = CryptoService.generateSalt()
                                withContext(Dispatchers.IO) {
                                        SecureStorage.saveEncryptedWithSalt(entries, decryptedPassword, dataPath, salt)
                                }
                                AppInitializer.markAppInitialized(appContext)
                                mainViewModel.saveAndReload(entries)
                        }
                }
        }

        private suspend fun notifyError(message: String) {
                mainViewModel.notifyCsvImportError(message)
        }

        private suspend fun notifySuccess(message: String) {
                mainViewModel.notifyCsvImportSuccess(message)
        }
}
