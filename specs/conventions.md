# Conventions

## Product rules

- **Rime only.** Do not add another input engine, speech feature, schema download, or bundled user
  dictionary without an explicit product decision.
- **Offline.** No network permission, cloud feature, analytics, or crash upload.
- **Shared keyboard remains engine-neutral.** Keyboard appearance and editing utilities belong to
  the app; composition and schema-specific behavior belong to Rime/Fcitx.
- **Few settings, good defaults.** Group settings by what the user wants to adjust. Do not expose
  Fcitx internals without a user need.
- **No upstream compatibility requirement.** This fork may restructure rather than preserve
  upstream APIs.
- **Licenses.** Everything in the APK must be redistributable. Remove license entries for content
  that is no longer packaged.
- **Privacy.** Do not read text from sensitive editor fields. App backups are unencrypted; exclude
  clipboard entries that an app marked sensitive.

## Code

- Put testable keyboard decisions in pure ime-core classes with values passed in; keep Android
  state and preferences in the app layer.
- Follow surrounding Kotlin style: small functions, `val` over `var`, sealed states/actions, and no
  new DI framework. Do not use `AppPrefs` from ime-core or `runBlocking` on the main thread.
- Keep untrusted imports and backups bounded and report failures explicitly.
- New code must not add detekt findings. Do not regenerate a baseline to hide one.
- Comments explain why, especially an observed bug or Android quirk; avoid comments that restate
  the code.

## User-facing strings

Describe what a user gets, not the implementation. Keep English, Simplified Chinese, and
Traditional Chinese in sync; other locales may fall back to English.

## Documentation

- `README.md`: product scope and releases.
- `BUILDING.md`: build and development instructions.
- `docs/`: user-facing behavior and settings; update it with behavior changes.
- `specs/`: architecture and contributor guidance; keep it accurate.
- `PRIVACY.md`: data and permissions; update it when either changes.
