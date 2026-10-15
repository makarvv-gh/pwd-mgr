package com.example.myklyuchik2.utils

object DateFormatter {
	private val inputFormat by lazy {
		java.text.SimpleDateFormat("yyyy-MM-dd")
	}
	private val displayFormat by lazy {
		java.text.SimpleDateFormat("dd.MM.yyyy")
	}

	fun parseInputDate(dateStr: String): java.util.Date? {
		return try {
			inputFormat.parse(dateStr)
		} catch (e: Exception) {
			null
		}
	}

	fun formatForDisplay(date: java.util.Date): String {
		return displayFormat.format(date)
	}

	fun formatForStorage(date: java.util.Date): String {
		return inputFormat.format(date)
	}
}