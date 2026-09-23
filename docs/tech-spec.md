# V92.4 implementation

- UI-owned PlaybackHighlightState carries song identity, palette transition and playback clock. Drawables are independently attached renderers of that state.
- List reorder restores transient source state before data mutation and normalizes geometry at OnPreDraw, after the actual new layout. Drop cleanup must not replay stale transforms over rebound rows.
- VinylStackGeometry maps continuous queue offsets to position, scale, opacity and depth. Five retained VinylRecordViews exchange roles after commit, preserving the incoming record and rotation.
- Keep Gradle 9.5.0, AGP 9.2.1, Java 17, compile/target SDK 36, min SDK 26, Media3 1.10.1.
- Android references: https://developer.android.com/reference/android/view/ViewGroup and https://developer.android.com/build/releases/agp-9-2-0-release-notes.
