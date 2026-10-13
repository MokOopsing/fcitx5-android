# Architecture

## Runtime

The Android app hosts one Fcitx5 instance in the process. `FcitxDaemon` owns its lifecycle and
serializes Fcitx calls on the Fcitx thread. `FcitxInputMethodService` connects Android's
`InputConnection` to the native Android frontend through JNI.

Fcitx5 owns input-method composition and candidates. The only registered application input engine is
Fcitx5-Rime (`rime`), built as a native addon. Rime schemas, dictionaries, composition, and learning
are not implemented in `lib/ime-core`.

The keyboard is an Android view tree under an `InputView` and `InputWindowManager`. It sends keys
through Fcitx, while common UI such as the candidate bar, symbols, emoji, clipboard, and text-editing
panel is independent of the chosen Rime schema.

## Modules

| Module | Responsibility |
|---|---|
| `app` | Android service, UI, preferences, data managers, JNI and native app build |
| `lib/ime-core` | Android-free keyboard policies and state machines |
| `lib/fcitx5` | Fcitx5 framework build and prebuilt native dependencies |
| `plugin/rime` | Fcitx5-Rime source integration and Rime-related assets |
| `codegen` | Generated Android/UI support code |

The former Chinese-addons Gradle module, speech modules, and self-built engine/data tools are not
included in the app build.

## Thread and lifetime rules

Fcitx API calls are suspend functions and run on the Fcitx thread. Do not block the main thread with
`runBlocking`; cached reads and `eventFlow` are the exceptions exposed by `FcitxConnection`.

View work belongs to the active `InputView` scope. A theme or layout change can replace that view,
so view-bound jobs must not outlive it. Service-level work belongs to the service scope.

## Input path

1. Android delivers a key event to the active keyboard view.
2. Shared key policy handles keyboard gestures and forwards text/key events to Fcitx.
3. Fcitx5-Rime processes composition according to the active user-installed schema.
4. Candidate and status updates return through the JNI event bridge.
5. The service commits selected text through the current `InputConnection`.

Sensitive editor fields continue to use the app's field policies; they do not alter or replace Rime's
schema logic.
