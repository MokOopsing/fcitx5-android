package org.fcitx.fcitx5.android.link

/**
 * 语音覆盖层 UI 桥接：
 * - CommonKeyActionListener 在显示覆盖层时注册回调；
 * - AsrkbSpeechClient 在回调线程里调用这些回调以更新/关闭覆盖层。
 */
object VoiceOverlayUiBridge {
    @Volatile var onAmplitude: ((Float) -> Unit)? = null
    @Volatile var onDone: (() -> Unit)? = null

    fun clear() {
        onAmplitude = null
        onDone = null
    }
}

