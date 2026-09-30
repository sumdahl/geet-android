# 5. Material 3 Expressive, the Pixel way

The whole app uses Material 3 Expressive fully, as Pixel's own apps do: dynamic colour (with Geet's own palette as a choice), colours taken from the current song's cover for the player and share sheet, spring motion, emphasized type, shape morphing (the player's cover), wavy progress, the morphing loading indicator, floating toolbars, connected button groups and split buttons.

The Expressive APIs are still experimental, so `material3` is pinned to the 1.5 track in the version catalog and opted in by the compose convention plugin. Motion is tuned for 120 Hz screens (the app asks for the display's highest refresh rate while browsing) and respects "remove animations".
