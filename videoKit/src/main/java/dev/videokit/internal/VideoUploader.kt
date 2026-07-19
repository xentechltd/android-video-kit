package dev.videokit.internal

import dev.videokit.UploadConfig
import java.io.IOException
import java.io.OutputStream

internal class ProgressOutputStream(
    private val delegate: OutputStream,
    private val totalBytes: Long,
    private val onProgress: (Float) -> Unit,
) : OutputStream() {
    private var bytesWritten = 0L

    override fun write(b: Int) {
        delegate.write(b)
        trackProgress(1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        trackProgress(len.toLong())
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }

    private fun trackProgress(delta: Long) {
        if (totalBytes <= 0L) {
            onProgress(0f)
            return
        }
        bytesWritten = (bytesWritten + delta).coerceAtMost(totalBytes)
        onProgress(bytesWritten.toFloat() / totalBytes.toFloat())
    }
}

internal object VideoUploader {
    fun upload(
        filePath: String,
        url: String,
        config: UploadConfig = UploadConfig(),
        onProgress: (Float) -> Unit = {},
    ) {
        val file = java.io.File(filePath)
        require(file.exists()) { "File not found: $filePath" }
        require(file.isFile) { "Path is not a file: $filePath" }
        require(url.isNotBlank()) { "Upload URL must not be blank" }

        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            connection.requestMethod = config.method
            connection.doOutput = true
            connection.useCaches = false
            connection.setRequestProperty("Content-Type", config.contentType)
            connection.setFixedLengthStreamingMode(file.length())

            connection.outputStream.use { outputStream ->
                val progressStream = ProgressOutputStream(outputStream, file.length()) { progress ->
                    onProgress(progress)
                }
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(progressStream)
                }
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException(
                    buildString {
                        append("Upload failed with HTTP $responseCode")
                        if (errorBody.isNotBlank()) {
                            append(": ")
                            append(errorBody)
                        }
                    },
                )
            }

            onProgress(1f)
        } finally {
            connection.disconnect()
        }
    }
}
