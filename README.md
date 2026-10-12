# 幽默输入法 · Youmo IME

[中文](#幽默输入法) · [English](#youmo-ime) · [使用说明](docs/README.md)

---

## 幽默输入法

一个在手机上**完全离线**运行的 Android 中文输入法。它基于 [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android)，
保留 [Fcitx5](https://github.com/fcitx/fcitx5) 作为输入法框架，拼音引擎是自己用 Kotlin 写的，取代了原来的 libime，
并且在 n-gram 解码器之上加了两个小型神经网络模型给整句候选重新排序。

> 这是一个独立的项目，与 Fcitx5 和 fcitx5-android 官方无关；它从 fcitx5-android 分出，保留了完整的历史，但不会合并回上游。问题请在本仓库反馈，不要报给上游。

### 特点

- **自己的拼音引擎**：全拼、双拼（8 种方案）、九键，按整句解码；简拼、半截输入、相邻键打错的纠正；按前文给出联想。
  九键的快速重按同时按"几次按键"和"连按到的字母"两种读法解码，由语言模型选；按键字母可以自定义。
- **新词**：近几年的新词由本项目自己从网页里找出、逐个校对后编进词典（显眼包、破防、红温……）。
- **英文键盘**：自己的词表和语言模型，自动纠错（含常见拼错）、补全、下一个词的预测，会学习你打的词；拼音里也能直接打出 iPhone、APP 这类英文词。
- **神经网络重排**：
  - 每次按键，4M 参数的模型（4.5 MB）在解码器的前两个整句里重排；
  - 停顿时，25M 参数的模型（25 MB）在前五个整句里精排；
  - 两个模型都会读输入框里光标前的文字作为上下文（密码框等敏感输入框不读）。
- **越用越顺手**：用户词库、调频；可以导入词库（.txt、.dict、搜狗 .scel），支持自定义短语；长按候选可以删除或置顶词组。
- **码表输入**：五笔、仓颉、自然码、二笔、五笔拼音，用自己的码表引擎，和拼音共用词库。
- **离线语音输入**（`voice` 版）：按住说话，中英混说。
- **不联网**：不申请网络权限，全部在手机上运行，输入的内容不会离开手机（[隐私政策](PRIVACY.md)）。
- **其他**：Material 3 界面、可自定义长按字符、主题、剪贴板管理（复制链接时自动去掉跟踪参数）、表情与符号、实体键盘下的悬浮候选栏。

### 效果

首选正确率（%）。"解码器"指只用 n-gram 解码器，"+ 模型"指加上 4M 和 25M 两个模型。

| 测试集 | 解码器 | + 模型 |
|---|---|---|
| 主集（日常与书面句子，548 句） | 88.3 | 90.9 |
| 网页（近期网页，模型没读过，3642 句） | 72.9 | 76.5 |
| 聊天（留出的一半，1064 句） | 68.7 | 72.7 |
| 对话（590 句） | 67.6 | 71.2 |
| 新词（405 句） | 79.5 | 80.0 |

2026-10-12 在提交 747991fb 上测，和 App 默认一样开着相邻键纠错。九键打主集，加模型后首选 88.8%。
测法见 `lib/ime-eval`（`release-report.sh`）。

### 下载

安装包在 [Releases](https://github.com/Sraw/youmo-ime/releases)：`text` 是纯文字版，`voice` 多了离线语音输入。也可以按下文自行构建。

包名是 `io.github.sraw.youmo`，可以和上游的 Fcitx5（`org.fcitx.fcitx5.android`）同时安装。
从本分支改名前的版本换过来，可以在旧版的“高级”设置里导出用户数据，再到新版里导入：设置、主题、剪贴板会带过来；
旧版的备份不含引擎学到的词和词频，这部分要重新积累（新版的备份包含它们）。

### 使用说明

怎么用、各个键和手势、每个设置是什么意思，都在[使用说明](docs/README.md)里。应用设置首页右上角的问号也能打开它。

### 数据与许可

- 代码：LGPL-2.1-or-later，与上游相同。
- 词典、拼音语言模型、码表：来自 [libime](https://github.com/fcitx/libime) 发布的文本源（LGPL-2.1-or-later），构建时编译成本引擎的格式。
  拼音语言模型里混入了 [FineWeb-2](https://huggingface.co/datasets/HuggingFaceFW/fineweb-2) 中文类聊天网页（ODC-By 1.0）和
  [Common Crawl](https://commoncrawl.org) 一次抓取里中文类聊天网页（遵守其使用条款）的 n-gram。混好的模型放在本仓库的
  `engine-data-*` release 里，由 `lib/ime-dict-tool/engine-data.sh` 在发版前生成。
  近几年的新词由本项目自己从该次抓取的中文网页里找出、逐个校对（`lexicon/`），打成词包发布在 `words-*` release 里，构建时编进词典的单独一层。
- 英文词表与语言模型：从 [FineWeb](https://huggingface.co/datasets/HuggingFaceFW/fineweb) sample-10BT 的英文网页（ODC-By 1.0）统计，
  发布在 `english-data-*` release 里，构建时编译。
- 笔画表（拼音里以 `u` 开头按笔画找字）：来自 [rime-stroke](https://github.com/rime/rime-stroke)（LGPL-3.0-only），构建时编进安装包。
- 句子模型：本项目自己训练（Apache-2.0）。先在 FineWeb-2 中文网页上训练语言模型，再用 Qwen3.5-9B-Base（Apache-2.0）给候选列表打分做蒸馏。
  文件发布在 [sentence-models-20261001](https://github.com/Sraw/youmo-ime/releases/tag/sentence-models-20261001)。
- 语音识别（只在 `voice` 版里）：[X-ASR](https://github.com/Gilgamesh-J/X-ASR) 中英模型（Apache-2.0），由 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)（Apache-2.0）和 [silero VAD](https://github.com/snakers4/silero-vad)（MIT）运行。
- 安装包里的词典、语言模型和句子模型都没有用仅限科研用途的数据训练。`lib/ime-eval` 里的聊天与对话评测集取自 [LCCC](https://github.com/thu-coai/CDial-GPT)（其说明要求仅用于科研），只用来评测和选参数：语言模型的混合权重和 `Predictor.USER_WEIGHT` 是按包含这些集的分数选的。

### 构建

需要：

- Android SDK Platform 36 与 Build-Tools 36.1.0；
- Android NDK 28.0.13004108 与 CMake 3.31.6；
- [extra-cmake-modules](https://github.com/KDE/extra-cmake-modules)、GNU gettext ≥ 0.20。

版本以 [Versions.kt](build-logic/convention/src/main/kotlin/Versions.kt) 为准。

```shell
git clone https://github.com/Sraw/youmo-ime.git
cd youmo-ime
git submodule update --init --recursive
sudo apt install extra-cmake-modules gettext   # Debian/Ubuntu；Arch：pacman -S extra-cmake-modules；macOS：brew install extra-cmake-modules gettext
./gradlew :app:assembleTextDebug   # 带语音：:app:assembleVoiceDebug
```

版本有两个：`text`（纯文字，默认）或 `voice`（离线语音输入，多一个录音权限和约 180 MB 的语音模型），都不申请网络权限。
`./gradlew :app:assembleRelease` 两个都打。

构建会自动下载引擎数据和句子模型，并按 SHA-256 校验：

- 第一次下载 libime 的数据源和本仓库 release 里混好的语言模型与新词包，约 400 MB（解开后语言模型占 1 GB 磁盘）；
- 编译拼音数据大约要 2 分钟、5 GB 内存。

正式版需要签名。把以下三项写进 `~/.gradle/gradle.properties`（也可以用环境变量 `SIGN_KEY_FILE`、`SIGN_KEY_ALIAS`、`SIGN_KEY_PWD`；
密钥文件也可以用 base64 写在 `signKeyBase64` / `SIGN_KEY_BASE64` 里），然后运行 `./gradlew :app:assembleRelease`。
没有配置密钥，或者密钥文件路径不存在时，构建不会报错，而是生成未签名的安装包。

```properties
signKeyFile=/path/to/release.p12
signKeyAlias=...
signKeyPwd=...
```

### 开发

改代码之前先读 [specs/](specs/README.md)：架构、引擎、应用、数据与构建、测试、约定和踩过的坑（英文）。

| 位置 | 内容 |
|---|---|
| `lib/ime-core` | 纯 JVM 模块，不依赖 Android：拼音与码表引擎、解码器、句子模型推理，以及各种决策逻辑 |
| `app` | Android 部分：界面、`InputMethodService`、设置、与 Fcitx5 的 JNI 桥 |
| `lib/ime-eval` | 评测：用测试集模拟打字，算首选正确率等指标 |
| `lib/ime-dict-tool` | 从文本源构建引擎数据（只在构建时用，不进安装包） |

| 做什么 | 命令 |
|---|---|
| 核心逻辑测试、API 级别检查、覆盖率门槛 | `./gradlew :lib:ime-core:check` |
| 应用的 JVM 测试 | `./gradlew :app:testTextDebugUnitTest` |
| 静态检查 | `./gradlew detekt` |

### 致谢

[fcitx5-android](https://github.com/fcitx5-android/fcitx5-android)、[Fcitx5](https://github.com/fcitx/fcitx5)、[libime](https://github.com/fcitx/libime)、
[fcitx5-chinese-addons](https://github.com/fcitx/fcitx5-chinese-addons)、[FineWeb](https://huggingface.co/datasets/HuggingFaceFW/fineweb)、[FineWeb-2](https://huggingface.co/datasets/HuggingFaceFW/fineweb-2)、[Common Crawl](https://commoncrawl.org)、[Qwen](https://github.com/QwenLM)。

---

## Youmo IME

*幽默 (yōumò) means humor.*

An Android Chinese input method that runs **entirely offline** on the phone. It is built on
[fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) and keeps [Fcitx5](https://github.com/fcitx/fcitx5)
as the input method framework. The pinyin engine is its own, written in Kotlin in place of libime, and two
small neural models rerank the n-gram decoder's whole-sentence candidates.

> This is an independent project, not affiliated with Fcitx5 or fcitx5-android. It branched off fcitx5-android,
> keeping the full history, and will not be merged upstream. Please report issues here, not upstream.

### Features

- **Its own pinyin engine**: full pinyin, shuangpin (8 schemes) and nine keys, decoded as whole sentences; abbreviations, half-typed syllables and slipped neighbouring keys; next-word suggestions from what you have typed.
  On nine keys a key pressed again quickly is decoded both as that many presses and as the letter it came to, and the language model picks; each key's letters can be changed.
- **New words**: the words of the last few years, found in web pages and curated one by one by this project, are in the dictionary.
- **English keyboard**: its own words and language model, autocorrection (common misspellings too), completions and next-word predictions, learning the words you type; Latin words such as iPhone and APP type straight from pinyin.
- **Neural reranking**:
  - at every key, a 4M-parameter model (4.5 MB) reorders the decoder's best two sentences;
  - when you pause, a 25M-parameter model (25 MB) reorders the best five;
  - both read the text before the cursor in the input field as context (never in password or other sensitive fields).
- **Learns as you type**: user words and frequencies, imported dictionaries (.txt, .dict, Sogou .scel), custom phrases; long-press a candidate to remove or pin it.
- **Code tables**: Wubi, Cangjie, Ziranma, Erbi and Wubi-Pinyin, on its own table engine, sharing the pinyin lexicon.
- **Offline voice input** (the `voice` build): hold to talk, Chinese and English mixed.
- **No network**: it asks for no network permission and runs entirely on the phone; what you type never leaves it ([privacy policy](PRIVACY.md)).
- **More**: Material 3 interface, configurable long-press characters, themes, clipboard management (tracking parameters stripped from copied links), emoji and symbols, a floating candidate bar with a physical keyboard.

### Accuracy

Top-1 accuracy (%). "Decoder" is the n-gram decoder alone; "+ models" adds the 4M and 25M models.

| Set | Decoder | + models |
|---|---|---|
| Main (everyday and written sentences, 548) | 88.3 | 90.9 |
| Web (recent pages the models never read, 3,642) | 72.9 | 76.5 |
| Chat (the held-out half, 1,064) | 68.7 | 72.7 |
| Dialog (590) | 67.6 | 71.2 |
| New words (405) | 79.5 | 80.0 |

Measured on 2026-10-12 at commit 747991fb, with neighbouring-key correction on as in the app by default. On nine keys the main set is 88.8% first with the models.
See `lib/ime-eval` (`release-report.sh`) for how these are measured.

### Download

Builds are on [Releases](https://github.com/Sraw/youmo-ime/releases): `text` is text only, `voice` adds offline voice input. Or build it yourself as below.

The package name is `io.github.sraw.youmo`, so it installs alongside upstream Fcitx5 (`org.fcitx.fcitx5.android`).
To move from this fork's builds before the renaming, export your user data in the old app's Advanced settings and import it in the new one.
Settings, themes and the clipboard come along. The old app's backups leave out the words and frequencies the engine has learnt, so those start again (the new app's backups include them).

### Using it

The user's manual, in Chinese, is in [docs/](docs/README.md): every key, gesture and setting. The question mark at the top right of the app's settings opens it.

### Data and licences

- Code: LGPL-2.1-or-later, as upstream.
- Dictionary, pinyin language model and code tables: compiled at build time from the text sources published by [libime](https://github.com/fcitx/libime) (LGPL-2.1-or-later).
  The pinyin language model also mixes in n-grams from chat-like Chinese pages of [FineWeb-2](https://huggingface.co/datasets/HuggingFaceFW/fineweb-2) (ODC-By 1.0)
  and from the chat-like Chinese pages of a [Common Crawl](https://commoncrawl.org) crawl (under its terms of use). The mixed model is in
  this repository's `engine-data-*` releases, made before a release by `lib/ime-dict-tool/engine-data.sh`.
  The words of the last few years are the project's own: found in that crawl's Chinese pages and curated one by one (`lexicon/`), packed and
  published as `words-*` releases, and compiled into a layer of the dictionary at build time.
- English words and language model: counted from the English pages of [FineWeb](https://huggingface.co/datasets/HuggingFaceFW/fineweb) sample-10BT (ODC-By 1.0),
  published as `english-data-*` releases and compiled at build time.
- Stroke table (pinyin's lookup by strokes after `u`): from [rime-stroke](https://github.com/rime/rime-stroke) (LGPL-3.0-only), compiled into the APK at build time.
- Sentence models: this project's own (Apache-2.0). A language model is first trained on FineWeb-2's Chinese pages, then distilled from Qwen3.5-9B-Base's (Apache-2.0) scores of candidate lists.
  The files are published at [sentence-models-20261001](https://github.com/Sraw/youmo-ime/releases/tag/sentence-models-20261001).
- Speech recognition (in the `voice` build only): the [X-ASR](https://github.com/Gilgamesh-J/X-ASR) zh-en model (Apache-2.0), run with [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache-2.0) and [silero VAD](https://github.com/snakers4/silero-vad) (MIT).
- The dictionary, language model and sentence models in the app are not trained on research-only data. The chat and dialog evaluation sets in `lib/ime-eval` come from [LCCC](https://github.com/thu-coai/CDial-GPT), whose README asks for research use only, and are used only to evaluate and to choose parameters: the language model's mix weight and `Predictor.USER_WEIGHT` were tuned on scores that include them.

### Building

Requirements:

- Android SDK Platform 36 and Build-Tools 36.1.0;
- Android NDK 28.0.13004108 and CMake 3.31.6;
- [extra-cmake-modules](https://github.com/KDE/extra-cmake-modules) and GNU gettext ≥ 0.20.

[Versions.kt](build-logic/convention/src/main/kotlin/Versions.kt) has the current versions.

```shell
git clone https://github.com/Sraw/youmo-ime.git
cd youmo-ime
git submodule update --init --recursive
sudo apt install extra-cmake-modules gettext   # Debian/Ubuntu; Arch: pacman -S extra-cmake-modules; macOS: brew install extra-cmake-modules gettext
./gradlew :app:assembleTextDebug   # with voice: :app:assembleVoiceDebug
```

There are two builds, neither with a network permission: `text`, the default, or `voice`, offline voice input, with the microphone
permission and a ~180 MB speech model. `./gradlew :app:assembleRelease` makes both.

The build downloads the engine data and the sentence models and checks their SHA-256:

- the first build downloads libime's data sources, and the mixed language model and the new-word pack from this
  repository's releases, about 400 MB (the model takes 1 GB of disk unpacked);
- compiling the pinyin data takes about 2 minutes and 5 GB of memory.

A release build needs a signing key. Put these three in `~/.gradle/gradle.properties` (or the environment variables `SIGN_KEY_FILE`, `SIGN_KEY_ALIAS` and `SIGN_KEY_PWD`;
the key file can also be given base64-encoded as `signKeyBase64` / `SIGN_KEY_BASE64`), then run `./gradlew :app:assembleRelease`.
Without a key, or with a key file path that does not exist, the build does not fail: it makes an unsigned APK.

```properties
signKeyFile=/path/to/release.p12
signKeyAlias=...
signKeyPwd=...
```

### Development

Before changing the code, read [specs/](specs/README.md): architecture, the engine, the app, data and build, testing, conventions, pitfalls.

| Where | What |
|---|---|
| `lib/ime-core` | Plain JVM, no Android: the pinyin and table engines, the decoder, sentence-model inference, and the decision logic |
| `app` | The Android part: views, the `InputMethodService`, preferences, the JNI bridge to Fcitx5 |
| `lib/ime-eval` | Evaluation: types the test sets and measures top-1 accuracy and more |
| `lib/ime-dict-tool` | Builds the engine data from text sources (build time only, not in the APK) |

| To | Run |
|---|---|
| Test the core logic, check the API level and coverage floors | `./gradlew :lib:ime-core:check` |
| Run the app's JVM tests | `./gradlew :app:testTextDebugUnitTest` |
| Run static analysis | `./gradlew detekt` |

### Thanks

[fcitx5-android](https://github.com/fcitx5-android/fcitx5-android), [Fcitx5](https://github.com/fcitx/fcitx5), [libime](https://github.com/fcitx/libime),
[fcitx5-chinese-addons](https://github.com/fcitx/fcitx5-chinese-addons), [FineWeb](https://huggingface.co/datasets/HuggingFaceFW/fineweb), [FineWeb-2](https://huggingface.co/datasets/HuggingFaceFW/fineweb-2), [Common Crawl](https://commoncrawl.org), [Qwen](https://github.com/QwenLM).
