package com.example.myklyuchik2.data.storage

import com.example.myklyuchik2.data.model.PasswordEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object JsonStorage {

	private val gson = Gson()

	fun saveToJsonFile(entries: List<PasswordEntry>, filePath: String) {
		val listOfMaps = entries.map { it.toDict() }
		val json = gson.toJson(listOfMaps)
		File(filePath).writeText(json)
	}

	fun loadFromJsonFile(filePath: String): List<PasswordEntry> {
		val file = File(filePath)
		if (!file.exists()) return emptyList()
		val json = file.readText()
		val type = object : TypeToken<List<Map<String, Any>>>() {}.type
		val listOfMaps: List<Map<String, Any>> = gson.fromJson(json, type)
		return listOfMaps.map { PasswordEntry.fromDict(it) }
	}
}