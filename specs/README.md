# specs/: how this project is built

These pages describe the architecture, the app, its Rime integration, build, testing, conventions,
and known pitfalls. `CLAUDE.md` is the summary. User-visible behavior and settings are documented in
[`docs/`](../docs/README.md).

| Page | Read it before |
|---|---|
| [architecture.md](architecture.md) | changing module boundaries, threads, or the path from key to editor |
| [engine.md](engine.md) | changing Fcitx5-Rime integration, config, or user data |
| [app.md](app.md) | changing the service, keyboard, settings, or persistence |
| [data-and-build.md](data-and-build.md) | changing native builds, packaged assets, dependencies, versions, or CI |
| [testing.md](testing.md) | finishing any change |
| [conventions.md](conventions.md) | writing code, adding a setting, or changing product behavior |
| [pitfalls.md](pitfalls.md) | debugging unexpected behavior or preparing a build |

## The project in five lines

- Youmo IME (`io.github.sraw.youmo`) is an offline Android input method branched from
  fcitx5-android; upstream compatibility is not a constraint.
- Fcitx5 remains the framework. Rime is the only input engine; schemas and dictionaries are
  supplied and maintained by the user.
- `app` contains the Android service and UI; `lib/ime-core` contains pure JVM keyboard decision
  logic, not a text input engine.
- There is one app variant and no speech, self-built pinyin, code-table, or English engine.
- The build packages the Fcitx5/Rime runtime and shared defaults, but no Rime schema or dictionary.

Keep these pages synchronized with code changes. Prefer naming a class over a line number, and
explain the reason behind a rule.
