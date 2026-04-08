package com.example.block2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.block2.diary.DiaryEditorScreen
import com.example.block2.diary.DiaryListScreen
import com.example.block2.diary.DiaryViewModel
import com.example.block2.gallery.GalleryScreen
import com.example.block2.gallery.GalleryViewModel

private enum class AppSection(val title: String) {
    Diary("Дневник"),
    Gallery("Фотогалерея")
}

@Composable
fun Block2App(
    diaryViewModel: DiaryViewModel,
    galleryViewModel: GalleryViewModel
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedSectionName by rememberSaveable { mutableStateOf(AppSection.Diary.name) }
    var isCreatingDiaryEntry by rememberSaveable { mutableStateOf(false) }
    var editingDiaryFileName by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedSection = AppSection.valueOf(selectedSectionName)
    val diaryState by diaryViewModel.uiState.collectAsState()
    val galleryState by galleryViewModel.uiState.collectAsState()
    val isDiaryEditorVisible = isCreatingDiaryEntry || editingDiaryFileName != null
    val currentDiaryEntry = editingDiaryFileName?.let { fileName ->
        diaryState.entries.firstOrNull { it.fileName == fileName }
    }

    fun closeDiaryEditor() {
        isCreatingDiaryEntry = false
        editingDiaryFileName = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = if (isDiaryEditorVisible) "Запись дневника" else selectedSection.title,
                showBack = isDiaryEditorVisible,
                onBack = ::closeDiaryEditor
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isDiaryEditorVisible) {
                DiaryEditorScreen(
                    entry = currentDiaryEntry,
                    isNewEntry = isCreatingDiaryEntry,
                    onBack = ::closeDiaryEditor,
                    onSave = { title, body ->
                        if (isCreatingDiaryEntry) {
                            diaryViewModel.createEntry(title = title, body = body)
                        } else {
                            editingDiaryFileName?.let { fileName ->
                                diaryViewModel.updateEntry(
                                    fileName = fileName,
                                    title = title,
                                    body = body
                                )
                            }
                        }
                        closeDiaryEditor()
                    }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    AppSectionTabs(
                        selectedSection = selectedSection,
                        onSectionSelected = { selectedSectionName = it.name }
                    )
                    when (selectedSection) {
                        AppSection.Diary -> {
                            DiaryListScreen(
                                entries = diaryState.entries,
                                onAddNewEntry = {
                                    isCreatingDiaryEntry = true
                                    editingDiaryFileName = null
                                },
                                onOpenEntry = { entry ->
                                    editingDiaryFileName = entry.fileName
                                    isCreatingDiaryEntry = false
                                },
                                onDeleteEntry = { entry ->
                                    diaryViewModel.deleteEntry(entry.fileName)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp)
                            )
                        }

                        AppSection.Gallery -> {
                            GalleryScreen(
                                viewModel = galleryViewModel,
                                photos = galleryState.photos,
                                snackbarHostState = snackbarHostState,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSectionTabs(
    selectedSection: AppSection,
    onSectionSelected: (AppSection) -> Unit
) {
    androidx.compose.material3.TabRow(selectedTabIndex = selectedSection.ordinal) {
        AppSection.entries.forEach { section ->
            androidx.compose.material3.Tab(
                selected = section == selectedSection,
                onClick = { onSectionSelected(section) },
                text = { androidx.compose.material3.Text(section.title) }
            )
        }
    }
}

@Composable
private fun AppTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit
) {
    androidx.compose.material3.Surface(shadowElevation = 3.dp) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (showBack) {
                androidx.compose.material3.TextButton(onClick = onBack) {
                    androidx.compose.material3.Text("Назад")
                }
            }
            androidx.compose.material3.Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge
            )
        }
    }
}
