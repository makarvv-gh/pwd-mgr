package com.example.myklyuchik2.ui.main

import android.content.Context
import android.content.res.AssetManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myklyuchik2.data.importexport.CsvImporter
import com.example.myklyuchik2.data.model.PasswordEntry
import com.example.myklyuchik2.data.storage.JsonStorage
import com.example.myklyuchik2.data.storage.SecureStorage
import com.example.myklyuchik2.ui.main.model.UiEvent
import com.example.myklyuchik2.ui.main.model.UiState
import com.example.myklyuchik2.ui.theme.MyKlyuchikTheme
import com.example.myklyuchik2.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

// ==================== ViewModel ====================
class MainViewModel(
	private val context: Context,
	private val assetManager: AssetManager
) : ViewModel() {

	private val _uiState = MutableStateFlow(UiState())
	val uiState: StateFlow<UiState> = _uiState.asStateFlow()

	private val _events = kotlinx.coroutines.channels.Channel<UiEvent>()
	val events = _events.receiveAsFlow()

	private val dataPath: String by lazy {
		File(context.filesDir, "passwords.enc").absolutePath
	}

	private val tempJsonPath: String by lazy {
		File(context.filesDir, "temp.json").absolutePath
	}

	init {
		loadEntries()
	}

	private fun loadEntries() {
		viewModelScope.launch {
			_uiState.update { it.copy(isLoading = true) }
			try {
				val entries = SecureStorage.loadEncrypted(dataPath, Constants.MASTER_PASSWORD)
				_uiState.update {
					it.copy(
						isLoading = false,
						allEntries = entries,
						filteredEntries = applyFilters(entries, it.filters)
					)
				}
			} catch (e: Exception) {
				_uiState.update { it.copy(isLoading = false, error = e.message) }
				_events.send(UiEvent.ShowError("Ошибка загрузки: ${e.message}"))
			}
		}
	}

	fun importFromCsv(fileName: String) {
		viewModelScope.launch {
			_uiState.update { it.copy(isLoading = true) }
			try {
				val entries = CsvImporter.importFromAssets(assetManager, fileName)
				JsonStorage.saveToJsonFile(entries, tempJsonPath)
				SecureStorage.saveEncrypted(entries, Constants.MASTER_PASSWORD, dataPath)
				loadEntries()
				_events.send(UiEvent.ShowSuccess("Импортировано ${entries.size} записей"))
			} catch (e: Exception) {
				_uiState.update { it.copy(isLoading = false, error = e.message) }
				_events.send(UiEvent.ShowError("Ошибка импорта: ${e.message}"))
			}
		}
	}

	fun addEntry(entry: PasswordEntry) {
		viewModelScope.launch {
			val current = _uiState.value.allEntries.toMutableList()
			current.add(0, entry)
			saveAndReload(current)
			_events.send(UiEvent.NavigateBack)
		}
	}

	fun updateEntry(updated: PasswordEntry) {
		viewModelScope.launch {
			val current = _uiState.value.allEntries.toMutableList()
			val idx = current.indexOfFirst { it.id == updated.id }
			if (idx >= 0) {
				current[idx] = updated.copy(updatedAt = Date().toString())
				saveAndReload(current)
			}
			_events.send(UiEvent.NavigateBack)
		}
	}

	fun deleteEntry(entry: PasswordEntry) {
		viewModelScope.launch {
			val current = _uiState.value.allEntries.toMutableList()
			val removed = current.removeIf { it.id == entry.id }
			if (removed) saveAndReload(current)
		}
	}

	private suspend fun saveAndReload(entries: List<PasswordEntry>) {
		SecureStorage.saveEncrypted(entries, Constants.MASTER_PASSWORD, dataPath)
		_uiState.update {
			it.copy(
				allEntries = entries,
				filteredEntries = applyFilters(entries, it.filters)
			)
		}
	}

	fun updateSearchQuery(query: String) {
		_uiState.update { state ->
			val newFilters = state.filters.copy(searchQuery = query)
			state.copy(
				filters = newFilters,
				filteredEntries = applyFilters(state.allEntries, newFilters)
			)
		}
	}

	fun updateTagFilter(tags: List<String>) {
		_uiState.update { state ->
			val newFilters = state.filters.copy(tagFilters = tags)
			state.copy(
				filters = newFilters,
				filteredEntries = applyFilters(state.allEntries, newFilters)
			)
		}
	}

	fun removeTagFilter(tag: String) {
		_uiState.update { state ->
			val newTags = state.filters.tagFilters - tag
			val newFilters = state.filters.copy(tagFilters = newTags)
			state.copy(
				filters = newFilters,
				filteredEntries = applyFilters(state.allEntries, newFilters)
			)
		}
	}

	fun clearFilters() {
		_uiState.update { state ->
			val newFilters = UiState.Filters()
			state.copy(
				filters = newFilters,
				filteredEntries = applyFilters(state.allEntries, newFilters)
			)
		}
	}

	fun toggleFilterSheet(show: Boolean) {
		_uiState.update { it.copy(showFilterSheet = show) }
	}

	fun toggleSettingsDialog(show: Boolean) {
		_uiState.update { it.copy(showSettingsDialog = show) }
	}

	fun setEntryToEdit(entry: PasswordEntry?) {
		_uiState.update { it.copy(entryToEdit = entry) }
	}

	private fun applyFilters(entries: List<PasswordEntry>, filters: UiState.Filters): List<PasswordEntry> {
		return entries.filter { entry ->
			val matchesSearch = filters.searchQuery.isBlank() ||
					entry.resourceName.contains(filters.searchQuery, ignoreCase = true) ||
					entry.login.contains(filters.searchQuery, ignoreCase = true)
			val matchesTags = filters.tagFilters.isEmpty() ||
					filters.tagFilters.all { tag -> entry.tags.any { it.equals(tag, ignoreCase = true) } }
			matchesSearch && matchesTags
		}
	}

	// Factory для создания ViewModel с параметрами
	class Factory(
		private val context: Context,
		private val assetManager: AssetManager
	) : ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return MainViewModel(context, assetManager) as T
		}
	}
}

