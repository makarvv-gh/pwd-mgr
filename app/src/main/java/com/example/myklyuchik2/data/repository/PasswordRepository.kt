package com.example.myklyuchik2.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.myklyuchik2.data.storage.SecurePasswordStorage
import com.example.myklyuchik2.data.storage.SecureStorage
import com.example.myklyuchik2.data.model.PasswordEntry
import com.example.myklyuchik2.ui.main.MainViewModel
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.exists
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PasswordRepository private constructor(
	context: Context, // Rename to appContext to be explicit
	private val mainViewModel: MainViewModel
) : ViewModel() {
	private val appContext = context.applicationContext
	companion object {
		@Volatile
		private var instance: PasswordRepository? = null

		fun getInstance(context: Context, mainViewModel: MainViewModel): PasswordRepository {
			return instance ?: synchronized(this) {
				PasswordRepository(context.applicationContext, mainViewModel).also {
					instance = it
				}
			}
		}
	}

	private val securePasswordStorage = SecurePasswordStorage.getInstance(context)

	suspend fun getCurrentPassword(): String? = withContext(Dispatchers.IO) {
		securePasswordStorage.decryptPassword()
	}

	suspend fun isPasswordSet(): Boolean = securePasswordStorage.isPasswordSet()

	suspend fun changePassword(oldPassword: String, newPassword: String): Boolean {
		// Validate inputs
		if (oldPassword.isEmpty() || newPassword.isEmpty()) {
			return false
		}

		return try {
			// Get current password from storage
			val currentPassword = getCurrentPassword() ?: return false

			// Constant-time comparison
			if (!constantTimeCompare(oldPassword, currentPassword)) {
				return false
			}

			// Update with new password
			val success = securePasswordStorage.encryptAndStorePassword(newPassword)
			if (!success) {
				return false
			}

			// Add this new code to re-encrypt the entries file
			try {
				// Get the current entries from MainViewModel or from memory
				val currentEntries = mainViewModel.uiState.value.allEntries

				// Get the data path
				val dataPath = File(appContext.filesDir, "passwords.enc").absolutePath

				// Get the container to access the existing salt
				val container = SecureStorage.readContainer(dataPath)
				val salt = Base64.decode(container.salt, Base64.URL_SAFE or Base64.NO_WRAP)

				// Re-encrypt with the same salt but new password
				SecureStorage.saveEncryptedWithSalt(currentEntries, newPassword, dataPath, salt)
			} catch (e: Exception) {
				// Log the error but continue with password change
				Log.e("PasswordRepository", "Error re-encrypting entries", e)
				return false
			}

			// Round-trip verification
			val verifyPassword = getCurrentPassword()
			verifyPassword != null && constantTimeCompare(verifyPassword, newPassword)
		} catch (e: Exception) {
			e.printStackTrace()
			false
		}
		return true
	}

	private fun constantTimeCompare(a: String, b: String): Boolean {
		var result = 0
		for (i in 0 until a.length.coerceAtMost(b.length)) {
			result = result or (a[i].code xor b[i].code)
		}
		return result == 0 && a.length == b.length
	}

	/*suspend fun initializeEmptyFile(masterPassword: String): Boolean = withContext(Dispatchers.IO) {
		try {
			val dataPath = File(appContext.filesDir, "passwords.enc")
			if (!dataPath.exists()) {
				val emptyEntries = emptyList<PasswordEntry>()
				val salt = generateSalt()
				val container = StorageContainer(
					salt = Base64.encodeToString(salt, Base64.URL_SAFE or Base64.NO_WRAP),
					encrypted_data = ""
				)
				File(dataPath).writeText(gson.toJson(container))
			}
			true
		} catch (e: Exception) {
			Log.e("PasswordRepository", "Error initializing empty file", e)
			false
		}
	}*/

}
