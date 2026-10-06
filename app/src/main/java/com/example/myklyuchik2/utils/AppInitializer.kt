// File: AppInitializer.kt
package com.example.myklyuchik2.utils

import android.content.Context
import android.util.Log
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
				Log.d("AppInitializer", "Spurious data file: data file exists but marker is missing")
				SecureStorage.deleteDataFile(dataFile.absolutePath)
				DataState.SpuriousData
			}
			// First-time use: neither file exists
			!dataFile.exists() && !markerFile.exists() -> {
				Log.d("AppInitializer", "First-time use: neither file exists")
				DataState.FirstTimeUse
			}
			// No data yet: marker exists but the data file hasn't been created
			// (master password was set, but no entries were entered or imported)
			!dataFile.exists() && markerFile.exists() -> {
				Log.d("AppInitializer", "No data yet: marker exists, data file missing")
				DataState.NoDataYet
			}
			// Normal use: both files exist
			else -> {
				Log.d("AppInitializer", "Normal use: both files exist")
				// Verify data file validity
				if (SecureStorage.hasValidData(dataFile.absolutePath)) {
					DataState.NormalUse
				} else {
					// Data file is invalid but marker exists
					// Delete both files and return FirstTimeUse
					Log.d("AppInitializer", "Normal use: Data file is invalid but marker exists")
					if (markerFile.exists()) {
						markerFile.delete()
					}
					if (dataFile.exists()) {
						SecureStorage.deleteDataFile(dataFile.absolutePath)
					}
					Log.d("AppInitializer", "Delete both files and return FirstTimeUse")
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
