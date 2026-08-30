package com.example.myklyuchik2.data.storage


import com.example.myklyuchik2.data.encryption.CryptoService
import com.example.myklyuchik2.data.model.PasswordEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import android.util.Log
import android.util.Base64


/**
 * Represents the possible states of the data file and marker file combination
 */
sealed class DataState {
	/**
	 * Indicates that a data file exists but no marker file is present
	 * This is considered a spurious file that should be deleted
	 */
	object SpuriousData : DataState()

	/**
	 * Indicates that neither data file nor marker file exists
	 * This is the first-time use state
	 */
	object FirstTimeUse : DataState()

	/**
	 * Indicates that both data file and marker file exist
	 * This is the normal use state
	 */
	object NormalUse : DataState()
}

/**
 * SecureStorage object handles encrypted data persistence
 */
object SecureStorage {

	data class StorageContainer(
		val salt: String,
		val encrypted_data: String
	)

	private val gson = Gson()

	fun saveEncryptedWithSalt(
		entries: List<PasswordEntry>,
		masterPassword: String,
		outputFile: String,
		salt: ByteArray
	) {
		val plainJson = gson.toJson(entries.map { it.toDict() })
		val encrypted = CryptoService.encryptWithSalt(plainJson, masterPassword, salt)
		val container = StorageContainer(encrypted.salt, encrypted.encryptedData)
		File(outputFile).writeText(gson.toJson(container))
	}

	// Existing saveEncrypted method modified to use the new method
	fun saveEncrypted(
		entries: List<PasswordEntry>,
		masterPassword: String,
		outputFile: String
	) {
		// Generate a new salt by reading the container (which will be empty)
		val container = readContainer(outputFile)
		val salt = Base64.decode(container.salt, Base64.URL_SAFE or Base64.NO_WRAP)

		// Use the new method with explicit salt
		saveEncryptedWithSalt(entries, masterPassword, outputFile, salt)
	}

	fun loadEncrypted(inputFile: String, masterPassword: String): List<PasswordEntry> {
		val file = File(inputFile)
		if (!file.exists()) return emptyList()

		try {
			val jsonContent = file.readText()
			if (jsonContent.trim().isEmpty()) {
				return emptyList()
			}

			val container: StorageContainer =
				gson.fromJson(jsonContent, StorageContainer::class.java)

			val decryptedJson = CryptoService.decrypt(
				CryptoService.EncryptedContainer(container.salt, container.encrypted_data),
				masterPassword
			)
			// 🔍 Log the raw decrypted JSON string
			Log.d("SecureStorage", "Decrypted JSON: $decryptedJson")

			val type = object : TypeToken<List<Map<String, Any>>>() {}.type
			val entries: List<PasswordEntry> = gson.fromJson(decryptedJson, type)

			val listOfMaps: List<Map<String, Any>> = gson.fromJson(decryptedJson, type)
			return listOfMaps.map { PasswordEntry.fromDict(it) }
		}catch (e: Exception) {
			// Log the error and return an empty list
			Log.e("SecureStorage", "Error loading encrypted data: ${e.message}", e)
			return emptyList()
		}
	}

	fun readContainer(filePath: String): StorageContainer {
		val file = File(filePath)
		if (!file.exists()) return StorageContainer("", "")

		val jsonContent = file.readText()
		return try {
			gson.fromJson(jsonContent, StorageContainer::class.java)
		} catch (e: Exception) {
			StorageContainer("", "")
		}
	}
	/**
	 * Checks if the data file exists and contains valid encrypted data.
	 * @param inputFile Path to the encrypted data file
	 * @return true if the file exists and contains both salt and encrypted data
	 */
	fun hasValidData(inputFile: String): Boolean {
		val file = File(inputFile)
		if (!file.exists()) return false

		return try {
			val jsonContent = file.readText()
			if (jsonContent.trim().isEmpty()) false
			else {
				val container: StorageContainer = gson.fromJson(jsonContent, StorageContainer::class.java)
				container.salt.isNotBlank() && container.encrypted_data.isNotBlank()
			}
		} catch (e: Exception) {
			false
		}
	}
	/**
	 * Deletes the data file if it exists
	 * @param inputFile Path to the encrypted data file
	 */
	fun deleteDataFile(inputFile: String) {
		val file = File(inputFile)
		if (file.exists()) {
			file.delete()
			Log.d("SecureStorage", "Spurious data file's been silently deleted")
		}
	}
}