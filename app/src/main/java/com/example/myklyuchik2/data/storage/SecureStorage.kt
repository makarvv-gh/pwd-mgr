package com.example.myklyuchik2.data.storage


import com.example.myklyuchik2.data.encryption.CryptoService
import com.example.myklyuchik2.data.model.PasswordEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import android.util.Log

object SecureStorage {

	data class StorageContainer(
		val salt: String,
		val encrypted_data: String
	)

	private val gson = Gson()

	fun saveEncrypted(entries: List<PasswordEntry>, masterPassword: String, outputFile: String) {
		val plainJson = gson.toJson(entries.map { it.toDict() })
		val encrypted = CryptoService.encrypt(plainJson, masterPassword)
		val container = StorageContainer(encrypted.salt, encrypted.encryptedData)
		File(outputFile).writeText(gson.toJson(container))
	}

	fun loadEncrypted(inputFile: String, masterPassword: String): List<PasswordEntry> {
		val file = File(inputFile)
		if (!file.exists()) return emptyList()
		val jsonContent = file.readText()
		val container: StorageContainer = gson.fromJson(jsonContent, StorageContainer::class.java)

		val decryptedJson = CryptoService.decrypt(
			CryptoService.EncryptedContainer(container.salt, container.encrypted_data),
			masterPassword
		)
		// 🔍 Log the raw decrypted JSON string
		Log.d("SecureStorage", "Decrypted JSON: $decryptedJson")

		val type = object : TypeToken<List<Map<String, Any>>>() {}.type
		val entries: List<PasswordEntry> = gson.fromJson(decryptedJson, type)

		/*/ 🔍 Log each entry's resourceName and login
		entries.forEachIndexed { index, entry ->
			Log.d("SecureStorage", "Entry $index: resourceName='${entry.resourceName}', login='${entry.login}'")
		}*/


		val listOfMaps: List<Map<String, Any>> = gson.fromJson(decryptedJson, type)
		return listOfMaps.map { PasswordEntry.fromDict(it) }
	}
}