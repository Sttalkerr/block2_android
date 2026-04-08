package com.example.block2.gallery

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GalleryPhoto(
    val fileName: String,
    val absolutePath: String,
    val lastModified: Long
) {
    val file: File
        get() = File(absolutePath)
}

data class GalleryUiState(
    val photos: List<GalleryPhoto> = emptyList()
)

private data class PendingPhoto(
    val file: File,
    val uri: Uri
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GalleryRepository(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _uiState = MutableStateFlow(GalleryUiState())
    private var pendingPhoto: PendingPhoto? = null

    val uiState = _uiState.asStateFlow()

    init {
        refreshPhotos()
    }

    fun preparePhotoCapture(): Uri? {
        val preparedPhoto = repository.createPendingPhoto() ?: return null
        pendingPhoto = preparedPhoto
        return preparedPhoto.uri
    }

    fun onPhotoCaptured(success: Boolean) {
        val currentPendingPhoto = pendingPhoto
        pendingPhoto = null

        if (!success) {
            currentPendingPhoto?.file?.delete()
            return
        }

        refreshPhotos()
    }

    fun refreshPhotos() {
        scope.launch {
            _uiState.update {
                it.copy(photos = repository.loadPhotos())
            }
        }
    }

    suspend fun exportPhoto(photo: GalleryPhoto): Boolean {
        return withContext(Dispatchers.IO) {
            repository.exportPhoto(photo)
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}

private class GalleryRepository(private val application: Application) {
    private val picturesDirectory: File
        get() = application.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: File(application.filesDir, "pictures").apply { mkdirs() }

    fun loadPhotos(): List<GalleryPhoto> {
        return picturesDirectory
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                GalleryPhoto(
                    fileName = file.name,
                    absolutePath = file.absolutePath,
                    lastModified = file.lastModified()
                )
            }
    }

    fun createPendingPhoto(): PendingPhoto? {
        if (!picturesDirectory.exists() && !picturesDirectory.mkdirs()) {
            return null
        }

        val fileName = "IMG_${
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        }.jpg"
        val photoFile = File(picturesDirectory, fileName)
        if (!photoFile.exists()) {
            photoFile.createNewFile()
        }

        val uri = FileProvider.getUriForFile(
            application,
            "${application.packageName}.fileprovider",
            photoFile
        )
        return PendingPhoto(file = photoFile, uri = uri)
    }

    fun exportPhoto(photo: GalleryPhoto): Boolean {
        val sourceFile = photo.file
        if (!sourceFile.exists()) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportWithMediaStore(sourceFile)
        } else {
            exportToPublicPictures(sourceFile)
        }
    }

    private fun exportWithMediaStore(sourceFile: File): Boolean {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/Block2Gallery"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = application.contentResolver
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return false

        return runCatching {
            resolver.openOutputStream(uri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            true
        }.getOrElse {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun exportToPublicPictures(sourceFile: File): Boolean {
        val publicDirectory = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        val targetDirectory = File(publicDirectory, "Block2Gallery")
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            return false
        }

        val targetFile = File(targetDirectory, sourceFile.name)
        return runCatching {
            sourceFile.inputStream().use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            MediaScannerConnection.scanFile(
                application,
                arrayOf(targetFile.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
            true
        }.getOrElse {
            false
        }
    }
}

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    photos: List<GalleryPhoto>,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedPhoto by remember { mutableStateOf<GalleryPhoto?>(null) }
    var pendingExportPhoto by remember { mutableStateOf<GalleryPhoto?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        viewModel.onPhotoCaptured(success)
    }

    val exportPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingExportPhoto?.let { photo ->
                coroutineScope.launch {
                    exportSelectedPhoto(
                        viewModel = viewModel,
                        photo = photo,
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }
        pendingExportPhoto = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.preparePhotoCapture()?.let(takePictureLauncher::launch)
        }
    }

    fun startCameraFlow() {
        if (hasPermission(context, Manifest.permission.CAMERA)) {
            viewModel.preparePhotoCapture()?.let(takePictureLauncher::launch)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier) {
        if (photos.isEmpty()) {
            EmptyGalleryState(
                onTakePhoto = ::startCameraFlow,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = photos,
                    key = { it.absolutePath }
                ) { photo ->
                    GalleryPhotoCard(
                        photo = photo,
                        onClick = { selectedPhoto = photo }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = ::startCameraFlow,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("Фото")
        }
    }

    selectedPhoto?.let { photo ->
        GalleryPhotoDialog(
            photo = photo,
            onDismiss = { selectedPhoto = null },
            onExport = {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    !hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                ) {
                    pendingExportPhoto = photo
                    exportPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    coroutineScope.launch {
                        exportSelectedPhoto(
                            viewModel = viewModel,
                            photo = photo,
                            snackbarHostState = snackbarHostState
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun GalleryPhotoCard(
    photo: GalleryPhoto,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp)
    ) {
        val bitmap = rememberBitmap(photo.absolutePath, sampleSize = 6)
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = photo.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Фото",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun GalleryPhotoDialog(
    photo: GalleryPhoto,
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val bitmap = rememberBitmap(photo.absolutePath, sampleSize = 2)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = photo.fileName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = photo.fileName,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onExport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Экспортировать в галерею")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Закрыть")
                }
            }
        }
    }
}

@Composable
private fun EmptyGalleryState(
    onTakePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "У вас пока нет фото",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onTakePhoto) {
            Text("Сделать первое фото")
        }
    }
}

private suspend fun exportSelectedPhoto(
    viewModel: GalleryViewModel,
    photo: GalleryPhoto,
    snackbarHostState: SnackbarHostState
) {
    val exported = viewModel.exportPhoto(photo)
    snackbarHostState.showSnackbar(
        message = if (exported) {
            "Фото добавлено в галерею"
        } else {
            "Не удалось экспортировать фото"
        }
    )
}

@Composable
private fun rememberBitmap(
    path: String,
    sampleSize: Int
): android.graphics.Bitmap? {
    return remember(path, sampleSize) {
        BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
        )
    }
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        permission
    ) == PackageManager.PERMISSION_GRANTED
}
