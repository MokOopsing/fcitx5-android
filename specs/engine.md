# Rime integration

Rime is the only input engine shipped by the app. The application does not implement pinyin,
shuangpin, T9, code-table, English, dictionary, or sentence-ranking engines.

## Native addon

`app/src/main/cpp/CMakeLists.txt` builds the Fcitx5-Rime addon from
`plugin/rime/src/main/cpp/fcitx5-rime` and links the prebuilt librime dependencies. Fcitx registers
the engine under the unique name `rime` (`RIME_INPUT_METHOD`).

The app builds only the Rime input engine, so available and enabled methods are reported directly by
Fcitx without an app-side allowlist. Once Fcitx is ready, `Fcitx` enables Rime so an upgraded
installation with an old engine list remains usable. Do not reintroduce an internal Fcitx
engine-cycling action: there is only one application engine.

## User data and schemas

The Rime user directory is `${externalFilesDir}/data/rime`, configured as the engine's
`UserDataDir`. It is not an app asset. The settings page offers a document-browser entry to that
directory.

The native build installs shared Rime prelude configuration only. It intentionally does not package
schemas, dictionaries, `essay.txt`, or a default Chinese input scheme. Consequently, a clean install
has no selectable Rime schema until the user installs one and deploys it. Do not add an implicit
schema download or restore bundled schemas without changing this product decision and its docs.

`UserDataManager` backs up the app's external files, including `data/rime`. It excludes the legacy
top-level `external/engine` tree when exporting and discards that tree when importing old archives;
Rime data under `external/data/rime` must remain intact.

## Fcitx configuration

`FcitxAPI` reads and writes Fcitx/Rime configuration asynchronously on the Fcitx thread.
`ConfigDescriptor` describes known external Rime options; `UserDataDir` is rendered as the custom
open-directory preference. Keep Rime-specific behavior in the native engine/configuration rather
than recreating it in ime-core.

The keyboard can call the Rime status actions to toggle ASCII mode or advance the schema. Missing
Rime status/actions are a no-op; there is no fallback to cycling the removed engines.
