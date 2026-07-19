package dev.videokit.internal

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

internal object OutputPathResolver {
    fun resolveConvertOutputPath(
        context: Context,
        inputUri: Uri,
        outputPath: String?,
    ): String {
        if (!outputPath.isNullOrBlank()) {
            return outputPath
        }

        val fileName = "converted_${resolveOriginalFileName(context, inputUri)}"
        val outputDir = File(context.cacheDir, "videokit").apply { mkdirs() }
        return File(outputDir, fileName).absolutePath
    }

    private fun resolveOriginalFileName(context: Context, inputUri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(inputUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    cursor.getString(index)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { return ensureVideoExtension(sanitizeFileName(it)) }
                }
            }
        }

        inputUri.lastPathSegment
            ?.takeIf { it.isNotBlank() }
            ?.let { return ensureVideoExtension(sanitizeFileName(it)) }

        return "video.mp4"
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/]"), "_")
    }

    private fun ensureVideoExtension(name: String): String {
        return if ('.' in name) name else "$name.mp4"
    }
}
