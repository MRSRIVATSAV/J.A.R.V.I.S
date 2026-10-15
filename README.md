# Jarvis Local AI

Jarvis Local AI is an Android app for private, on-device language and image inference. It uses Jetpack Compose, Kotlin, Room, DataStore, and the native `llama.cpp` runtime. The app contains no cloud inference client, account flow, analytics, or advertising SDK.

**Important verification status:** The Android app, ARM64 JNI bridge, and `libmtmd` native target build successfully. The exact catalog model pair was downloaded and both text and image generation succeeded through upstream `llama-mtmd-cli` on the host. The Android APK's JNI path was compiled but not exercised on an Android device; no Vivo T1 5G or emulator was available.

## Features

- Compose chat with saved conversations, rename/delete, copy, regeneration, and cancellation.
- Local system-prompt preferences and local Room history.
- Gallery image picker, thumbnail, replacement/removal, EXIF orientation handling, and downscaling to at most 896 pixels on the long edge.
- GGUF model import through Android's document picker; model and matching projector files can be selected together.
- Optional explicit download of the two official SmolVLM Q8_0 files from Hugging Face.
- Native token streaming using `llama.cpp`; image prompts use `libmtmd` and its image preprocessing/tokenization path.
- Model loading/unloading, load-time reporting, GGUF signature checks, compatibility errors, and active-model deletion protection.

## Architecture

- `ui/`: Compose screens for chat, history, models, and settings.
- `chat/`: ViewModel, conversation orchestration, and image preprocessing.
- `data/`: Room conversation/message/model records and DataStore preferences.
- `models/`: the model catalog, SAF import, and explicit model downloads.
- `inference/`: `LocalModelEngine` interface and `LlamaCppEngine` implementation.
- `app/src/main/cpp/`: JNI bridge, plus CMake fetching the pinned upstream llama.cpp commit and building `llama` and `mtmd` for ARM64.

The runtime source is fetched at build time from [ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp), pinned to commit `620d2225cc436ab0b001e3200e590e3b286ef9ab`. The Kotlin engine runs inference on `Dispatchers.IO`. The native context uses a 2048-token context and CPU inference; GPU offload is not enabled.

## Requirements

- Android Studio with JDK 17.
- Android SDK Platform 36 and Build Tools 36.0.0.
- Android NDK 28.2.13676358 and CMake 3.31.6.
- Gradle wrapper is committed; no system Gradle is required.
- First build requires network access to resolve Maven dependencies and fetch the pinned llama.cpp source. Inference itself does not use the network.

## Build Locally

In Android Studio, open this repository, allow Gradle sync, set the Gradle JDK to 17, and install the SDK/NDK/CMake versions listed above. Build the debug APK with:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The expected APK is `app/build/outputs/apk/debug/app-debug.apk`. To build only the native ARM64 library, run:

```sh
./gradlew :app:externalNativeBuildDebug
```

The application currently packages `arm64-v8a`, matching the Vivo T1 5G target. It does not package x86 emulator libraries.

## GitHub Actions APK

The **Build Android APK** workflow in `.github/workflows/build-apk.yml` runs on pushes to `main` and manual `workflow_dispatch`. It installs Android SDK, NDK, and CMake components, runs `testDebugUnitTest`, builds the debug APK with the committed wrapper, verifies that the output exists, and uploads it as the `jarvis-local-ai-debug-apk` artifact. Download that artifact from the completed workflow run's **Artifacts** section.

The uploaded APK is a debug build and is not Play Store-ready. For a signed release, use Android Studio's **Build > Generate Signed Bundle / APK** flow, create a private keystore outside this repository, and keep the keystore and passwords in a secure password manager or CI secrets. Do not commit signing files or credentials. Release signing is not configured in this project.

## Install on a Vivo T1 5G

