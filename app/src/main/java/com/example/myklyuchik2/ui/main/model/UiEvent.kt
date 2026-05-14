package com.example.myklyuchik2.ui.main.model

sealed class UiEvent {
	object NavigateBack : UiEvent()
	data class ShowError(val message: String) : UiEvent()
	data class ShowSuccess(val message: String) : UiEvent()
	data class EditEntry(val entry: com.example.myklyuchik2.data.model.PasswordEntry) : UiEvent()
}