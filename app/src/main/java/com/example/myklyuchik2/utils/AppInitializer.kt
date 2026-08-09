// File: AppInitializer.kt
package com.example.myklyuchik2.utils

import android.content.Context
import java.io.File

object AppInitializer {

	private val installMarkerFileName = "1st_install_marker"

	fun isFirstUse(context: Context): Boolean {
		val markerFile = File(context.noBackupFilesDir, installMarkerFileName)
		return !markerFile.exists()
	}

	fun markAppInitialized(context: Context) {
		val markerFile = File(context.noBackupFilesDir, installMarkerFileName)
		if (!markerFile.exists()) {
			markerFile.parentFile?.mkdirs()
			markerFile.createNewFile()
		}
	}

	fun clearInstallMarker(context: Context) {
		val markerFile = File(context.noBackupFilesDir, installMarkerFileName)
		if (markerFile.exists()) {
			markerFile.delete()
		}
	}

	fun isDataValid(context: Context): Boolean {
		val dataFile = File(context.filesDir, "passwords.enc")
		return dataFile.exists() //&& canDecrypt(dataFile) - this must be checked elsewhere, where biometric's been initialized and key can be retrieved
	}
}
