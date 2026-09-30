# Geet

Download music on Android, beautifully. Share a Spotify, YouTube Music or YouTube link to Geet and the songs land in your Music folder, tagged, with album covers and synced lyrics, then play them in a player that takes on each song's colours.

- **Share to download:** share a song, album or playlist from Spotify, YouTube Music or YouTube, and a sheet shows what it is before you tap Download.
- **Done properly:** each song is matched to the right upload by the [geet](https://github.com/sumdahl/geet) engine and saved with its album, artists, year, cover and lyrics. Songs you already have are never downloaded twice.
- **A player worth using:** synced lyrics like Spotify's, a live spectrum, lock-screen and earbud controls, and colours from the album cover.
- **Material 3 Expressive**, smooth on 120 Hz screens.
- **Private by design:** no accounts, no sign-in, no tracking. Free and open source (GPL-3.0).

Geet needs Android 12 or newer. Download it from [Releases](https://github.com/sumdahl/geet-android/releases).

## Building

You need JDK 17, the Android SDK, an NDK and Go, and a checkout of [geet](https://github.com/sumdahl/geet) next to this one (the engine is built from it):

```bash
git clone https://github.com/sumdahl/geet ../geet
./gradlew installDebug
```

See [AGENTS.md](AGENTS.md) for the architecture and the full check.

## Your logo

Replace `app/src/main/res/drawable/ic_launcher_foreground.xml` (the colour logo, kept within the middle 66% of a 108 dp square) and `ic_launcher_monochrome.xml` (a one-colour silhouette for themed icons). The splash screen and notifications use them too.
