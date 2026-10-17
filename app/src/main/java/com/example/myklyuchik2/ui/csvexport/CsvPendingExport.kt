package com.example.myklyuchik2.ui.csvexport

import android.net.Uri

internal object CsvPendingExport {
	/** Invoked from MainActivity.onActivityResult() with the picked Uri, or null. */
	var callback: ((Uri?) -> Unit)? = null
}