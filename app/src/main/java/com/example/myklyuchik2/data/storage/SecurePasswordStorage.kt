package com.example.myklyuchik2.data.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
//import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import android.util.Base64

class SecurePasswordStorage(context: Context) {
	private val context = context.applicationContext
	private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
	private val keyGenerator: KeyGenerator = KeyGenerator.getInstance(
		KeyProperties.KEY_ALGORITHM_AES,
		"AndroidKeyStore"
	)

	private val KEY_ALIAS = "master_password_key"

	init {
		if (!keyStore.containsAlias(KEY_ALIAS)) {
			createKey()
		}
	}

	private fun createKey() {
		val keySpec = KeyGenParameterSpec.Builder(
			KEY_ALIAS,
			KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
		)
			.setBlockModes(KeyProperties.BLOCK_MODE_CBC)
			.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
			.setUserAuthenticationRequired(true)
			.build()

		keyGenerator.init(keySpec)
		keyGenerator.generateKey()
	}

	fun encrypt(password: String): String {
		val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
		cipher.init(Cipher.ENCRYPT_MODE, keyStore.getKey(KEY_ALIAS, null) as SecretKey)

		val iv = cipher.parameters.getParameterSpec(IvParameterSpec::class.java)
		val encrypted = cipher.doFinal(password.toByteArray())

		return Base64.encodeToString(iv.iv + encrypted, Base64.DEFAULT)
	}

	fun decrypt(encryptedData: String): String? {
		try {
			val data = Base64.decode(encryptedData, Base64.DEFAULT)
			val iv = IvParameterSpec(data, 0, 16)
			val encrypted = data.copyOfRange(16, data.size)

			val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
			cipher.init(Cipher.DECRYPT_MODE, keyStore.getKey(KEY_ALIAS, null) as SecretKey, iv)

			val decrypted = cipher.doFinal(encrypted)
			return String(decrypted)
		} catch (e: Exception) {
			// Log error and return null if decryption fails
			e.printStackTrace()
			return null
		}
	}
/* too early for that, last but one in tech debt
	companion object {
		const val BIOMETRIC_REQUEST_CODE = 101
	}*/
}
