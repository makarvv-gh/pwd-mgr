package com.example.myklyuchik2.data.encryption

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.text.Charsets
import android.util.Log

object CryptoService {

	private const val ITERATIONS = 100_000
	private const val KEY_LENGTH_BYTES = 32  // 16 для AES + 16 для HMAC
	private const val SALT_SIZE_BYTES = 16
	private const val IV_SIZE_BYTES = 16
	private const val HMAC_SIZE_BYTES = 32
	private const val FERNET_VERSION: Byte = 0x80.toByte()

	// TTL: 0 = отключить проверку времени (для хранимых паролей)
	// Если нужна проверка — установите, например, 365 * 24 * 3600 (1 год в секундах)
	private const val TTL_SECONDS: Long = 0L

	data class EncryptedContainer(
		val salt: String,      // Base64 URL-safe, no-wrap
		val encryptedData: String  // Fernet token, Base64 URL-safe
	)

	fun generateSalt(): ByteArray {
		val salt = ByteArray(SALT_SIZE_BYTES)
		SecureRandom().nextBytes(salt)
		return salt
	}

	private fun deriveKeyBytes(password: String, salt: ByteArray): ByteArray {
		val spec = PBEKeySpec(
			password.toCharArray(),
			salt,
			ITERATIONS,
			KEY_LENGTH_BYTES * 8
		)
		// Add a fixed salt for HMAC key derivation to ensure consistency - 26-08-22
		val hmacSpec = PBEKeySpec(
			password.toCharArray(),
			"HMAC_KEY_SALT_0123456789".toByteArray(), // Fixed salt for HMAC key
			ITERATIONS,
			256 // 256 bits = 32 bytes for HMAC key
		)

		val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")

		//26-08-22
		//return factory.generateSecret(spec).encoded
		// Derive main key
		val mainKey = factory.generateSecret(spec).encoded

		// Derive HMAC key separately
		val hmacKey = factory.generateSecret(hmacSpec).encoded

		// Combine them using a deterministic method
		val combinedKey = ByteArray(KEY_LENGTH_BYTES)
		for (i in mainKey.indices) {
			combinedKey[i] = (mainKey[i].toInt() and 0xFF).xor(hmacKey[i % hmacKey.size].toInt() and 0xFF).toByte()
		}
		return combinedKey
	}