// ==================== MainScreen ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
	viewModel: MainViewModel = viewModel(
		factory = MainViewModel.Factory(
			context = LocalContext.current.applicationContext,
			assetManager = LocalContext.current.assets
		)
	),
	onAddEntry: () -> Unit,
	onEditEntry: (PasswordEntry) -> Unit,
	onDeleteEntry: (PasswordEntry) -> Unit,
	onSettingsClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	val scope = rememberCoroutineScope()
	val snackbarHostState = remember { SnackbarHostState() }

	// Обработка событий из ViewModel
	LaunchedEffect(Unit) {
		viewModel.events.collect { event ->
			when (event) {
				is UiEvent.ShowError -> {
					snackbarHostState.showSnackbar(
						message = event.message,
						duration = SnackbarDuration.Long
					)
				}
				is UiEvent.ShowSuccess -> {
					snackbarHostState.showSnackbar(
						message = event.message,
						duration = SnackbarDuration.Short
					)
				}
				is UiEvent.NavigateBack -> { /* Обработка навигации во Fragment/Activity */ }
				is UiEvent.EditEntry -> {
					viewModel.setEntryToEdit(event.entry)
					onEditEntry(event.entry)
				}
			}
		}
	}

	Scaffold(
		modifier = modifier,
		topBar = {
			MainTopAppBar(
				onSearchClick = { viewModel.toggleFilterSheet(true) },
				onSettingsClick = { viewModel.toggleSettingsDialog(true) }
			)
		},
		floatingActionButton = {
			FloatingActionButton(
				onClick = onAddEntry,
				containerColor = MaterialTheme.colorScheme.primary,
				contentColor = MaterialTheme.colorScheme.onPrimary
			) {
				Icon(Icons.Default.Add, contentDescription = "Добавить запись")
			}
		},
		snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
		) {
			// Панель активных фильтров
			AnimatedVisibility(
				visible = state.filters.searchQuery.isNotBlank() || state.filters.tagFilters.isNotEmpty(),
				enter = fadeIn(),
				exit = fadeOut(),
				modifier = Modifier
					.fillMaxWidth()
					.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
					.padding(horizontal = 16.dp, vertical = 8.dp)
			) {
				ActiveFiltersBar(
					filters = state.filters,
					onRemoveTag = viewModel::removeTagFilter,
					onClearAll = viewModel::clearFilters,
					totalCount = state.allEntries.size,
					shownCount = state.filteredEntries.size
				)
			}

			// Индикатор загрузки
			if (state.isLoading) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.padding(24.dp),
					contentAlignment = Alignment.Center
				) {
					CircularProgressIndicator()
				}
			}

			// Список записей
			LazyColumn(
				contentPadding = PaddingValues(16.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				items(
					items = state.filteredEntries,
					key = { it.id }
				) { entry ->
					PasswordListItem(
						entry = entry,
						onEdit = { onEditEntry(entry) },
						onDelete = {
							// Показываем диалог подтверждения удаления
							// (реализация ниже через showModal)
							scope.launch {
								val result = snackbarHostState.showSnackbar(
									message = "Удалить «${entry.resourceName}»?",
									actionLabel = "Удалить",
									duration = SnackbarDuration.Short
								)
								if (result == SnackbarResult.ActionPerformed) {
									onDeleteEntry(entry)
								}
							}
						}
					)
				}

				if (state.filteredEntries.isEmpty() && !state.isLoading) {
					item {
						EmptyStateView(hasFilters = state.filters.searchQuery.isNotBlank() || state.filters.tagFilters.isNotEmpty())
					}
				}
			}
		}

		// Шторка фильтров
		if (state.showFilterSheet) {
			FilterBottomSheet(
				onDismiss = { viewModel.toggleFilterSheet(false) },
				searchQuery = state.filters.searchQuery,
				onSearchChange = viewModel::updateSearchQuery,
				tagFilters = state.filters.tagFilters,
				onTagFilterChange = viewModel::updateTagFilter,
				onClearAll = viewModel::clearFilters
			)
		}

		// Диалог настроек (заглушка)
		if (state.showSettingsDialog) {
			SettingsDialog(
				onDismiss = { viewModel.toggleSettingsDialog(false) }
			)
		}
	}
}

