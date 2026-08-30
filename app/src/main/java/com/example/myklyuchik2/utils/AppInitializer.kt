// File: AppInitializer.kt
package com.example.myklyuchik2.utils

import android.content.Context
import java.io.File
import com.example.myklyuchik2.data.storage.SecureStorage
import com.example.myklyuchik2.data.storage.DataState


object AppInitializer {

	private val installMarkerFileName = "1st_install_marker"
	private const val dataFileName = "passwords.enc"

	/**
	 * Determines the current data state by checking the existence of both data and marker files
	 * @param context Android context
	 * @return DataState representing the current state of data files
	 */
	fun determineDataState(context: Context): DataState {
		val markerFile = File(context.noBackupFilesDir, installMarkerFileName)
		val dataFile = File(context.filesDir, dataFileName)

		return when {
			// Spurious data file: data file exists but marker is missing
			dataFile.exists() && !markerFile.exists() -> {
				// Delete the spurious data file
				SecureStorage.deleteDataFile(dataFile.absolutePath)
				DataState.SpuriousData
			}
			// First-time use: neither file exists
			!dataFile.exists() && !markerFile.exists() -> {
				DataState.FirstTimeUse
			}
			// Normal use: both files exist
			else -> {
				// Verify data file validity
				if (SecureStorage.hasValidData(dataFile.absolutePath)) {
					DataState.NormalUse
				} else {
					// Data file is invalid but marker exists
					// Delete both files and return FirstTimeUse
					if (markerFile.exists()) {
						markerFile.delete()
					}
					if (dataFile.exists()) {
						SecureStorage.deleteDataFile(dataFile.absolutePath)
					}
					DataState.FirstTimeUse
				}
			}
		}
	}

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
		return when(determineDataState(context)) {
			DataState.NormalUse -> true
			else -> false
		}
	}
}
