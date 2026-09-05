package com.example.myklyuchik2.ui.settings

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myklyuchik2.ui.csvimport.CsvImportActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
	navController: NavController,
	onNavigateBack: () -> Unit = { navController.popBackStack() },
	//onNavigateBack = { navController.popBackStack()},
		// Alternatively, you could use:
		// navController.navigateUp()
	onImportCsv: (String) -> Unit,  // Updated to take a file path parameter,
	onExportCsv: () -> Unit,
	onChangePassword: () -> Unit = { navController.navigate("change-password") },
	onCloudClick: () -> Unit // пока заглушка
) {
	val context = LocalContext.current
	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Настройки") },
				navigationIcon = {
					IconButton(onClick = onNavigateBack) {
						Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
					}
				}
			)
		}

	) { padding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.padding(horizontal = 16.dp)
		) {
			item { SectionTitle("Данные") }
			item {
				SettingsListItem(
					title = "Импорт из CSV",
					icon = Icons.Default.Upload,
					onClick = {
						// Start CSV import activity
						val intent = Intent(context, CsvImportActivity::class.java)
						(context as? ComponentActivity)?.startActivityForResult(intent, 1001)
					}
				)
			}
			item {
				SettingsListItem(
					title = "Экспорт в CSV",
					icon = Icons.Default.Download,
					onClick = onExportCsv,
					enabled = false, // заглушка
					trailing = { Badge { Text("Coming Soon") } }
				)
			}

			item { SectionTitle("Безопасность") }
			item {
				SettingsListItem(
					title = "Смена мастер-пароля",
					icon = Icons.Default.Lock,
					onClick = onChangePassword,
					enabled = true, // Now enabled
					trailing = null
				)
			}

			item { SectionTitle("Синхронизация") }
			item {
				SettingsListItem(
					title = "Облачный диск",
					icon = Icons.Default.Cloud,
					onClick = onCloudClick,
					enabled = false, // заглушка
					trailing = { Badge { Text("Coming Soon") } }
				)
			}
		}
	}
}

@Composable
private fun SettingsListItem(
	title: String,
	icon: ImageVector,
	onClick: () -> Unit,
	enabled: Boolean = true,
	trailing: @Composable (() -> Unit)? = null
) {
	ListItem(
		headlineContent = { Text(title) },
		leadingContent = { Icon(icon, contentDescription = null) },
		trailingContent = trailing ?: { Icon(Icons.Default.ChevronRight, null) },
		modifier = Modifier
			.clickable(enabled = enabled, onClick = onClick)
			.then(if (!enabled) Modifier.alpha(0.5f) else Modifier)
	)
}

@Composable
private fun SectionTitle(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.titleSmall,
		color = MaterialTheme.colorScheme.primary,
		modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)
	)
}