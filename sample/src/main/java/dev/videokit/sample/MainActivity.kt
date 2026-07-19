package dev.videokit.sample

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.videokit.ConversionConfig
import dev.videokit.VideoKit
import dev.videokit.sample.ui.theme.VideoKitSampleTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoKitSampleTheme {
                VideoKitSampleScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoKitSampleScreen() {
    val context = LocalContext.current
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var isConverting by remember { mutableStateOf(false) }
    var conversionProgress by remember { mutableFloatStateOf(0f) }
    var conversionStatus by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember { mutableStateOf(false) }
    var conversionConfig by rememberSaveable(stateSaver = ConversionConfigUiState.Saver) {
        mutableStateOf(ConversionConfigUiState())
    }
    var lastOutputFile by remember { mutableStateOf<File?>(null) }
    var uploadUrl by rememberSaveable { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }

    val isBusy = isConverting || isUploading

    LaunchedEffect(Unit) {
        hasPermission = hasVideoPermissions(context as ComponentActivity)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        hasPermission = permissions.all { it.value }
        if (!hasPermission) {
            Toast.makeText(context, "Video access permission is required", Toast.LENGTH_LONG).show()
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        selectedVideoUri = uri
        conversionStatus = null
        conversionProgress = 0f
        uploadStatus = null
        lastOutputFile = null
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            permissionLauncher.launch(requiredVideoPermissions())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VideoKit Sample") },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Convert and upload with VideoKit",
                style = MaterialTheme.typography.headlineSmall,
            )

            ConversionConfigSection(
                config = conversionConfig,
                onConfigChange = { conversionConfig = it },
                enabled = !isBusy,
            )

            Button(
                onClick = { videoPickerLauncher.launch("video/*") },
                enabled = hasPermission && !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Pick video")
            }

            selectedVideoUri?.let { uri ->
                Text(
                    text = "Selected: ${uri.lastPathSegment ?: "Video"}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (isConverting) {
                    LinearProgressIndicator(
                        progress = { conversionProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Converting… ${(conversionProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Button(
                        onClick = {
                            (context as? ComponentActivity)?.let { activity ->
                                activity.lifecycleScope.launch {
                                    isConverting = true
                                    conversionStatus = null
                                    try {
                                        val outputFile = convertAndSaveVideo(
                                            activity = activity,
                                            inputUri = uri,
                                            config = conversionConfig.toConversionConfig(),
                                        ) { progress ->
                                            conversionProgress = progress
                                        }
                                        lastOutputFile = outputFile
                                        conversionStatus = "Video converted and ready to upload"
                                        Toast.makeText(context, "Video converted", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        conversionStatus = "Error: ${e.message}"
                                        Toast.makeText(
                                            context,
                                            "Conversion failed: ${e.message}",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    } finally {
                                        isConverting = false
                                    }
                                }
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Convert")
                    }
                }
            }

            conversionStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            UploadSection(
                uploadUrl = uploadUrl,
                onUploadUrlChange = { uploadUrl = it },
                lastOutputFile = lastOutputFile,
                isUploading = isUploading,
                uploadProgress = uploadProgress,
                uploadStatus = uploadStatus,
                onUploadClick = {
                    val file = lastOutputFile ?: return@UploadSection
                    val url = uploadUrl.trim()
                    if (url.isBlank()) return@UploadSection

                    (context as? ComponentActivity)?.let { activity ->
                        activity.lifecycleScope.launch {
                            isUploading = true
                            uploadStatus = null
                            uploadProgress = 0f
                            try {
                                val result = VideoKit().upload(
                                    filePath = file.absolutePath,
                                    url = url,
                                ) { progress ->
                                    uploadProgress = progress
                                }
                                result.fold(
                                    onSuccess = {
                                        uploadStatus = "Upload completed"
                                        Toast.makeText(context, "Upload completed", Toast.LENGTH_LONG).show()
                                    },
                                    onFailure = { error ->
                                        uploadStatus = "Upload error: ${error.message}"
                                        Toast.makeText(
                                            context,
                                            "Upload failed: ${error.message}",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    },
                                )
                            } finally {
                                isUploading = false
                            }
                        }
                    }
                },
                enabled = !isBusy,
            )
        }
    }
}

private fun requiredVideoPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }
}

private fun hasVideoPermissions(activity: ComponentActivity): Boolean {
    return requiredVideoPermissions().all { permission ->
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }
}

private suspend fun convertAndSaveVideo(
    activity: ComponentActivity,
    inputUri: Uri,
    config: ConversionConfig,
    onProgress: (Float) -> Unit,
): File {
    val fileName = "videokit_${System.currentTimeMillis()}.mp4"
    val outputFile = createOutputFile(activity, fileName)

    val result = VideoKit().convert(
        context = activity,
        inputUri = inputUri,
        outputPath = outputFile.absolutePath,
        config = config,
        onProgress = onProgress,
    )

    result.fold(
        onSuccess = { },
        onFailure = { error ->
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw error
        },
    )

    val uploadCacheDir = File(activity.cacheDir, "uploads").apply { mkdirs() }
    val uploadFile = File(uploadCacheDir, fileName)
    outputFile.copyTo(uploadFile, overwrite = true)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        publishVideoToGallery(activity, outputFile, fileName)
    }

    return uploadFile
}

private fun createOutputFile(activity: ComponentActivity, fileName: String): File {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val moviesDir = activity.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val outputDir = File(moviesDir, "VideoKit")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        return File(outputDir, fileName)
    }

    val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    val outputDir = File(moviesDir, "VideoKit")
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }
    return File(outputDir, fileName)
}

private fun publishVideoToGallery(
    activity: ComponentActivity,
    videoFile: File,
    fileName: String,
) {
    val contentValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VideoKit")
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }

    val uri = activity.contentResolver.insert(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        contentValues,
    ) ?: return

    activity.contentResolver.openOutputStream(uri)?.use { outputStream ->
        FileInputStream(videoFile).use { inputStream ->
            inputStream.copyTo(outputStream)
        }
    }

    contentValues.clear()
    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
    activity.contentResolver.update(uri, contentValues, null, null)
    videoFile.delete()
}
