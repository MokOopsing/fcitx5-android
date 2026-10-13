# Pitfalls

Things that have cost time before; keep these in mind when debugging.

## Build and interfaces

### Kotlin incremental compilation and delegated interfaces

After adding or removing a method from `FcitxAPI` (or another interface implemented with `by`),
incremental Kotlin compilation can leave delegating classes stale. If a runtime
`AbstractMethodError` appears after an interface change, clean the affected app Kotlin outputs and
rebuild; do not treat a successful incremental build as proof that all delegates are current.

### detekt is syntax-focused

Type resolution does not work in this setup, so detekt is not a substitute for Kotlin compilation
or tests.

## Android behavior

### Cursor reports can lag

Editors may report selection changes late (for example a WebView or long document). Use
`EditingSession`/`CursorTracker` predictions rather than computing the next edit from a stale
selection callback. Deletions use code points so one key press removes a whole emoji.

### `EditorInfo` changes behavior

Password, URL, email, search, no-suggestions, and no-personalized-learning flags affect the keyboard
(`EditorTraits`, `FieldPolicy`, `AddressKeys`, and the return key action). Test password and address
fields as well as ordinary text fields.

### Restore requires restarting Fcitx

Fcitx holds config and user-data files open. Replacing them under a running process can leave it
writing to stale files; stop and restart the Fcitx instance around restore.

### Rime schemas are not bundled

A clean install can expose a healthy Rime engine while offering no schema or candidates. Verify the
native availability test separately from a manual typing test with a user-installed schema.

## Device testing

- `adb shell input text` bypasses the input method. Tap or inject key events to test typing.
- `uiautomator dump` does not see the keyboard window, popups, or candidate menus. Use measured
  coordinates, screenshots, and the field's text.
- `am force-stop` can make Android switch input methods. Re-select the IME before testing again.
- Debug builds have a `.debug` package suffix and a different IME id from release.
- `connectedAndroidTest` may uninstall the test APK afterward; instrumentation restarts the app
  process on startup.
- Coordinates measured on one screen size, orientation, or theme may be wrong on another.

## Tests

- A class that reads `AppPrefs` in its initializer needs `AppPrefs.init(...)` in a JVM test.
- `unitTests.isReturnDefaultValues = false` is intentional; framework-dependent behavior belongs
  under Robolectric.
