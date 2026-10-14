# youmo-ime, 知了猴输入法

This project branched from fcitx5-android and is not intended to merge upstream. Free to restructure.

This page is the summary. The long form is [`specs/`](specs/README.md): architecture, the app, Rime integration, build, testing, conventions, and pitfalls. User-facing behavior and settings live in [`docs/`](docs/README.md); keep them accurate when behavior changes.

## Where code goes

- `lib/ime-core` — plain JVM module with keyboard decision logic and state machines. No Android dependency. Keep logic testable; preserve the animal-sniffer API check and Kover floors.
- `app` — Android UI, `InputMethodService`, preferences, file-backed managers, and JNI-facing `core/`. Keep it thin and pass values into ime-core policies.
- `plugin/rime` — Fcitx5-Rime integration and native build configuration. Rime schemas and dictionaries are user-provided; do not bundle input-method datasets.
- Pattern to follow (see `PendingInputPolicy`, `LayoutSwitchPolicy`, `BackspaceSwipeBehavior`): pure class, preferences passed as arguments, no `AppPrefs` or `Context`.
- Deliberately not done: an `ImeHost` abstraction over the service, injecting `AppPrefs` everywhere, or changing the DI framework.

## Commands

| What | Command |
|---|---|
| Core logic tests + API-level check + coverage floors | `./gradlew :lib:ime-core:check` |
| App JVM tests | `./gradlew :app:testDebugUnitTest` |
| Static analysis | `./gradlew detekt` |
| Accept current findings after a deliberate batch (run alone, not with `detekt`) | `./gradlew :<module>:detektBaseline` |
| Native emulator tests | `./gradlew :app:connectedDebugAndroidTest` |

Unit tests need no submodules, NDK, or CMake; app tests require an Android SDK. Instrumented tests need the native build and an emulator. CI runs them weekly and on PRs labelled `emulator`.

## Conventions

- New decision logic gets a unit test in the same change. Prefer fakes over mockk: `FakeEditor` (ime-core testFixtures) and `FakeFcitxAPI` (app tests). The latter cannot model the JNI engine; keep it dumb.
- `unitTests.isReturnDefaultValues = false` on purpose: a JVM test reaching an `android.jar` stub must fail loudly. Framework-dependent tests run under Robolectric.
- `FcitxAPI.getImConfig`, `getAddonSubConfig` and friends are `suspend` and run on the Fcitx thread. Do not add `runBlocking` on the main thread; `FcitxConnection.runImmediately` is for cached reads and `eventFlow` only.
- Detekt findings are frozen in `config/detekt/baseline-*.xml`. New code must not add to them; do not regenerate baselines just to silence a finding.
- Comments explain why, especially an observed bug or Android quirk; match the surrounding density.
