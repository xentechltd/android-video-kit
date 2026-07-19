package dev.videokit.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.videokit.ConversionConfig

data class ConversionConfigUiState(
    val targetWidth: Int = 1080,
    val targetHeight: Int = 1920,
    val targetFps: Int = 15,
    val videoMimeType: String = MIME_H264,
    val portraitEncodingEnabled: Boolean = true,
) {
    fun toConversionConfig(): ConversionConfig {
        return ConversionConfig(
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            targetFps = targetFps,
            videoMimeType = videoMimeType,
            portraitEncodingEnabled = portraitEncodingEnabled,
        )
    }

    companion object {
        val Saver = mapSaver(
            save = { state ->
                mapOf(
                    "targetWidth" to state.targetWidth,
                    "targetHeight" to state.targetHeight,
                    "targetFps" to state.targetFps,
                    "videoMimeType" to state.videoMimeType,
                    "portraitEncodingEnabled" to state.portraitEncodingEnabled,
                )
            },
            restore = { saved ->
                ConversionConfigUiState(
                    targetWidth = saved["targetWidth"] as Int,
                    targetHeight = saved["targetHeight"] as Int,
                    targetFps = saved["targetFps"] as Int,
                    videoMimeType = saved["videoMimeType"] as String,
                    portraitEncodingEnabled = saved["portraitEncodingEnabled"] as Boolean,
                )
            },
        )
    }
}

private enum class ResolutionPreset(
    val label: String,
    val width: Int,
    val height: Int,
) {
    Portrait1080("1080×1920", 1080, 1920),
    Portrait720("720×1280", 720, 1280),
    Landscape1080("1920×1080", 1920, 1080),
    Square1080("1080×1080", 1080, 1080),
    Custom("Custom", 0, 0),
}

private const val MIME_H264 = "video/avc"
private const val MIME_HEVC = "video/hevc"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConversionConfigSection(
    config: ConversionConfigUiState,
    onConfigChange: (ConversionConfigUiState) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val selectedPreset = resolvePreset(config.targetWidth, config.targetHeight)
    var customWidthText by rememberSaveable(config.targetWidth) {
        mutableStateOf(config.targetWidth.toString())
    }
    var customHeightText by rememberSaveable(config.targetHeight) {
        mutableStateOf(config.targetHeight.toString())
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Conversion settings",
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text = "Resolution",
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ResolutionPreset.entries.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = {
                        if (preset == ResolutionPreset.Custom) {
                            onConfigChange(config.copy())
                            return@FilterChip
                        }
                        customWidthText = preset.width.toString()
                        customHeightText = preset.height.toString()
                        onConfigChange(
                            config.copy(
                                targetWidth = preset.width,
                                targetHeight = preset.height,
                            ),
                        )
                    },
                    label = { Text(preset.label) },
                    enabled = enabled,
                )
            }
        }

        if (selectedPreset == ResolutionPreset.Custom) {
            OutlinedTextField(
                value = customWidthText,
                onValueChange = { value ->
                    customWidthText = value
                    value.toIntOrNull()?.takeIf { it > 0 }?.let { width ->
                        onConfigChange(config.copy(targetWidth = width))
                    }
                },
                label = { Text("Width") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = customHeightText,
                onValueChange = { value ->
                    customHeightText = value
                    value.toIntOrNull()?.takeIf { it > 0 }?.let { height ->
                        onConfigChange(config.copy(targetHeight = height))
                    }
                },
                label = { Text("Height") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = "Output size: ${config.targetWidth}×${config.targetHeight}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "Frame rate: ${formatFpsLabel(config.targetFps)}",
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = config.targetFps.toFloat(),
            onValueChange = { value ->
                onConfigChange(config.copy(targetFps = value.toInt()))
            },
            valueRange = 0f..60f,
            steps = 59,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "Video codec",
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = config.videoMimeType == MIME_H264,
                onClick = { onConfigChange(config.copy(videoMimeType = MIME_H264)) },
                label = { Text("H.264") },
                enabled = enabled,
            )
            FilterChip(
                selected = config.videoMimeType == MIME_HEVC,
                onClick = { onConfigChange(config.copy(videoMimeType = MIME_HEVC)) },
                label = { Text("HEVC") },
                enabled = enabled,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        ConfigSwitchRow(
            title = "Portrait encoding",
            description = "Write portrait videos with width < height and rotation 0",
            checked = config.portraitEncodingEnabled,
            onCheckedChange = { enabledValue ->
                onConfigChange(config.copy(portraitEncodingEnabled = enabledValue))
            },
            enabled = enabled,
        )
    }
}

@Composable
private fun ConfigSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

private fun resolvePreset(width: Int, height: Int): ResolutionPreset {
    return ResolutionPreset.entries.firstOrNull { preset ->
        preset != ResolutionPreset.Custom && preset.width == width && preset.height == height
    } ?: ResolutionPreset.Custom
}

private fun formatFpsLabel(fps: Int): String {
    return if (fps <= 0) "Original (no limit)" else "$fps fps"
}
