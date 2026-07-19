package dev.videokit.internal

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import dev.videokit.ConversionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object VideoConverter {
    suspend fun convert(
        context: Context,
        inputUri: Uri,
        outputPath: String,
        config: ConversionConfig,
        onProgress: (Float) -> Unit,
    ) {
        val mediaItem = MediaItem.fromUri(inputUri)
        val videoEffects = buildVideoEffects(config)

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(false)
            .setRemoveVideo(false)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        withContext(Dispatchers.Main) {
            runTransformer(context, editedMediaItem, outputPath, config, onProgress)
        }
    }

    private fun buildVideoEffects(config: ConversionConfig): List<Effect> {
        val videoEffects = mutableListOf<Effect>(
            Presentation.createForWidthAndHeight(
                config.targetWidth,
                config.targetHeight,
                Presentation.LAYOUT_SCALE_TO_FIT,
            ),
        )

        if (config.targetFps > 0) {
            videoEffects.add(
                FrameDropEffect.createDefaultFrameDropEffect(config.targetFps.toFloat()),
            )
        }

        return videoEffects
    }

    private suspend fun runTransformer(
        context: Context,
        editedMediaItem: EditedMediaItem,
        outputPath: String,
        config: ConversionConfig,
        onProgress: (Float) -> Unit,
    ): ExportResult = suspendCancellableCoroutine { continuation ->
        val progressHolder = ProgressHolder()
        val handler = Handler(Looper.getMainLooper())
        var progressPollRunnable: Runnable? = null

        fun stopProgressPolling() {
            progressPollRunnable?.let(handler::removeCallbacks)
        }

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                stopProgressPolling()
                onProgress(1f)
                continuation.resume(result)
            }

            override fun onError(
                composition: Composition,
                result: ExportResult,
                exception: ExportException,
            ) {
                stopProgressPolling()

                val errorMessage = exception.message.orEmpty()
                val causeMessage = exception.cause?.message.orEmpty()

                if (errorMessage.contains("GL_EXT_YUV_target") ||
                    causeMessage.contains("GL_EXT_YUV_target") ||
                    errorMessage.contains("Video frame processing error")
                ) {
                    continuation.resumeWithException(
                        Exception(
                            "HDR video processing is not supported on this device. " +
                                "Use an SDR video or test on a physical device.",
                        ),
                    )
                } else {
                    continuation.resumeWithException(exception)
                }
            }
        }

        val transformer = Transformer.Builder(context)
            .setTransformationRequest(
                TransformationRequest.Builder()
                    .setVideoMimeType(config.videoMimeType)
                    .setHdrMode(HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                    .build(),
            )
            .setPortraitEncodingEnabled(config.portraitEncodingEnabled)
            .addListener(listener)
            .build()

        progressPollRunnable = object : Runnable {
            override fun run() {
                val progressState = transformer.getProgress(progressHolder)
                if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress / 100f)
                }
                if (progressState != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    handler.postDelayed(this, PROGRESS_POLL_INTERVAL_MS)
                }
            }
        }

        transformer.start(editedMediaItem, outputPath)
        progressPollRunnable?.let(handler::post)

        continuation.invokeOnCancellation {
            stopProgressPolling()
            transformer.cancel()
        }
    }

    private const val PROGRESS_POLL_INTERVAL_MS = 500L
}
