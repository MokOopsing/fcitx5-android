# 知了猴输入法 · Youmo IME

[中文](#知了猴输入法) · [English](#youmo-rime) · [使用说明](docs/README.md) · [下载](https://github.com/MokOopsing/fcitx5-android/actions)

## 知了猴输入法

知了猴输入法是一个基于 Fcitx5 的 Android 输入法，**只保留 Rime 输入引擎**。键盘、主题、剪贴板、符号、表情和文本编辑等共用功能仍可使用。应用不申请网络权限，输入内容和 Rime 用户数据留在设备本地。

**不随安装包提供输入方案或词典。** 首次使用需自行安装 Rime 方案并部署，详见[Rime 输入说明](docs/rime.md)。

本项目由 [youmo-ime](https://github.com/Sraw/youmo-ime) 分叉，保留 [Fcitx5](https://github.com/fcitx/fcitx5) 框架及 [Rime](https://rime.im) 引擎。

### 下载与使用

安装包在 action构建记录(https://github.com/MokOopsing/fcitx5-android/actions)。

### 构建与开发

见[构建与开发](BUILDING.md)及[开发者规格](specs/README.md)。应用仅包含单一构建变体；Rime 方案、词典等用户数据不参与构建，也不写入安装包。

### 许可

应用代码遵循 LGPL-2.1-or-later。Rime、Fcitx5 和随包原生组件的许可详见应用内第三方许可页面及 `app/licenses/`。

---

## Youmo IME

Youmo IME is an Android input method built on Fcitx5. **Rime is the only input engine.** The shared keyboard, themes, clipboard, symbols, emoji, and text-editing tools remain available. The app requests no network permission; typed text and Rime user data stay on device.

**No Rime schema or dictionary is bundled.** Install a Rime schema and deploy it before typing; see the [Rime guide](docs/rime.md).

This project branched from [youmo-ime](https://github.com/Sraw/youmo-ime), retaining the [Fcitx5](https://github.com/fcitx/fcitx5) framework and [Rime](https://rime.im) engine. 

### Download and use

Get the APK from [GitHub Action](https://github.com/MokOopsing/fcitx5-android/actions)

### Build and development

See [BUILDING.md](BUILDING.md) and the [developer specs](specs/README.md). There is one app build variant. Rime schemas, dictionaries, and other user data are not built or packaged.

### License

The app code is LGPL-2.1-or-later. See the in-app third-party licenses and `app/licenses/` for Fcitx5, Rime, and native dependencies.