// ==================== TopAppBar ====================
@Composable
private fun MainTopAppBar(
	onSearchClick: () -> Unit,
	onSettingsClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	TopAppBar(
		title = { Text("Логины-пароли") },
		actions = {
			IconButton(onClick = onSearchClick) {
				Icon(Icons.Default.Search, contentDescription = "Фильтры")
			}
			IconButton(onClick = onSettingsClick) {
				Icon(Icons.Default.Settings, contentDescription = "Настройки")
			}
		},
		modifier = modifier
	)
}

// ==================== Active Filters Bar ====================
@Composable
private fun ActiveFiltersBar(
	filters: UiState.Filters,
	onRemoveTag: (String) -> Unit,
	onClearAll: () -> Unit,
	totalCount: Int,
	shownCount: Int,
	modifier: Modifier = Modifier
) {
	Column(modifier = modifier) {
		// Текущие фильтры
		if (filters.tagFilters.isNotEmpty()) {
			FlowRow(
				horizontalArrangement = Arrangement.spacedBy(4.dp),
				verticalArrangement = Arrangement.spacedBy(4.dp),
				modifier = Modifier.fillMaxWidth()
			) {
				filters.tagFilters.forEach { tag ->
					SuggestionChip(
						onClick = { onRemoveTag(tag) },
						label = { Text(tag) },
						icon = { Icon(Icons.Outlined.Close, contentDescription = "Удалить", modifier = Modifier.size(16.dp)) }
					)
				}
			}
			Spacer(modifier = Modifier.height(4.dp))
		}

		// Строка с информацией и кнопкой сброса
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = "Показано $shownCount из $totalCount",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			TextButton(onClick = onClearAll) {
				Text("Сбросить всё")
			}
		}
	}
}

