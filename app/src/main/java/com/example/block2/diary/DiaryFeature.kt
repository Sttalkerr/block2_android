package com.example.block2.diary

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat

data class DiaryEntry(
    val fileName: String,
    val title: String,
    val body: String,
    val createdAt: Long
) {
    val previewText: String
        get() = body.replace("\n", " ").trim().take(40)
}

data class DiaryUiState(
    val entries: List<DiaryEntry> = emptyList()
)

class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DiaryRepository(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _uiState = MutableStateFlow(DiaryUiState())

    val uiState = _uiState.asStateFlow()

    init {
        loadEntries()
    }

    fun createEntry(title: String, body: String) {
        if (title.isBlank() && body.isBlank()) return

        scope.launch {
            val entry = repository.createEntry(title = title, body = body)
            _uiState.update { state ->
                state.copy(entries = listOf(entry) + state.entries)
            }
        }
    }

    fun updateEntry(fileName: String, title: String, body: String) {
        if (title.isBlank() && body.isBlank()) return

        scope.launch {
            val entry = repository.updateEntry(
                fileName = fileName,
                title = title,
                body = body
            ) ?: return@launch

            _uiState.update { state ->
                state.copy(
                    entries = state.entries.map { existing ->
                        if (existing.fileName == fileName) entry else existing
                    }
                )
            }
        }
    }

    fun deleteEntry(fileName: String) {
        scope.launch {
            if (repository.deleteEntry(fileName)) {
                _uiState.update { state ->
                    state.copy(entries = state.entries.filterNot { it.fileName == fileName })
                }
            }
        }
    }

    private fun loadEntries() {
        scope.launch {
            _uiState.value = DiaryUiState(entries = repository.loadEntries())
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}

private class DiaryRepository(application: Application) {
    private val filesDirectory = application.filesDir

    fun loadEntries(): List<DiaryEntry> {
        return filesDirectory
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "txt" }
            .mapNotNull(::readEntry)
            .sortedByDescending { it.createdAt }
    }

    fun createEntry(title: String, body: String): DiaryEntry {
        val timestamp = System.currentTimeMillis()
        val fileName = buildFileName(timestamp = timestamp, title = title)
        val file = File(filesDirectory, fileName)
        file.writeText(
            buildEntryContent(
                title = title,
                createdAt = timestamp,
                body = body
            )
        )
        return DiaryEntry(
            fileName = fileName,
            title = title.trim(),
            body = body.trim(),
            createdAt = timestamp
        )
    }

    fun updateEntry(fileName: String, title: String, body: String): DiaryEntry? {
        val file = File(filesDirectory, fileName)
        if (!file.exists()) return null

        val createdAt = extractTimestamp(fileName)
        file.writeText(
            buildEntryContent(
                title = title,
                createdAt = createdAt,
                body = body
            )
        )

        return DiaryEntry(
            fileName = fileName,
            title = title.trim(),
            body = body.trim(),
            createdAt = createdAt
        )
    }

    fun deleteEntry(fileName: String): Boolean {
        return File(filesDirectory, fileName).delete()
    }

    private fun readEntry(file: File): DiaryEntry? {
        return runCatching {
            val lines = file.readLines()
            val title = lines.firstOrNull()?.removePrefix("title=")?.trim().orEmpty()
            val createdAt = lines.getOrNull(1)
                ?.removePrefix("createdAt=")
                ?.toLongOrNull()
                ?: extractTimestamp(file.name)
            val separatorIndex = lines.indexOf("---")
            val body = if (separatorIndex >= 0) {
                lines.drop(separatorIndex + 1).joinToString("\n")
            } else {
                lines.drop(2).joinToString("\n")
            }

            DiaryEntry(
                fileName = file.name,
                title = title,
                body = body.trim(),
                createdAt = createdAt
            )
        }.getOrNull()
    }

    private fun buildFileName(timestamp: Long, title: String): String {
        val sanitizedTitle = title
            .trim()
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), "_")
            .trim('_')
            .take(40)
        val suffix = sanitizedTitle.takeIf { it.isNotBlank() }?.let { "_$it" }.orEmpty()
        return "${timestamp}${suffix}.txt"
    }

    private fun buildEntryContent(
        title: String,
        createdAt: Long,
        body: String
    ): String {
        return buildString {
            appendLine("title=${title.trim()}")
            appendLine("createdAt=$createdAt")
            appendLine("---")
            append(body.trim())
        }
    }

    private fun extractTimestamp(fileName: String): Long {
        val prefix = fileName.substringBefore('_').substringBefore('.')
        return prefix.toLongOrNull() ?: System.currentTimeMillis()
    }
}

@Composable
fun DiaryListScreen(
    entries: List<DiaryEntry>,
    onAddNewEntry: () -> Unit,
    onOpenEntry: (DiaryEntry) -> Unit,
    onDeleteEntry: (DiaryEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (entries.isEmpty()) {
            EmptyDiaryState(
                onAddNewEntry = onAddNewEntry,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                items(
                    items = entries,
                    key = { it.fileName }
                ) { entry ->
                    DiaryEntryCard(
                        entry = entry,
                        onOpenEntry = { onOpenEntry(entry) },
                        onDeleteEntry = { onDeleteEntry(entry) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(88.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = onAddNewEntry,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("+")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiaryEntryCard(
    entry: DiaryEntry,
    onOpenEntry: () -> Unit,
    onDeleteEntry: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpenEntry,
                    onLongClick = { menuExpanded = true }
                ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (entry.title.isBlank()) "Без заголовка" else entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatDiaryDate(entry.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (entry.previewText.isBlank()) "Пустая запись" else entry.previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Удалить") },
                onClick = {
                    menuExpanded = false
                    onDeleteEntry()
                }
            )
        }
    }
}

@Composable
private fun EmptyDiaryState(
    onAddNewEntry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "У вас пока нет записей",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Нажмите +, чтобы создать первую",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAddNewEntry) {
            Text("Новая запись")
        }
    }
}

@Composable
fun DiaryEditorScreen(
    entry: DiaryEntry?,
    isNewEntry: Boolean,
    onBack: () -> Unit,
    onSave: (title: String, body: String) -> Unit
) {
    var title by remember(entry?.fileName, isNewEntry) {
        mutableStateOf(entry?.title.orEmpty())
    }
    var body by remember(entry?.fileName, isNewEntry) {
        mutableStateOf(entry?.body.orEmpty())
    }

    LaunchedEffect(entry?.fileName, isNewEntry) {
        title = entry?.title.orEmpty()
        body = entry?.body.orEmpty()
    }

    if (!isNewEntry && entry == null) {
        MissingDiaryEntryState(onBack = onBack)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Заголовок (необязательно)") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text("Текст заметки") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onBack) {
                Text("Назад")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = { onSave(title, body) },
                enabled = title.isNotBlank() || body.isNotBlank()
            ) {
                Text("Сохранить")
            }
        }
    }
}

@Composable
private fun MissingDiaryEntryState(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Запись не найдена",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text("Вернуться")
        }
    }
}

private fun formatDiaryDate(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(
        DateFormat.SHORT,
        DateFormat.SHORT
    ).format(timestamp)
}
