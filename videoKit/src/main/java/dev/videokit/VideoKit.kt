package dev.videokit

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import dev.videokit.internal.VideoConverter
import dev.videokit.internal.VideoUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@UnstableApi
class VideoKit {
    suspend fun convert(
        context: Context,
        inputUri: Uri,
        outputPath: String,
        config: ConversionConfig = ConversionConfig.Default,
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> {
        return try {
            VideoConverter.convert(
                context = context,
                inputUri = inputUri,
                outputPath = outputPath,
                config = config,
                onProgress = onProgress,
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upload(
        filePath: String,
        url: String,
        config: UploadConfig = UploadConfig(),
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> {
        val mainHandler = Handler(Looper.getMainLooper())
        return try {
            withContext(Dispatchers.IO) {
                VideoUploader.upload(
                    filePath = filePath,
                    url = url,
                    config = config,
                ) { progress ->
                    mainHandler.post { onProgress(progress) }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertAndUpload(
        context: Context,
        inputUri: Uri,
        outputPath: String,
        uploadUrl: String,
        conversionConfig: ConversionConfig = ConversionConfig.Default,
        uploadConfig: UploadConfig = UploadConfig(),
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> {
        val convertResult = convert(
            context = context,
            inputUri = inputUri,
            outputPath = outputPath,
            config = conversionConfig,
            onProgress = { progress -> onProgress(progress / 2f) },
        )
        if (convertResult.isFailure) {
            return convertResult
        }

        return upload(
            filePath = outputPath,
            url = uploadUrl,
            config = uploadConfig,
            onProgress = { progress -> onProgress(progress / 2f + 0.5f) },
        )
    }
}