// ==================== Password List Item ====================
@Composable
fun PasswordListItem(
	entry: PasswordEntry,
	onEdit: () -> Unit,
	onDelete: () -> Unit,
	modifier: Modifier = Modifier
) {
	@OptIn(ExperimentalMaterial3Api::class)
	var showDeleteConfirm by remember { mutableStateOf(false) }

	SwipeToDismissBox(
		state = rememberSwipeToDismissBoxState(
			positionalThreshold = { it * 0.25f },
			confirmValueChange = { value ->
				when (value) {
					SwipeToDismissBoxValue.EndToStart -> {
						showDeleteConfirm = true
						false // Отменяем свайп до подтверждения
					}
					SwipeToDismissBoxValue.StartToEnd -> {
						onEdit()
						false
					}
					else -> true
				}
			}
		),
		backgroundContent = { dismissState ->
			val color = when (dismissState.targetValue) {
				SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
				SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
				else -> Color.Transparent
			}
			val icon = when (dismissState.targetValue) {
				SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
				SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Edit
				else -> null
			}
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(color)
					.padding(16.dp),
				contentAlignment = when (dismissState.targetValue) {
					SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
					SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
					else -> Alignment.Center
				}
			) {
				icon?.let {
					Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
				}
			}
		},
		content = {
			Card(
				modifier = modifier
					.fillMaxWidth()
					.clickable(onClick = onEdit),
				colors = CardDefaults.cardColors(
					containerColor = MaterialTheme.colorScheme.surface,
					contentColor = MaterialTheme.colorScheme.onSurface
				),
				elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp)
				) {
					// Имя ресурса (16.sp)
					Text(
						text = entry.resourceName,
						style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)

					Spacer(modifier = Modifier.height(4.dp))

					// Логин (14.sp, минимум 12 для доступности)
					Text(
						text = entry.login.ifBlank { "—" },
						style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)

					// Теги (если есть)
					if (entry.tags.isNotEmpty()) {
						Spacer(modifier = Modifier.height(8.dp))
						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(4.dp),
							verticalArrangement = Arrangement.spacedBy(4.dp)
						) {
							entry.tags.take(3).forEach { tag ->
								AssistChip(
									onClick = { /* Можно добавить фильтрацию по тапу */ },
									label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
									border = AssistChipDefaults.assistChipBorder(
										borderColor = MaterialTheme.colorScheme.outlineVariant
									)
								)
							}
							if (entry.tags.size > 3) {
								Text(
									text = "+${entry.tags.size - 3}",
									style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						}
					}
				}
			}
		}
	)

	// Диалог подтверждения удаления
	if (showDeleteConfirm) {
		AlertDialog(
			onDismissRequest = { showDeleteConfirm = false },
			icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
			title = { Text("Удалить запись?") },
			text = { Text("Вы уверены, что хотите удалить «${entry.resourceName}»? Это действие нельзя отменить.") },
			confirmButton = {
				TextButton(
					onClick = {
						onDelete()
						showDeleteConfirm = false
					},
					colors = ButtonDefaults.textButtonColors(
						contentColor = MaterialTheme.colorScheme.error
					)
				) {
					Text("Удалить")
				}
			},
			dismissButton = {
				TextButton(onClick = { showDeleteConfirm = false }) {
					Text("Отмена")
				}
			}
		)
	}
}

// ==================== Empty State ====================
@Composable
private fun EmptyStateView(
	hasFilters: Boolean,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier
			.fillMaxWidth()
			.padding(32.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		Icon(
			imageVector = if (hasFilters) Icons.Default.Search else Icons.Default.Key,
			contentDescription = null,
			modifier = Modifier.size(64.dp),
			tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
		)
		Spacer(modifier = Modifier.height(16.dp))
		Text(
			text = if (hasFilters) "Ничего не найдено" else "Список пуст",
			style = MaterialTheme.typography.titleMedium
		)
		Spacer(modifier = Modifier.height(8.dp))
		Text(
			text = if (hasFilters)
				"Попробуйте изменить параметры поиска"
			else
				"Нажмите ➕ чтобы добавить первую запись",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = androidx.compose.ui.text.style.TextAlign.Center
		)
	}
}

