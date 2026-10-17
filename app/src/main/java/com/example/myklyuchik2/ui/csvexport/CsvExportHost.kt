package com.example.myklyuchik2.ui.csvexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myklyuchik2.data.model.PasswordEntry
import com.example.myklyuchik2.utils.DateFormatter
import com.example.myklyuchik2.ui.main.MainViewModel
import com.example.myklyuchik2.ui.main.model.UiEvent
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class CsvExportHost(private val mainViewModel: MainViewModel) : ViewModel() {

    companion object {
        private const val TAG = "CsvExportHost"
        private const val CSV_HEADER = "resource_name,login,password,change_date,url,email,auth_code,notes,tags"

        fun factory(mainViewModel: MainViewModel): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CsvExportHost(mainViewModel) as T
                }
            }
    }

    fun exportCsv(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val entries = mainViewModel.uiState.value.filteredEntries
                if (entries.isEmpty()) {
                    notifyError("Нет записей для экспорта")
                    return@launch
                }

                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream == null) {
                    notifyError("Не удалось открыть файл для записи")
                    return@launch
                }

                val writer = OutputStreamWriter(outputStream, Charset.forName("windows-1251"))
                writer.use { w ->
                    w.write(CSV_HEADER + "\r\n")

                    for (entry in entries) {
                        val tags = if (entry.tags.isNotEmpty()) {
                            "\"${entry.tags.joinToString(",")}\""
                        } else {
                            ""
                        }

                        val line = listOf(
                            entry.resourceName,
                            entry.login,
                            entry.password,
                            entry.changeDate,
                            entry.url,
                            entry.email,
                            entry.authCode,
                            entry.notes,
                            tags
                        ).joinToString(",")

                        w.write("$line\r\n")
                    }
                }

                notifySuccess("Успешно экспортировано записей: ${entries.size}")
            } catch (e: Exception) {
                Log.e(TAG, "ERROR exporting CSV", e)
                notifyError("Ошибка экспорта: ${e.message}")
            }
        }
    }

    private suspend fun notifyError(message: String) {
        mainViewModel.notifyCsvImportError(message)
    }

    private suspend fun notifySuccess(message: String) {
        mainViewModel.notifyCsvImportSuccess(message)
    }
}
