package com.example.myklyuchik2.ui.settings

import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.navigation.NavController
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.input.key.Key
import android.view.KeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myklyuchik2.ui.main.model.EntryMode
import com.example.myklyuchik2.ui.theme.MyKlyuchikTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
	navController: NavController,
	onNavigateBack: () -> Unit = { navController.popBackStack() },
	//onNavigateBack = { navController.popBackStack()},
		// Alternatively, you could use:
		// navController.navigateUp()
	onImportCsv: () -> Unit,
	onExportCsv: () -> Unit,
	onChangePassword: () -> Unit,
	onCloudClick: () -> Unit // пока заглушка
) {
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
					onClick = onImportCsv
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
					enabled = false, // заглушка
					trailing = { Badge { Text("Coming Soon") } }
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