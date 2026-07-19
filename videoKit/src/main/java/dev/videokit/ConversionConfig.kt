package dev.videokit

data class ConversionConfig(
    val targetWidth: Int = 1080,
    val targetHeight: Int = 1920,
    val targetFps: Int = 15,
    val videoMimeType: String = "video/avc",
    val portraitEncodingEnabled: Boolean = true,
) {
    companion object {
        val Default = ConversionConfig()
    }
}
