# 构建与开发 · Building and development

[中文](#构建与开发) · [English](#building-and-development) · [返回 README](README.md)

## 构建与开发

### 环境

- JDK 17；
- Android SDK Platform 36、Build-Tools 36.1.0；
- Android NDK 28.0.13004108、CMake 3.31.6；
- `extra-cmake-modules` 和 GNU gettext 0.20 或更高版本。

版本以 [Versions.kt](build-logic/convention/src/main/kotlin/Versions.kt) 为准。首次检出后初始化原生子模块：

```shell
git submodule update --init --recursive
```

### 构建

```shell
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

当前只有一个应用变体。构建会编译 Fcitx5 和 Rime 原生插件，不会下载或生成输入方案、词典、语言模型或语音模型。Rime 用户需自行将方案文件放入 Rime 用户数据目录并部署。

正式发布需要签名密钥。在 `~/.gradle/gradle.properties` 中设置：

```properties
signKeyFile=/path/to/release.p12
signKeyAlias=...
signKeyPwd=...
```

也可使用 `SIGN_KEY_FILE`、`SIGN_KEY_ALIAS`、`SIGN_KEY_PWD` 环境变量。未配置密钥时生成未签名 APK。

### 测试与检查

| 内容 | 命令 |
|---|---|
| ime-core 单元测试、Android API 检查和覆盖率门槛 | `./gradlew :lib:ime-core:check` |
| 应用 JVM 单元测试 | `./gradlew :app:testDebugUnitTest` |
| 静态分析 | `./gradlew detekt` |
| 模拟器原生集成测试 | `./gradlew :app:connectedDebugAndroidTest` |

ime-core 检查只需要 JDK；应用 JVM 测试还需要 Android SDK。模拟器测试另外需要 NDK、CMake 和 Android 模拟器。

### 代码位置

| 位置 | 内容 |
|---|---|
| `lib/ime-core` | 纯 JVM 的输入决策和键盘状态逻辑 |
| `app` | Android 界面、输入法服务、设置、持久化和 JNI 桥接 |
| `plugin/rime` | Fcitx5-Rime 插件及其原生构建配置 |
| `lib/fcitx5` | Fcitx5 框架和预构建原生依赖 |

变更用户可见行为或设置时，同时更新[使用说明](docs/README.md)。架构、约定和测试流程见[开发者规格](specs/README.md)。

---

## Building and development

### Requirements

- JDK 17;
- Android SDK Platform 36 and Build-Tools 36.1.0;
- Android NDK 28.0.13004108 and CMake 3.31.6;
- `extra-cmake-modules` and GNU gettext 0.20 or newer.

Versions are in [Versions.kt](build-logic/convention/src/main/kotlin/Versions.kt). Initialize the native submodules after checkout:

```shell
git submodule update --init --recursive
```

### Build

```shell
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

There is one app variant. The build compiles Fcitx5 and the native Rime addon; it does not download or generate schemas, dictionaries, language models, or speech models. Install Rime schema files in the Rime user data directory and deploy them yourself.

Release builds need a signing key. Set the following in `~/.gradle/gradle.properties`:

```properties
signKeyFile=/path/to/release.p12
signKeyAlias=...
signKeyPwd=...
```

Alternatively use the `SIGN_KEY_FILE`, `SIGN_KEY_ALIAS`, and `SIGN_KEY_PWD` environment variables. Without a key, the build produces an unsigned APK.

### Tests and checks

| What | Command |
|---|---|
| ime-core tests, Android API check, and coverage floors | `./gradlew :lib:ime-core:check` |
| App JVM unit tests | `./gradlew :app:testDebugUnitTest` |
| Static analysis | `./gradlew detekt` |
| Native emulator integration tests | `./gradlew :app:connectedDebugAndroidTest` |

ime-core checks need only a JDK; app JVM tests also need the Android SDK. Emulator tests additionally need the NDK, CMake, and an Android emulator.

### Code layout

| Path | Contents |
|---|---|
| `lib/ime-core` | Plain JVM input decisions and keyboard state logic |
| `app` | Android UI, input method service, settings, persistence, and JNI bridge |
| `plugin/rime` | Fcitx5-Rime addon and native build configuration |
| `lib/fcitx5` | Fcitx5 framework and prebuilt native dependencies |

Update the [user guide](docs/README.md) with user-visible behavior or settings changes. See the [developer specs](specs/README.md) for architecture, conventions, and tests.
