package dev.videokit

data class UploadConfig(
    val contentType: String = "video/mp4",
    val method: String = "PUT",
)