1. Download `app-debug.apk` from the workflow artifact or build it locally.
2. Transfer it to the phone and open it, allowing installation from that file manager if Android asks.
3. Or enable USB debugging and run `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Open **Models**, import a compatible GGUF (and its matching projector for vision), or explicitly download the listed model pair. Then select **Load**.

The app requests no broad storage, camera, microphone, contacts, or location permission. Image selection and model import use Android document/photo pickers.

## Models and Compatibility

The native runtime is `llama.cpp` with its `libmtmd` multimodal component. Supported input files are GGUF model files and, for vision, a separate compatible `mmproj` GGUF. A `.gguf` extension and file signature are only file-level checks; llama.cpp performs the real architecture load, while `libmtmd` validates the projector/model pairing and vision capability. Embedded chat templates are required by the current JNI bridge. Unsupported architecture, template, projector, or memory errors are reported instead of returning a fake response.

The optional catalog entry is:

- **SmolVLM-500M-Instruct Q8_0**, model and matching `mmproj` from `ggml-org/SmolVLM-500M-Instruct-GGUF` on Hugging Face.
- Downloads are pinned to repository revision `72e986006ef53e37cdd3f6d4241c90b0f01df376`. The model file is 436,806,912 bytes and the projector is 108,783,360 bytes (about 520 MiB total).
- The upstream llama.cpp multimodal guide lists SmolVLM GGUF support. The model card identifies Apache-2.0 licensing. Review the [model card](https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF) and [llama.cpp multimodal guide](https://github.com/ggml-org/llama.cpp/blob/master/docs/multimodal.md) before use.
- The UI's 1.5-2.5 GB memory figure is a rough estimate, not a measured requirement. The two files are not bundled with this app. On a 6 GB phone, Android, other running apps, image resolution, context allocation, and thermal conditions affect whether loading succeeds.

Other GGUF models can be imported. The importer does not promise that every GGUF architecture works, and it does not infer vision support from a filename. Choose **Load** to ask the actual runtime. A vision projector must match the text model; selecting unrelated files may fail at load or image initialization.

## Offline and Network Behavior

- Chat, history, model import, model loading, and inference are local.
- The app's `INTERNET` permission is used only when the user explicitly presses **Download pair**. That action retrieves the two model files over HTTPS from Hugging Face.
- No model is automatically downloaded. After a model pair is present on the device, inference does not require connectivity.
- The first source build also needs network access for Maven artifacts and the pinned llama.cpp Git checkout. This is a build-time dependency, not an inference service.

## Troubleshooting

- **SDK not found:** Install Android Platform 36, Build Tools 36.0.0, NDK 28.2.13676358, and CMake 3.31.6; set `ANDROID_HOME` or use Android Studio's SDK configuration.
- **Native build fails:** Ensure network is available during the first build, use JDK 17 and the committed Gradle wrapper, then retry `./gradlew :app:externalNativeBuildDebug --stacktrace`.
- **Model fails to load:** Confirm the file is a complete GGUF supported by the pinned llama.cpp revision and contains a supported chat template. For image use, import the matching projector from the same model release.
- **Out of memory:** Close other apps, unload other models, use a smaller quantization, and retry. A file fitting in storage does not prove it fits in RAM. The current native context is 2048 tokens; this is not a promise of successful allocation on every device.
- **Generation settings:** Context is fixed at 2048 tokens and temperature is fixed at 0.7 in this build; there are no UI controls for settings the current app does not expose to its native runtime.
- **Image prompt rejected:** Load a vision-capable model together with its matching projector. Text-only models explicitly reject image inference.
- **Download interrupted:** Cancelled partial files are discarded. Retry the explicit download while connected; ensure there is enough free app storage for both files.

## Add a Model

To add a curated download, add a catalog entry with official file URLs, licensing, exact model/projector names, quantization, and conservative metadata. Do not bundle model weights. Before labeling a new entry verified, check the model's license, upstream `llama.cpp`/`libmtmd` support, chat template, tokenizer, image processor, projector, and memory use; then load and test the exact files on representative hardware. Imported models remain runtime-validated rather than implicitly trusted.

## Tests and Known Limitations

Verified in the current workspace:

- Gradle project configuration with Gradle 8.13 and JDK 17.
- Kotlin debug compilation.
- ARM64 JNI/native compilation against the pinned llama.cpp and `libmtmd` sources.
- Four model catalog/GGUF validation unit tests passed.
- Full `testDebugUnitTest assembleDebug` completed successfully; the debug APK was inspected and includes the ARM64 `llama`, `mtmd`, ggml, and app JNI libraries.
- Host-side text-only inference and image-plus-question inference succeeded with the exact SmolVLM-500M-Instruct Q8_0 model and matched projector, using the upstream CLI built from the same llama.cpp commit. The image smoke test used a local Ubuntu logo and returned a relevant visual description.
- Android Lint passed.

Not verified here:

- Launch on Android or a Vivo T1 5G.
- Inference through the APK's JNI bridge, target-device speed/peak RAM, cancellation responsiveness on-device, and offline operation on a handset.
- Instrumentation tests and release signing.

The current UI persists image attachments and supports regeneration of the latest user request, but past images are not replayed as multimodal context in later turns. Only one current image is sent per generation. Audio, video, multiple images, GPU acceleration, and background model downloads are not implemented.