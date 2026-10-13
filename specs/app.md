# The app (`app/`)

The app is Android glue: it hosts Fcitx5-Rime, presents shared keyboard tools, and manages Android
preferences and files. Input-method composition belongs to Rime.

## Packages

| Package | Responsibility |
|---|---|
| `core/` | Fcitx lifecycle, JNI API, events, input-method names and Android subtype registration |
| `daemon/` | Shared Fcitx instance and connections |
| `input/` | `InputMethodService`, keyboard views, candidate bar, symbols, emoji, clipboard, editing |
| `data/` | Preferences, themes, clipboard database, user-data import/export and bundled-data sync |
| `ui/` | Setup flow, settings pages, search, developer/about screens |
| `utils/` | Small shared helpers |
| `src/main/cpp/` | JNI, Android frontend, addon loading/notification and the Rime plugin build |

The settings input-method list is immutable and contains Rime only. Its configuration page is built
from Fcitx configuration descriptors. Common keyboard settings remain in managed app preferences.

## Input view and keys

`FcitxInputMethodService` owns the editing session and the active `InputView`. The view contains
keyboard and utility windows managed by `InputWindowManager`; replacing a view cancels its scoped
work. Shared key gestures forward events to Fcitx, while composition and candidate generation belong
to Rime.

The shared keyboard still provides full, numeric, symbol and emoji layouts, key popups, cursor
gestures, clipboard, and text editing. It does not contain an alternate input-engine layout such as
T9.

## Settings

App preferences are declared in `data/prefs` and rendered by managed preference fragments. Rime
engine options come from Fcitx config and are rendered by `PreferenceScreenFactory` using
`ConfigDescriptor`. Do not add a settings page for a removed engine or dictionary manager.

Searchable managed preferences are indexed from their category declarations. Hand-built settings
pages must register their entries in `SettingsIndex` and have a test.

## Persistence

`UserDataManager` exports preferences, supported databases, and app external files. Clipboard
entries marked sensitive are removed from the backup copy. The legacy `external/engine` directory
is excluded; `external/data/rime` is Rime's user data and is preserved. Backups are unencrypted.

`DataManager` synchronizes the packaged Fcitx/Rime shared runtime assets. It must not create or
download user schemas or dictionaries.

## Adding a setting

1. Prefer a sound default over adding a setting.
2. For keyboard behavior, pass values into a pure ime-core policy and test it there.
3. For a Rime option, use the Fcitx configuration and its descriptor rather than app-side shadow
   state.
4. Add localized strings in English, Simplified Chinese, and Traditional Chinese.
5. Update `SettingsIndex` if the page is hand-built, add a test, and describe it in `docs/settings.md`.
