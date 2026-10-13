# Testing

A change is done when the focused tests and checks pass, and user-visible behavior has been tried
where the environment permits. Report environmental blockers rather than claiming unrun tests passed.

## Test layers

| Layer | Run | Scope |
|---|---|---|
| ime-core | `./gradlew :lib:ime-core:check` | Pure JVM decisions, Android API surface and coverage floors |
| App JVM | `./gradlew :app:testDebugUnitTest` | Android glue and preference/data behavior (Robolectric where needed) |
| Build logic | `./gradlew -p build-logic test` | Convention/build metadata plugins |
| Static analysis | `./gradlew detekt` | Kotlin syntax rules and frozen baselines |
| Instrumented | `./gradlew :app:connectedDebugAndroidTest` | Native Rime availability on a device/emulator |

Unit tests need an Android SDK for the app module. `ime-core` checks need only a JDK. Instrumented
tests additionally need the Android NDK, CMake, and a running emulator/device.

## Test conventions

- New decision logic gets a test in ime-core where practical.
- Prefer `FakeEditor` and `FakeFcitxAPI` over mocks. The latter is not a native engine simulation.
- `unitTests.isReturnDefaultValues = false` is deliberate: reaching an `android.jar` stub from a
  JVM test must fail loudly.
- Test a user-visible behavior and a regression case, not just the shape of the implementation.
- Kover floors apply to ime-core; do not lower or regenerate them to hide missing coverage.

## Device checks

For keyboard or Rime changes, test a clean install with a user-installed schema, candidate selection,
Rime ASCII/schema switching, password fields, URL/email fields, rotation, and an in-place upgrade.
The shipped app contains no schemas, so verify schema installation separately from engine availability.

`RimeAvailabilityTest` checks the native Fcitx instance exposes and enables only `rime`; it does not
verify a particular schema or dictionary. Those are intentionally supplied by the user.

## CI

JVM tests run on pushes and pull requests. The native emulator workflow runs weekly, on demand, and
on pull requests labelled `emulator`.
