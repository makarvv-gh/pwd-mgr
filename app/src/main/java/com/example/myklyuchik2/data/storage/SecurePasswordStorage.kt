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
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.GCMParameterSpec
import androidx.core.content.edit
import kotlinx.coroutines.*
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import android.os.CancellationSignal

private const val KEY_ALIAS = "master_password_key"
private const val SHARED_PREFS_NAME = "secure_password_prefs"
private const val PASSWORD_KEY = "encrypted_password"
private const val IV_KEY = "password_iv"
class SecurePasswordStorage private constructor(context: Context) {
	private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
	private val sharedPreferences: SharedPreferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)

	companion object {


		@Volatile
		private var instance: SecurePasswordStorage? = null

		fun getInstance(context: Context): SecurePasswordStorage {
			return instance ?: synchronized(this) {
				SecurePasswordStorage(context).also { instance = it }
			}
		}
	}


	init {
		// Initialize Keystore key
		try {
			if (!keyStore.containsAlias(KEY_ALIAS)) {
				generateKey(context)
			}
		} catch (e: Exception) {
			Log.e("SecurePasswordStorage", "Error checking keystore alias", e)
		}
	}
	// Move initializeWithPassword outside of init block
	// TODO: Remove after first-login flow is implemented
	fun initializeWithPassword(password: String): Boolean {
		return encryptAndStorePassword(password)
	}
	//}

	private fun generateKey(context: Context) {
		val biometricManager = BiometricManager.from(context)
		when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
			BiometricManager.BIOMETRIC_SUCCESS -> {
				// Biometric is available and enrolled
				Log.e("SecurePasswordStorage", "Biometric is available and enrolled")
				val keyGenSpec = KeyGenParameterSpec.Builder(
					KEY_ALIAS,
					KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
				)
					.setKeySize(256)
					.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
					.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
					.setKeyValidityStart(null) // Remove validity start
					.setKeyValidityEnd(null)   // Remove validity end
					.setUserAuthenticationRequired(false)
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
				Log.e("SecurePasswordStorage", "Biometric not available or smth")
			}
		}
	}
//TODO: REMOVE IN PRODUCTION
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
			val keyInfo = key as? KeyInfo
			Log.d("SecurePasswordStorage", "Key is inside secure hardware: ${keyInfo?.isInsideSecureHardware}")
			Log.d("SecurePasswordStorage", "Key is user-authentication required: ${keyInfo?.isUserAuthenticationRequired}")

			key as? KeyInfo
		}
	}
	//TODO: REMOVE IN PRODUCTION
	private fun logKeyInfo(key: SecretKey?) {
		if (key == null) {
			Log.e("SecurePasswordStorage", "Key is null")
			return
		}

		Log.d("SecurePasswordStorage", "Key algorithm: ${key.algorithm}")
		Log.d("SecurePasswordStorage", "Key format: ${key.format}")
		Log.d("SecurePasswordStorage", "Key encoded length: ${key.encoded.size}")
	}
	//suspend fun encryptAndStorePassword(password: String): Boolean = withContext(Dispatchers.IO) {
	fun encryptAndStorePassword(password: String): Boolean {
		try {
			val cipher = Cipher.getInstance("AES/GCM/NoPadding")
			val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
			//logKeyInfo(key)
			Log.d("SecurePasswordStorage", "Generated key class: ${key?.javaClass?.name}")
			/*val keyBytes = "0123456789abcdef".toByteArray() // 16 bytes = AES-128
			val key = SecretKeySpec(keyBytes, "AES")*/
			Log.d("SecurePasswordStorage", "encryptAndStorePassword called with password: $password")
			if (key == null) {
				Log.e("SecurePasswordStorage", "Failed to retrieve secret key")
				return false
			}

			cipher.init(Cipher.ENCRYPT_MODE, key)
			val iv = cipher.getIV()
			Log.d("SecurePasswordStorage", "Encryption IV: ${iv.joinToString { "%02x".format(it) }}")
			val encryptedBytes = cipher.doFinal(password.toByteArray())

			sharedPreferences.edit {
				putString(IV_KEY, Base64.encodeToString(iv, Base64.DEFAULT))
				putString(PASSWORD_KEY, Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
			}

			Log.d("SecurePasswordStorage", "encryptAndStorePassword - successful completion")
			return true
		} catch (e: Exception) {
			Log.e("SecurePasswordStorage", "Exception during encryptAndStorePassword", e)
			return false
		}
	}
	//suspend fun decryptPassword(): String? = withContext(Dispatchers.IO) {
	 fun decryptPassword(): String? = runBlocking {
		withContext(Dispatchers.IO) {
			try {
				val encryptedPassword = sharedPreferences.getString(PASSWORD_KEY, null)
				val iv = sharedPreferences.getString(IV_KEY, null)

				if (encryptedPassword == null || iv == null) {
					return@withContext null
				}

				val cipher = Cipher.getInstance("AES/GCM/NoPadding")
				/*val TEST_KEY_BYTES = "0123456789abcdef".toByteArray() // 16 bytes = AES-128
			val key = SecretKeySpec(TEST_KEY_BYTES, "AES")*/
				val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: return@withContext null
				//logKeyInfo(key)
				Log.d("SecurePasswordStorage", "Generated key class: ${key?.javaClass?.name}")
				//match IV string to one created during encryption
				val ivBytes = Base64.decode(iv, Base64.DEFAULT)
				Log.d(
					"SecurePasswordStorage",
					"Decryption IV: ${ivBytes.joinToString { "%02x".format(it) }}"
				)

				val ivSpec = GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT))

				cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)

				//val testEncrypt = cipher.doFinal("test".toByteArray())
				//Log.d("SecurePasswordStorage", "Test encryption succeeded")
				val decryptedBytes =
					cipher.doFinal(Base64.decode(encryptedPassword, Base64.DEFAULT))

				String(decryptedBytes)
			} catch (e: Exception) {
				when (e) {
					is android.security.keystore.KeyPermanentlyInvalidatedException -> {
						// This happens when the user changes their lock screen credentials
						// We need to prompt them to reset their password
						Log.d(
							"SecurePasswordStorage",
							"Key Permanently Invalidated Exception: lock screen credentials changed at runtime"
						)
						null
					}

					is android.security.keystore.UserNotAuthenticatedException -> {
						// Biometric not authenticated yet, we'll handle this in the next step
						Log.d(
							"SecurePasswordStorage",
							"Biometric not authenticated yet exception occurred"
						)
						null
					}

					else -> {
						e.printStackTrace()
						null
					}
				}
			}
		}	}

	fun isPasswordSet(): Boolean {
		return sharedPreferences.contains(PASSWORD_KEY)
	}
}
