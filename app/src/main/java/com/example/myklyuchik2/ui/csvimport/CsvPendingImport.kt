package com.example.myklyuchik2.ui.csvimport

import android.net.Uri

/**
 * Process-wide holder for the pending CSV-import callback.
 * ... (см. комментарии в файле о причине обхода ActivityResultRegistry)
 */
internal object CsvPendingImport {
	/** Invoked from MainActivity.onActivityResult() with the picked Uri, or null. */
	var callback: ((Uri?) -> Unit)? = null
}