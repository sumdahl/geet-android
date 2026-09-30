# Geet domain glossary

- **Engine**: the geet CLI (`libgeet.so`), which reads a link's metadata, finds the song on YouTube, downloads, converts and tags it. The app never does these itself. _Avoid: backend, server._
- **Link**: what the user shares or pastes: a Spotify, YouTube Music, YouTube, Apple Music or Deezer URL, or an `itunes:`/`deezer:` ref. It names a **track**, an **album** or a **playlist**.
- **Preview**: what a link is before anything downloads (name, cover, song count), from `geet info`.
- **Job**: one link in the download queue. It has a state (queued, running, done, failed, cancelled) and one **track** row per song. The Downloads screen shows one card per job. _Avoid: task, download (for the whole link)._
- **Track**: one song of a job, walking the engine's stages: resolved, downloading, tagging, done or failed.
- **Library**: the songs in the output folder (`Music/Geet` by default), as MediaStore knows them. A **playlist** in the library is a folder named after the playlist the songs came from.
- **Song**: a saved audio file in the library, with its tags, embedded cover and lyrics.
- **Lyrics**: a song's words, **synced** (each line has a start time) or plain. Saved with the song at download time.
- **Queue**: what the player plays next. _Not_ the download queue, which is made of **jobs**.
- **Cover colours**: the colour scheme taken from the current song's cover, used by the player, mini player and share sheet.
- **Engine setting**: a setting of the engine (format, bitrate, parallel downloads…), passed as a `GEET_*` variable. **App setting**: one of the app's own (theme, cover colours, download on share).
