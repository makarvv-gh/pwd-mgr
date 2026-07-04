package com.example.myklyuchik2.data.repository

import android.content.Context
import com.example.myklyuchik2.data.storage.SecurePasswordStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PasswordRepository private constructor(context: Context) {
	companion object {
		@Volatile
		private var instance: PasswordRepository? = null

		fun getInstance(context: Context): PasswordRepository {
			return instance ?: synchronized(this) {
				PasswordRepository(context).also { instance = it }
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

			// Round-trip verification
			val verifyPassword = getCurrentPassword()
			verifyPassword != null && constantTimeCompare(verifyPassword, newPassword)
		} catch (e: Exception) {
			e.printStackTrace()
			false
		}
	}

	private fun constantTimeCompare(a: String, b: String): Boolean {
		var result = 0
		for (i in 0 until a.length.coerceAtMost(b.length)) {
			result = result or (a[i].code xor b[i].code)
		}
		return result == 0 && a.length == b.length
	}
}
