package dev.videokit.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun UploadSection(
    uploadUrl: String,
    onUploadUrlChange: (String) -> Unit,
    lastOutputFile: File?,
    isUploading: Boolean,
    uploadProgress: Float,
    uploadStatus: String?,
    onUploadClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text(
            text = "Upload",
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text = "Paste a presigned PUT URL for your storage provider.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = uploadUrl,
            onValueChange = onUploadUrlChange,
            label = { Text("Upload URL") },
            placeholder = { Text("https://storage.example.com/...") },
            singleLine = false,
            minLines = 2,
            enabled = enabled && !isUploading,
            modifier = Modifier.fillMaxWidth(),
        )

        lastOutputFile?.let { file ->
            Text(
                text = "File ready: ${file.name} (${formatFileSize(file.length())})",
                style = MaterialTheme.typography.bodySmall,
            )
        } ?: Text(
            text = "Convert a video first to enable upload",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (isUploading) {
            LinearProgressIndicator(
                progress = { uploadProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Uploading… ${(uploadProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Button(
                onClick = onUploadClick,
                enabled = enabled && lastOutputFile != null && uploadUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Upload video")
            }
        }

        uploadStatus?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}
