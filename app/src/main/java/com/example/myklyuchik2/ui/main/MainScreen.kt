package com.example.myklyuchik2.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.myklyuchik2.data.model.PasswordEntry
import com.example.myklyuchik2.data.storage.JsonStorage
import com.example.myklyuchik2.data.storage.SecureStorage
import com.example.myklyuchik2.ui.main.model.UiEvent
import com.example.myklyuchik2.ui.main.model.UiState
import com.example.myklyuchik2.ui.theme.MyKlyuchikTheme
import androidx.navigation.NavController
import com.example.myklyuchik2.ui.navigation.Screen
import com.example.myklyuchik2.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

// ==================== MainScreen ====================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
	navController: NavController,
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
				//onSettingsClick = { viewModel.toggleSettingsDialog(true) },
				onSettingsClick = { navController.navigate("settings") }, // Direct navigation
				navController = navController
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
					//items = uiState.filteredEntries,
					items = state.filteredEntries,
					key = { it.id }
				) { entry ->
					PasswordListItem(
						entry = entry ?: return@items, // Add null check
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
				if (!state.isLoading && state.filteredEntries.isEmpty()) {
				//if (state.filteredEntries.isEmpty() && !state.isLoading) {
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
		/*if (state.showSettingsDialog) {
			/*SettingsDialog(
				onDismiss = { viewModel.toggleSettingsDialog(false) }
			)*/
			navController.navigate("settings")
		}*/
	}
}

// ==================== TopAppBar ====================
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainTopAppBar(
	onSearchClick: () -> Unit,
	navController: NavController,
	//onSettingsClick: () -> Unit = { navController.navigate("settings") },
	onSettingsClick: () -> Unit = { navController.navigate(Screen.Settings.route) },
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
//@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@OptIn(ExperimentalLayoutApi::class)
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
			//@OptIn(ExperimentalLayoutApi::class)
			//@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
			/*FlowRow(
				modifier = Modifier.fillMaxWidth()
			) {*/
			FlowRow(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(4.dp),
				verticalArrangement = Arrangement.spacedBy(4.dp)
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PasswordListItem(
    entry: PasswordEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
   // @OptIn(ExperimentalMaterial3Api::class)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.25f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    showDeleteConfirm = true
                    false // Prevent auto-dismiss until confirmed
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEdit()
                    false
                }
                else -> true
            }
        }
    )

    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val direction = state.targetValue
            val color = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                else -> Color.Transparent
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Edit
                else -> null
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(16.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.Center
                }
            ) {
                icon?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
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
		            modifier = Modifier.padding(16.dp),
		            verticalArrangement = Arrangement.spacedBy(8.dp)
	            ) {
		            Text(
			            //text = "Resource: ${entry.resourceName}",
			            text = entry.resourceName,
			            style = MaterialTheme.typography.titleMedium
		            )
		            Text(
			            text = "Login: ${entry.login}",
			            style = MaterialTheme.typography.bodyMedium
		            )
	            }
            }
        }
    )

    // AlertDialog for delete confirmation (unchanged)
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
//@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
		//dragHandle = { SheetDragHandle() } deprecated, no longer used
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
				//@OptIn(ExperimentalLayoutApi::class)
				//@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

			/*// Быстрые теги (можно заменить на загрузку из DataStore)
			val quickTags = listOf("www", "PC", "моб", "покупки", "работа", "проч")
			if (quickTags.any { it !in tagFilters }) {
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					text = "Быстрый выбор:",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				@OptIn(ExperimentalLayoutApi::class)
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
			}*/

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
	val navController = rememberNavController()

	MyKlyuchikTheme(darkTheme = false) {
		MainScreen(
			navController = navController,
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
	val navController = rememberNavController()

	MyKlyuchikTheme(darkTheme = true) {
		MainScreen(
			navController = navController,
			onAddEntry = {},
			onEditEntry = {},
			onDeleteEntry = {},
			onSettingsClick = {}
		)
	}
}

