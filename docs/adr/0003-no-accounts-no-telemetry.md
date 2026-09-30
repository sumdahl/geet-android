# 3. No accounts, no sign-in, no telemetry; fully open source

Geet never asks the user to sign in to anything, collects nothing, and uses no Google Play Services, Firebase or other proprietary SDKs. It's GPL-3.0 (youtubedl-android, which it links, is GPL-3.0; the MIT geet engine can ship inside it) and should build for F-Droid.

Consequences: only public playlists and videos can be downloaded, and the app says so when a link is private. YouTube's bot check has no cookie-based fix; the defences are modest parallelism, the engine's retries, keeping yt-dlp current, and a clear message to wait.