// ==================== Filter Bottom Sheet ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
	onDismiss: () -> Unit,
	searchQuery: String,
	onSearchChange: (String) -> Unit,
	tagFilters: List<String>,
	onTagFilterChange: (List<String>) -> Unit,
	onClearAll: () -> Unit,
	modifier: Modifier = Modifier
) {
	var tagInput by remember { mutableStateOf("") }

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		dragHandle = { SheetDragHandle() }
	) {
		Column(
			modifier = modifier
				.fillMaxWidth()
				.padding(16.dp)
		) {
			Text(
				text = "Фильтры",
				style = MaterialTheme.typography.titleLarge,
				modifier = Modifier.padding(bottom = 16.dp)
			)

			// Поиск по тексту
			OutlinedTextField(
				value = searchQuery,
				onValueChange = onSearchChange,
				label = { Text("Поиск") },
				placeholder = { Text("Ресурс или логин") },
				trailingIcon = {
					if (searchQuery.isNotBlank()) {
						IconButton(onClick = { onSearchChange("") }) {
							Icon(Icons.Default.Close, contentDescription = "Очистить")
						}
					}
				},
				singleLine = true,
				modifier = Modifier.fillMaxWidth()
			)

			Spacer(modifier = Modifier.height(16.dp))

			// Фильтр по тегам
			Text(
				text = "Теги",
				style = MaterialTheme.typography.titleMedium,
				modifier = Modifier.padding(bottom = 8.dp)
			)

			// Выбранные теги
			if (tagFilters.isNotEmpty()) {
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(4.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp),
					modifier = Modifier.padding(bottom = 8.dp)
				) {
					tagFilters.forEach { tag ->
						FilterChip(
							selected = true,
							onClick = { onTagFilterChange(tagFilters - tag) },
							label = { Text(tag) },
							leadingIcon = {
								Icon(
									Icons.Outlined.Close,
									contentDescription = "Удалить",
									modifier = Modifier.size(16.dp)
								)
							}
						)
					}
				}
			}

			// Ввод нового тега
			OutlinedTextField(
				value = tagInput,
				onValueChange = { tagInput = it },
				label = { Text("Добавить тег") },
				placeholder = { Text("Например: work, personal") },
				trailingIcon = {
					if (tagInput.isNotBlank()) {
						IconButton(onClick = {
							if (tagInput !in tagFilters) {
								onTagFilterChange(tagFilters + tagInput.trim())
							}
							tagInput = ""
						}) {
							Icon(Icons.Default.Add, contentDescription = "Добавить")
						}
					}
				},
				singleLine = true,
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
				modifier = Modifier.fillMaxWidth()
			)

			// Быстрые теги (можно заменить на загрузку из DataStore)
			val quickTags = listOf("work", "personal", "finance", "social", "shopping", "other")
			if (quickTags.any { it !in tagFilters }) {
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					text = "Быстрый выбор:",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(4.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp),
					modifier = Modifier.padding(top = 4.dp)
				) {
					quickTags
						.filter { it !in tagFilters }
						.take(6)
						.forEach { tag ->
							SuggestionChip(
								onClick = { onTagFilterChange(tagFilters + tag) },
								label = { Text(tag) }
							)
						}
				}
			}

			Spacer(modifier = Modifier.weight(1f))

			// Кнопка сброса
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End
			) {
				TextButton(onClick = onClearAll) {
					Text("Сбросить все фильтры")
				}
			}
		}
	}
}

// ==================== Settings Dialog (заглушка) ====================
@Composable
private fun SettingsDialog(
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		icon = { Icon(Icons.Default.Settings, contentDescription = null) },
		title = { Text("Настройки") },
		text = { Text("Раздел настроек в разработке. Здесь будут:\n• Смена мастер-пароля\n• Экспорт/импорт\n• Облачная синхронизация") },
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text("Понятно")
			}
		},
		modifier = modifier
	)
}

// ==================== Preview ====================
@Preview(showBackground = true, name = "MainScreen Light")
@Composable
private fun MainScreenPreviewLight() {
	MyKlyuchikTheme(darkTheme = false) {
		// Для превью используем мок-данные
		MainScreen(
			onAddEntry = {},
			onEditEntry = {},
			onDeleteEntry = {},
			onSettingsClick = {}
		)
	}
}

@Preview(showBackground = true, name = "MainScreen Dark")
@Composable
private fun MainScreenPreviewDark() {
	MyKlyuchikTheme(darkTheme = true) {
		MainScreen(
			onAddEntry = {},
			onEditEntry = {},
			onDeleteEntry = {},
			onSettingsClick = {}
		)
	}
}
