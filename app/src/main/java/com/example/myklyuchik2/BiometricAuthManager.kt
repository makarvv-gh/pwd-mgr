package com.example.myklyuchik2

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.core.hardware.fingerprint.FingerprintManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BiometricAuthManager(private val context: Context) {
	private val executor = ContextCompat.getMainExecutor(context)

	// Check if biometric authentication is available and setup
	fun canAuthenticate(): Int {
		val biometricManager = BiometricManager.from(context)
		return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
	}

	// Show biometric prompt for authentication
	fun authenticate(
		fragment: Fragment,
		title: String = "Биометрическая аутентификация",
		subtitle: String = "Используйте отпечаток пальца для аутентификации"
	): Pair<BiometricPrompt, BiometricPrompt.PromptInfo> {
		val executor = ContextCompat.getMainExecutor(context)
		val biometricPrompt = BiometricPrompt(
			fragment,
			executor,
			object : BiometricPrompt.AuthenticationCallback() {
				override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
					// Handle successful biometric authentication
				}

				override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
					// Handle authentication error
				}

				override fun onAuthenticationFailed() {
					// Handle authentication failure
				}
			}
		)

		val promptInfo = BiometricPrompt.PromptInfo.Builder()
			.setTitle(title)
			.setSubtitle(subtitle)
			.setNegativeButtonText("Отмена")
			.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
			.build()

		return Pair(biometricPrompt, promptInfo)
	}
}
