package com.example.myklyuchik2.ui.main.model

import com.example.myklyuchik2.data.model.PasswordEntry

data class UiState(
	val isLoading: Boolean = false,
	val error: String? = null,
	val allEntries: List<PasswordEntry> = emptyList(),
	val filteredEntries: List<PasswordEntry> = emptyList(),
	val filters: Filters = Filters(),
	val showFilterSheet: Boolean = false,
	val showSettingsDialog: Boolean = false,
	val entryToEdit: PasswordEntry? = null
) {
	data class Filters(
		val searchQuery: String = "",
		val tagFilters: List<String> = emptyList()
	)
}