	fun  encryptWithSalt(data: String, password: String, salt: ByteArray): EncryptedContainer {
		//val salt = generateSalt() - use the same salt
		val fullKey = deriveKeyBytes(password, salt)

		// Split key: first 16 bytes for encryption, last 16 for signing
		val encryptKey = fullKey.copyOfRange(0, 16)
		val signKey = fullKey.copyOfRange(16, 32)

		// Generate random IV
		val iv = ByteArray(IV_SIZE_BYTES)
		SecureRandom().nextBytes(iv)

		// Encrypt with AES-128-CBC
		/*val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
		cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encryptKey, "AES"), IvParameterSpec(iv))
		val cipherText = cipher.doFinal(data.toByteArray(Charsets.UTF_8))*/

		// new: Encrypt with AES-CBC
		val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
		cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encryptKey, "AES"), IvParameterSpec(iv))
		val cipherText = cipher.doFinal(data.toByteArray(Charsets.UTF_8))


		// Build token payload: version(1) + timestamp(8) + IV(16) + ciphertext
		val timestamp = System.currentTimeMillis() / 1000L
		val payload = ByteArray(1 + 8 + IV_SIZE_BYTES + cipherText.size)
		payload[0] = FERNET_VERSION
		// Write timestamp as big-endian long
		for (i in 0 until 8) {
			payload[1 + i] = ((timestamp shr (56 - i * 8)) and 0xFF).toByte()
		}
		System.arraycopy(iv, 0, payload, 9, IV_SIZE_BYTES)
		System.arraycopy(cipherText, 0, payload, 9 + IV_SIZE_BYTES, cipherText.size)

		// Compute HMAC-SHA256 over payload
		val mac = Mac.getInstance("HmacSHA256")
		mac.init(SecretKeySpec(signKey, "HmacSHA256"))
		val hmac = mac.doFinal(payload)

		// Final token: payload + hmac
		val token = payload + hmac

		return EncryptedContainer(
			salt = Base64.encodeToString(salt, Base64.URL_SAFE or Base64.NO_WRAP),
			encryptedData = Base64.encodeToString(token, Base64.URL_SAFE or Base64.NO_WRAP)
		)
	}

	fun decrypt(container: EncryptedContainer, password: String): String {
		val salt = Base64.decode(container.salt, Base64.URL_SAFE or Base64.NO_WRAP)
		val token = Base64.decode(container.encryptedData, Base64.URL_SAFE or Base64.NO_WRAP)

		// Parse token: version(1) + timestamp(8) + IV(16) + ciphertext + hmac(32)
		if (token.size < 1 + 8 + IV_SIZE_BYTES + HMAC_SIZE_BYTES) {
			throw IllegalArgumentException("Invalid token: too short")
		}
		if (token[0] != FERNET_VERSION) {
			throw IllegalArgumentException("Unsupported Fernet version: ${token[0]}")
		}

		// Extract timestamp and check TTL
		var timestamp = 0L
		for (i in 0 until 8) {
			timestamp = timestamp or ((token[1 + i].toLong() and 0xFF) shl (56 - i * 8))
		}
		if (TTL_SECONDS > 0) {
			val now = System.currentTimeMillis() / 1000L
			if (timestamp > now + 60 || timestamp < now - TTL_SECONDS) {
				throw IllegalArgumentException("Token timestamp out of valid range")
			}
		}

		// Extract IV and ciphertext
		val iv = token.copyOfRange(9, 9 + IV_SIZE_BYTES)
		Log.d("CryptoService", "Extracted IV: ${iv.joinToString(":") { "%02x".format(it) }}")
		val cipherTextEnd = token.size - HMAC_SIZE_BYTES
		val cipherText = token.copyOfRange(9 + IV_SIZE_BYTES, cipherTextEnd)
		val receivedHmac = token.copyOfRange(cipherTextEnd, token.size)

		// Derive keys
		val fullKey = deriveKeyBytes(password, salt)
		val encryptKey = fullKey.copyOfRange(0, 16)
		val signKey = fullKey.copyOfRange(16, 32)

		// Verify HMAC over: version + timestamp + IV + ciphertext
		val payloadForHmac = token.copyOfRange(0, cipherTextEnd)
		val mac = Mac.getInstance("HmacSHA256")
		mac.init(SecretKeySpec(signKey, "HmacSHA256"))
		val computedHmac = mac.doFinal(payloadForHmac)

		// Constant-time comparison to prevent timing attacks
		/*if (!MessageDigest.isEqual(receivedHmac, computedHmac)) {
			throw SecurityException("HMAC verification failed: wrong password or corrupted data")
		}*/
		// replace MessageDigest.isEqual with our own implementation
		if (!verifyHmac(receivedHmac, computedHmac)) {
			throw SecurityException("HMAC verification failed: wrong password or corrupted data")
		}
		// Decrypt
		val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
		cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encryptKey, "AES"), IvParameterSpec(iv))
		val plainBytes = cipher.doFinal(cipherText)

		return String(plainBytes, Charsets.UTF_8)
	}
	// Add this helper method to ensure consistent HMAC computation
	fun verifyHmac(receivedHmac: ByteArray, computedHmac: ByteArray): Boolean {
		if (receivedHmac.size != computedHmac.size) return false
		var result = 0
		for (i in receivedHmac.indices) {
			// Use our xor extension function for constant-time comparison
			result = result or (receivedHmac[i].xor(computedHmac[i]))
		}
		return result == 0
	}
	private fun Byte.xor(other: Byte): Int {
		return (this.toInt() and 0xFF) xor (other.toInt() and 0xFF)
	}
}