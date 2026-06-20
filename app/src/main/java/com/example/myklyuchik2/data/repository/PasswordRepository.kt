package com.example.myklyuchik2.data.repository

import android.content.Context
import com.example.myklyuchik2.data.storage.SecurePasswordStorage

class PasswordRepository(context: Context) {
	private val securePasswordStorage = SecurePasswordStorage(context)
	private var encryptedPassword: String? = null

	companion object {
		@Volatile
		private var INSTANCE: PasswordRepository? = null

		fun getInstance(context: Context): PasswordRepository {
			return INSTANCE ?: synchronized(this) {
				val instance = PasswordRepository(context)
				INSTANCE = instance
				instance
			}
		}
		// Temporary migration constant
		private const val LEGACY_MASTER_PASSWORD = "master123"
	}
/* боевая версия
	init {
		// Initialize with default password if no password is stored yet
		if (encryptedPassword == null) {
			encryptedPassword = securePasswordStorage.encrypt("default_master_password")
		}
	}*/
init {
	// Try to get password from storage
	encryptedPassword = try {
		// Try to decrypt existing password
		val encrypted = securePasswordStorage.decrypt(LEGACY_MASTER_PASSWORD)
		encrypted?.let {
			// If we can decrypt it, store it in the new format
			encryptedPassword = securePasswordStorage.encrypt(it)
			encryptedPassword
		} ?: run {
			// If not, check if storage is empty and use legacy password
			if (securePasswordStorage is SecurePasswordStorage && encryptedPassword == null) {
				securePasswordStorage.encrypt(LEGACY_MASTER_PASSWORD)
			} else {
				null
			}
		}
	} catch (e: Exception) {
		// If decryption fails, use legacy password
		securePasswordStorage.encrypt(LEGACY_MASTER_PASSWORD)
	}
}
	fun getMasterPassword(): String? {
		encryptedPassword?.let { encrypted ->
			return securePasswordStorage.decrypt(encrypted)
		}
		return null
	}

	fun updateMasterPassword(newPassword: String): Boolean {
		encryptedPassword = securePasswordStorage.encrypt(newPassword)
		return encryptedPassword != null
	}

	fun verifyPassword(inputPassword: String): Boolean {
		val storedPassword = getMasterPassword()
		return inputPassword == storedPassword
	}
}
