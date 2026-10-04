package com.example.myklyuchik2.ui.main

import android.content.Context
import android.content.res.AssetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import com.example.myklyuchik2.data.importexport.CsvImporter
import com.example.myklyuchik2.data.model.PasswordEntry
import com.example.myklyuchik2.data.storage.JsonStorage
import com.example.myklyuchik2.data.storage.SecurePasswordStorage
import com.example.myklyuchik2.data.storage.SecureStorage
import com.example.myklyuchik2.ui.main.model.UiEvent
import com.example.myklyuchik2.ui.main.model.UiState
import com.example.myklyuchik2.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import android.util.Log


// ==================== ViewModel ====================
class MainViewModel private constructor(
	private val context: Context,
	private val assetManager: AssetManager
) : ViewModel() {

	companion object {
		/**
		 * Single shared instance. MainActivity's AppNavHost and MainScreen used to
		 * create SEPARATE MainViewModel instances (each viewModel() call without a
		 * key created its own entry in the Activity's ViewModelStore). The import
		 * updated one copy while the visible screen observed another — exactly the
		 * "entries appear only after relaunch" symptom. A process-wide singleton
		 * guarantees every screen observes the same state.
		 */
		@Volatile
		private var instance: MainViewModel? = null

		fun getInstance(context: Context, assetManager: AssetManager): MainViewModel {
			return instance ?: synchronized(this) {
				instance ?: MainViewModel(
					context.applicationContext,
					assetManager
				).also { instance = it }
			}
		}
	}

	private val _uiState = MutableStateFlow(UiState())
	val uiState: StateFlow<UiState> = _uiState.asStateFlow()

	private val _events = kotlinx.coroutines.channels.Channel<UiEvent>()
	val events = _events.receiveAsFlow()

	private val dataPath: String by lazy {
		File(context.filesDir, "passwords.enc").absolutePath
	}

	private val tempJsonPath: String by lazy {
		File(context.filesDir, "temp.json").absolutePath
	}

	init {
		// NOTE: loadEntries() is deliberately NOT called here. The singleton is
		// created eagerly by AppNavHost before the master password has been
		// entered/verified; loading at construction time set isLoading = true
		// and then bailed out early (decryptPassword() returns null before
		// authentication / on first use), leaving the spinner running forever.
		// Loading is triggered explicitly from MainScreen when it becomes visible.
	}

	fun loadEntries() {
		viewModelScope.launch {
			_uiState.update { it.copy(isLoading = true) }
			try {
				val dataFile = File(dataPath)
				if (!dataFile.exists()) {
					// First-time use (or app reset): nothing to load yet — show
					// the empty state instead of an infinite spinner.
					publishEntries(emptyList())
					return@launch
				}

				val storage = SecurePasswordStorage.getInstance(context)
				val decryptedPassword = storage.decryptPassword() ?: run {
					// Master password not available (not yet verified). Clear the
					// flag so the UI can render the empty state rather than spin.
					_uiState.update { it.copy(isLoading = false) }
					return@launch
				}

				val entries = SecureStorage.loadEncrypted(dataPath, decryptedPassword)
				publishEntries(entries)
			} catch (e: Exception) {
				_uiState.update { it.copy(isLoading = false, error = e.message) }
				_events.send(UiEvent.ShowError("Ошибка загрузки: ${e.message}"))
			}
		}
	}

	fun importFromCsv(fileName: String) {
		viewModelScope.launch {
			_uiState.update { it.copy(isLoading = true) }
			try {
				val entries = CsvImporter.importFromAssets(assetManager, fileName)
				val storage = SecurePasswordStorage.getInstance(context)
				val decryptedPassword = storage.decryptPassword() ?: return@launch
				SecureStorage.saveEncrypted(entries, decryptedPassword, dataPath)
				// Reload from disk so state is refreshed after the save.
				loadEntries()
				_events.send(UiEvent.ShowSuccess("Импортировано ${entries.size} записей"))
			} catch (e: Exception) {
				_uiState.update { it.copy(isLoading = false, error = e.message) }
				_events.send(UiEvent.ShowError("Ошибка импорта: ${e.message}"))
			}
		}
	}

	fun addEntry(entry: PasswordEntry) {
		viewModelScope.launch {
			val current = _uiState.value.allEntries.toMutableList()
			current.add(0, entry)
			saveAndPersist(current)
			_events.send(UiEvent.NavigateBack)
		}
	}

	fun updateEntry(updated: PasswordEntry) {
		viewModelScope.launch {
			val current = _uiState.value.allEntries.toMutableList()
			val idx = current.indexOfFirst { it.id == updated.id }
			if (idx >= 0) {
				current[idx] = updated.copy(updatedAt = Date().toString())
				saveAndPersist(current)
			}
			_events.send(UiEvent.NavigateBack)
		}
	}

	fun deleteEntry(entry: PasswordEntry) {
		viewModelScope.launch {
			val current = _uiState.value.allEntries.toMutableList()
			val removed = current.removeIf { it.id == entry.id }
			if (removed) saveAndPersist(current)
		}
	}

	/**
	 * Persists [entries] to the encrypted store AND publishes them into [uiState].
	 * Used by all in-app mutations (add/update/delete).
	 */
	private suspend fun saveAndPersist(entries: List<PasswordEntry>) {
		val storage = SecurePasswordStorage.getInstance(context)
		val decryptedPassword = storage.decryptPassword() ?: return
		withContext(Dispatchers.IO) {
			SecureStorage.saveEncrypted(entries, decryptedPassword, dataPath)
		}
		publishEntries(entries)
	}

	/**
	 * State-only refresh of [uiState]. The CSV import path persists the file
	 * itself (SecureStorage.saveEncryptedWithSalt, preserving the container salt),
	 * then calls this — previously saveAndReload also re-wrote the file with a
	 * different scheme, clobbering the freshly imported data.
	 */
	suspend fun saveAndReload(entries: List<PasswordEntry>) {
		publishEntries(entries)
	}

	private suspend fun publishEntries(entries: List<PasswordEntry>) {
		viewModelScope.launch {
			Log.d("MainViewModel", "Publishing ${entries.size} entries to UI state")
			_uiState.update {
				it.copy(
					isLoading = false,
					allEntries = entries,
					filteredEntries = applyFilters(entries, it.filters)
				)
			}
		}.join() // Ensure the state is published before returning so callers
		// (e.g. CSV import finishing its Activity) don't race the recomposition.
	}

	fun updateSearchQuery(query: String) {
		_uiState.update { state ->
			val newFilters = state.filters.copy(searchQuery = query)
			state.copy(
				filters = newFilters,
				filteredEntries = applyFilters(state.allEntries, newFilters)
			)
		}
	}

	fun updateTagFilter(tags: List<String>) {
		_uiState.update { state ->
			val newFilters = state.filters.copy(tagFilters = tags)
			state.copy(
				filters = newFilters,
				filteredEntries = applyFilters(state.allEntries, newFilters)
			)
		}
	}

	fun removeTagFilter(tag: String) {
		_uiState.update { state ->
			val newTags = state.filters.tagFilters - tag
			val newFilters = state.filters.copy(tagFilters = newTags)
			state.copy(
				filters = newFilters,
				filteredEntries = applyFilters(state.allEntries, newFilters)
			)
		}
	}

	fun clearFilters() {
		_uiState.update { state ->
			val newFilters = UiState.Filters()
			state.copy(
				filters = newFilters,
				filteredEntries = applyFilters(state.allEntries, newFilters)
			)
		}
	}

	fun toggleFilterSheet(show: Boolean) {
		_uiState.update { it.copy(showFilterSheet = show) }
	}

	fun toggleSettingsDialog(show: Boolean) {
		_uiState.update { it.copy(showSettingsDialog = show) }
	}

	fun setEntryToEdit(entry: PasswordEntry?) {
		_uiState.update { it.copy(entryToEdit = entry) }
	}

	private fun applyFilters(entries: List<PasswordEntry>, filters: UiState.Filters): List<PasswordEntry> {
		return entries.filter { entry ->
			val matchesSearch = filters.searchQuery.isBlank() ||
					entry.resourceName.contains(filters.searchQuery, ignoreCase = true) ||
					entry.login.contains(filters.searchQuery, ignoreCase = true)
			val matchesTags = filters.tagFilters.isEmpty() ||
					//filters.tagFilters.all { tag -> entry.tags.any { it.equals(tag, ignoreCase = true) } }
					filters.tagFilters.all { tag -> entry.tags.any { it.compareTo(tag, ignoreCase = true) == 0 } }
			matchesSearch && matchesTags
		}
	}

	suspend fun getDecryptedPassword(): Result<String> {
		val storage = SecurePasswordStorage.getInstance(context)
		return try {
			val decryptedPassword = storage.decryptPassword() ?: throw Exception("Password decryption failed")
			Result.success(decryptedPassword)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}
	fun getContext(): Context = context
	// Factory для создания ViewModel с параметрами
	class Factory(
		private val context: Context,
		private val assetManager: AssetManager
	) : ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			// Always hand out the process-wide singleton so every screen and the
			// CSV import path observe/mutate the SAME state.
			return MainViewModel.getInstance(context, assetManager) as T
		}
	}
}