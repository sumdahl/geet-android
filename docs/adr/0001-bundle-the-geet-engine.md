# 1. Bundle the geet engine instead of rewriting it

Geet downloads by running the geet CLI, built for Android with the NDK (`GOOS=android`) and shipped as `jniLibs/<abi>/libgeet.so`. yt-dlp (Python and its zipapp), ffmpeg, ffprobe and QuickJS come from youtubedl-android, also as native libraries. The app talks to the engine only through its CLI and `--json` output.

The alternative was a Kotlin rewrite of the metadata, matching and tagging, with NewPipeExtractor for YouTube. We chose bundling because geet's matching (scored against a 201-song corpus) and tagging already work, fixes land in both the desktop and the phone, and nothing runs on a server: it costs nothing to host.

Consequences: APKs are about 60 to 90 MB per ABI (Python and ffmpeg), so releases are split per ABI. Android only runs executables shipped as native libraries (W^X since Android 10), so the packaging must extract them (`useLegacyPackaging`). New app features that need engine work start as a geet PR.
