package com.example.myklyuchik2.data.importexport

import android.content.res.AssetManager
import com.example.myklyuchik2.data.model.PasswordEntry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset

object CsvImporter {

	/**
	 * Импортирует записи из CSV-файла, лежащего в папке assets.
	 * @param assetManager AssetManager контекста
	 * @param fileName имя файла (например, "example.csv")
	 * @param charset кодировка (по умолчанию Windows-1251)
	 */
	fun importFromAssets(
		assetManager: AssetManager,
		fileName: String,
		charset: Charset = Charset.forName("windows-1251")
	): List<PasswordEntry> {
		val inputStream = assetManager.open(fileName)
		val reader = BufferedReader(InputStreamReader(inputStream, charset))

		val lines = reader.readLines()
		if (lines.isEmpty()) return emptyList()

		val header = lines.first().split(",").map { it.trim().lowercase() }
		val indexOfResource = header.indexOf("resource_name")
		val indexOfLogin = header.indexOf("login")
		val indexOfPassword = header.indexOf("password")
		val indexOfUrl = header.indexOf("url")
		val indexOfEmail = header.indexOf("email")
		val indexOfAuth = header.indexOf("auth_code")
		val indexOfNotes = header.indexOf("notes")
		val indexOfTags = header.indexOf("tags")

		val entries = mutableListOf<PasswordEntry>()
		for (i in 1 until lines.size) {
			val line = lines[i]
			// Простой split с учётом кавычек
			val columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
				.map { it.trim().removeSurrounding("\"") }

			fun get(idx: Int) = if (idx in columns.indices) columns[idx] else ""

			val resourceName = get(indexOfResource)
			if (resourceName.isBlank()) continue

			val tagsStr = get(indexOfTags)
			val tags = if (tagsStr.isNotBlank()) tagsStr.split(";").map { it.trim() } else emptyList()

			entries.add(
				PasswordEntry(
					resourceName = resourceName,
					login = get(indexOfLogin),
					password = get(indexOfPassword),
					url = get(indexOfUrl),
					email = get(indexOfEmail),
					authCode = get(indexOfAuth),
					notes = get(indexOfNotes),
					tags = tags
				)
			)
		}
		reader.close()
		return entries
	}
}