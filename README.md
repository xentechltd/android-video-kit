# VideoKit

Android library for working with video, currently supports convertion and upload to the server.


## Requirements

- Android SDK 24+
- Media3 1.5.0+ (portrait encoding support)

## Usage

Add JitPack to `settings.gradle.kts`:

```kotlin
maven { url = uri("https://jitpack.io") }
```

Add the dependency:

```kotlin
implementation("com.github.xentechltd:android-video-kit:v1.0.0")
```

Convert a video:

```kotlin
import dev.videokit.ConversionConfig
import dev.videokit.VideoKit

val result = VideoKit().convert(
    context = context,
    inputUri = inputUri,
    outputPath = outputPath,
    config = ConversionConfig.Default,
    onProgress = { progress -> /* 0.0–1.0 */ },
)
```

Upload a converted file to a presigned URL (S3-compatible storage):

```kotlin
import dev.videokit.UploadConfig
import dev.videokit.VideoKit

val result = VideoKit().upload(
    filePath = outputPath,
    url = presignedPutUrl,
    config = UploadConfig(contentType = "video/mp4"),
    onProgress = { progress -> /* 0.0–1.0 */ },
)
```

Convert and upload in one call (combined progress: 0–50% convert, 50–100% upload):

```kotlin
val result = VideoKit().convertAndUpload(
    context = context,
    inputUri = inputUri,
    outputPath = outputPath,
    uploadUrl = presignedPutUrl,
    conversionConfig = ConversionConfig.Default,
    uploadConfig = UploadConfig(contentType = "video/mp4"),
    onProgress = { progress -> /* 0.0–1.0 */ },
)
```

Media3 APIs are marked `@UnstableApi`. Opt in where required in your app module.

## Sample app

Run the `sample` configuration from Android Studio, or:

```bash
./gradlew :sample:installDebug
```

The sample app lets you adjust conversion settings, convert a video, and upload it to a presigned PUT URL.

## License

Copyright 2026 Xentech

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.