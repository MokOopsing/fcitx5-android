# Data, build, release

## Building

| What | Command |
|---|---|
| Debug APK | `./gradlew :app:assembleDebug` |
| Release APK | `./gradlew :app:assembleRelease` |
| One ABI only | add `-PbuildABI=arm64-v8a` or `-PbuildABI=x86_64` |
| Core checks | `./gradlew :lib:ime-core:check` |
| App JVM tests | `./gradlew :app:testDebugUnitTest` |
| Static analysis | `./gradlew detekt` |
| Emulator tests | `./gradlew :app:connectedDebugAndroidTest` |

Needs JDK 17, Android SDK Platform 36 / Build-Tools 36.1.0, NDK 28.0.13004108, CMake 3.31.6,
`extra-cmake-modules`, and gettext. Versions are in
`build-logic/convention/src/main/kotlin/Versions.kt`. See [BUILDING.md](../BUILDING.md) for setup
and signing.

## Packaged assets

The app packages Fcitx5 and its native dependencies plus shared Rime prelude configuration. No
Rime schema, dictionary, or generated engine data is fetched or compiled by the app build. Rime user
data belongs to the user and lives in
`${externalFilesDir}/data/rime`.

`DataDescriptorPlugin` fingerprints packaged files under app assets so `DataManager` can synchronize
runtime data. Keep user data outside those bundled assets.

## Native code

`app/src/main/cpp` builds the Android frontend, addon loader, notification bridge, JNI library, and
the Fcitx5-Rime plugin. `lib/fcitx5` builds Fcitx5 using the prebuilt native dependencies. The app
does not link the removed `androidengine` or include the Chinese addons module.

When changing JNI or `FcitxAPI`, inspect both sides of the interface and run the app tests. The
instrumented Rime availability test additionally requires a native build and emulator.

## Licenses

Everything distributed in the APK must be licensed for redistribution. Update `app/licenses/` when
the packaged Fcitx5/Rime native dependencies change. Do not keep license entries for removed
language corpora or speech models.

## Versions and releases

- Version name comes from annotated `v*` tags, falling back to `Versions.baseVersionName`.
- `versionCode` includes an ABI suffix; raise `Versions.baseVersionCode` for a release.
- Release APKs are built for the supported release ABI, signed outside the repository, installed
  over the previous version, and uploaded with `SHA256SUMS.txt`.
- Package `io.github.sraw.youmo` (debug has a `.debug` suffix).

## CI

| Workflow | Runs |
|---|---|
| `unit_test.yml` | ime-core checks, app JVM tests, build-logic tests, detekt |
| `pull_request.yml` | Release build for the single app variant |
| `emulator_test.yml` | Rime native availability test on an API 35 emulator, weekly and on PRs labelled `emulator` |
