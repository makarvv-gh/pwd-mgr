package com.example.myklyuchik2.data.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyInfo
import android.util.Base64
import android.util.Log
//import androidx.security.crypto.EncryptedSharedPreferences
//import androidx.security.crypto.MasterKeys
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.core.content.edit
import kotlinx.coroutines.*
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import android.os.CancellationSignal

class SecurePasswordStorage private constructor(context: Context) {
	companion object {
		private const val KEY_ALIAS = "master_password_key"
		private const val LEGACY_MASTER_PASSWORD = "master123"
		private const val ENCRYPTED_PREFS_NAME = "secure_password_prefs"
		private const val SHARED_PREFS_NAME = "secure_password_prefs"
		private const val PASSWORD_KEY = "encrypted_password"
		private const val IV_KEY = "password_iv"

		@Volatile
		private var instance: SecurePasswordStorage? = null

		fun getInstance(context: Context): SecurePasswordStorage {
			return instance ?: synchronized(this) {
				SecurePasswordStorage(context).also { instance = it }
			}
		}
	}

	private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
	private val sharedPreferences: SharedPreferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)

	init {
		// Initialize Keystore key
		if (!keyStore.containsAlias(KEY_ALIAS)) {
			generateKey(context)
		}

		// Initialize encrypted shared preferences
		/*val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
		encryptedSharedPreferences = EncryptedSharedPreferences.create(
			ENCRYPTED_PREFS_NAME,
			masterKey,
			context,
			EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
			EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_SIV
		)*/

		// First-use initialization
		if (!isPasswordSet()) {
			Log.d("SecurePasswordStorage", "First-use initialization: seeding Keystore with legacy master password")
			CoroutineScope(Dispatchers.IO).launch {
				encryptAndStorePassword(LEGACY_MASTER_PASSWORD)
			}
			// TODO: Remove after first-login flow is implemented
		}
	}

	private fun generateKey(context: Context) {
		val biometricManager = BiometricManager.from(context)
		when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
			BiometricManager.BIOMETRIC_SUCCESS -> {
				// Biometric is available and enrolled
				val keyGenSpec = KeyGenParameterSpec.Builder(
					KEY_ALIAS,
					KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
				)
					.setKeySize(256)
					.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
					.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
					.setKeyValidityStart(null) // Remove validity start
					.setKeyValidityEnd(null)   // Remove validity end
					.setUserAuthenticationRequired(true)
					.build()

				val keyGenerator = javax.crypto.KeyGenerator.getInstance(
					KeyProperties.KEY_ALGORITHM_AES,
					"AndroidKeyStore"
				)

				keyGenerator.init(keyGenSpec)
				keyGenerator.generateKey()

				// Log whether the key is in secure hardware
				val keyInfo = getKeyInfo()
				Log.d("SecurePasswordStorage", "Key is inside secure hardware: ${keyInfo?.isInsideSecureHardware}")
			}
			BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
				// No biometric is enrolled, handle this case (e.g., show a message to the user)
				Log.e("SecurePasswordStorage", "No biometric enrolled")
				// Optionally, you can fallback to a less secure method or prompt the user to enroll
			}
			else -> {
				// Biometric is not available or another error occurred
				Log.e("SecurePasswordStorage", "Biometric not available")
			}
		}
	}

	private fun getKeyInfo(): KeyInfo? {
		return run {
			val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry ?: run {
				Log.d("SecurePasswordStorage", "KeyStore entry is null or not a SecretKeyEntry")
				return@run null
			}

			val key = keyStore.getKey(KEY_ALIAS, null) ?: run {
				Log.d("SecurePasswordStorage", "Key is null")
				return@run null
			}

			key as? KeyInfo
		}
	}

	suspend fun encryptAndStorePassword(password: String): Boolean = withContext(Dispatchers.IO) {
		try {
			val cipher = Cipher.getInstance("AES/GCM/NoPadding")
			//val key = keyStore.getKey(KEY_ALIAS, null) as SecretKey
			val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
				?: return@withContext false
			cipher.init(Cipher.ENCRYPT_MODE, key)

			val iv = cipher.getIV()
			val encryptedBytes = cipher.doFinal(password.toByteArray())

			// Store IV and encrypted password
			sharedPreferences.edit {
				putString(IV_KEY, Base64.encodeToString(iv, Base64.DEFAULT))
				putString(PASSWORD_KEY, Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
			}

			true
		} catch (e: Exception) {
			e.printStackTrace()
			false
		}
	}

	suspend fun decryptPassword(): String? = withContext(Dispatchers.IO) {
		try {
			val encryptedPassword = sharedPreferences.getString(PASSWORD_KEY, null)
			val iv = sharedPreferences.getString(IV_KEY, null)

			if (encryptedPassword == null || iv == null) {
				return@withContext null
			}

			val cipher = Cipher.getInstance("AES/GCM/NoPadding")
			//val key = keyStore.getKey(KEY_ALIAS, null) as SecretKey
			val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: return@withContext null
			val ivSpec = GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT))

			cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
			val decryptedBytes = cipher.doFinal(Base64.decode(encryptedPassword, Base64.DEFAULT))

			String(decryptedBytes)
		} catch (e: Exception) {
			when (e) {
				is android.security.keystore.KeyPermanentlyInvalidatedException -> {
					// This happens when the user changes their lock screen credentials
					// We need to prompt them to reset their password
					null
				}
				is android.security.keystore.UserNotAuthenticatedException -> {
					// Biometric not authenticated yet, we'll handle this in the next step
					null
				}
				else -> {
					e.printStackTrace()
					null
				}
			}
		}
	}

	fun isPasswordSet(): Boolean {
		return sharedPreferences.contains(PASSWORD_KEY)
	}
}
