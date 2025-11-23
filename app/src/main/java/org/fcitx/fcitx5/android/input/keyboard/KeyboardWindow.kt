/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupActionListener
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.wm.EssentialWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent

class KeyboardWindow : InputWindow.SimpleInputWindow<KeyboardWindow>(), EssentialWindow,
    InputBroadcastReceiver {

    private val service by manager.inputMethodService()
    private val fcitx by manager.fcitx()
    private val theme by manager.theme()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val windowManager: InputWindowManager by manager.must()
    private val popup: PopupComponent by manager.must()
    private val bar: KawaiiBarComponent by manager.must()
    private val returnKeyDrawable: ReturnKeyDrawableComponent by manager.must()

    companion object : EssentialWindow.Key

    override val key: EssentialWindow.Key
        get() = KeyboardWindow

    override fun enterAnimation(lastWindow: InputWindow) = Slide().apply {
        slideEdge = Gravity.BOTTOM
    }.takeIf {
        // disable animation switching between picker
        lastWindow !is PickerWindow
    }

    override fun exitAnimation(nextWindow: InputWindow) =
        super.exitAnimation(nextWindow).takeIf {
            // disable animation switching between picker
            nextWindow !is PickerWindow
        }

    private lateinit var keyboardView: FrameLayout
    private var voiceOverlay: View? = null
    private var voiceWave: org.fcitx.fcitx5.android.input.voice.WaveformView? = null

    private val keyboards: HashMap<String, BaseKeyboard> by lazy {
        hashMapOf(
            TextKeyboard.Name to TextKeyboard(context, theme),
            NumberKeyboard.Name to NumberKeyboard(context, theme)
        )
    }
    private var currentKeyboardName = ""
    private var lastSymbolType: String by AppPrefs.getInstance().internal.lastSymbolLayout

    private val currentKeyboard: BaseKeyboard? get() = keyboards[currentKeyboardName]

    private val keyActionListener = KeyActionListener { it, source ->
        if (it is KeyAction.LayoutSwitchAction) {
            switchLayout(it.act)
        } else {
            commonKeyActionListener.listener.onKeyAction(it, source)
        }
    }

    private val popupActionListener: PopupActionListener by lazy {
        popup.listener
    }

    // This will be called EXACTLY ONCE
    override fun onCreateView(): View {
        keyboardView = context.frameLayout(R.id.keyboard_view)
        attachLayout(TextKeyboard.Name)
        return keyboardView
    }

    private fun detachCurrentLayout() {
        currentKeyboard?.also {
            it.onDetach()
            keyboardView.removeView(it)
            it.keyActionListener = null
            it.popupActionListener = null
        }
    }

    private fun attachLayout(target: String) {
        currentKeyboardName = target
        currentKeyboard?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            keyboardView.apply { add(it, lParams(matchParent, matchParent)) }
            it.onAttach()
            it.onReturnDrawableUpdate(returnKeyDrawable.resourceId)
            it.onInputMethodUpdate(fcitx.runImmediately { inputMethodEntryCached })
        }
    }

    fun switchLayout(to: String, remember: Boolean = true) {
        val target = to.ifEmpty { lastSymbolType }
        ContextCompat.getMainExecutor(service).execute {
            if (keyboards.containsKey(target)) {
                if (remember && target != TextKeyboard.Name) {
                    lastSymbolType = target
                }
                if (target == currentKeyboardName) return@execute
                detachCurrentLayout()
                attachLayout(target)
                if (windowManager.isAttached(this)) {
                    notifyBarLayoutChanged()
                }
            } else {
                if (remember) {
                    lastSymbolType = PickerWindow.Key.Symbol.name
                }
                windowManager.attachWindow(PickerWindow.Key.Symbol)
            }
        }
    }

    override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags) {
        val targetLayout = when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER -> NumberKeyboard.Name
            InputType.TYPE_CLASS_PHONE -> NumberKeyboard.Name
            else -> TextKeyboard.Name
        }
        switchLayout(targetLayout, remember = false)
    }

    override fun onImeUpdate(ime: InputMethodEntry) {
        currentKeyboard?.onInputMethodUpdate(ime)
    }

    override fun onPunctuationUpdate(mapping: Map<String, String>) {
        currentKeyboard?.onPunctuationUpdate(mapping)
    }

    override fun onReturnKeyDrawableUpdate(resourceId: Int) {
        currentKeyboard?.onReturnDrawableUpdate(resourceId)
    }

    override fun onAttached() {
        currentKeyboard?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            it.onAttach()
        }
        notifyBarLayoutChanged()
    }

    override fun onDetached() {
        // 清理语音覆盖层，避免窗口切换后残留
        hideVoiceOverlay()
        currentKeyboard?.let {
            it.onDetach()
            it.keyActionListener = null
            it.popupActionListener = null
        }
        popup.dismissAll()
    }

    // Call this when
    // 1) the keyboard window was newly attached
    // 2) currently keyboard window is attached and switchLayout was used
    private fun notifyBarLayoutChanged() {
        bar.onKeyboardLayoutSwitched(currentKeyboardName == NumberKeyboard.Name)
    }

    /**
     * 显示“语音输入占位”覆盖层：覆盖键盘区域为纯色空白视图。
     * - 不可点击/不可聚焦：不拦截触摸事件，空间格键仍可接收抬起事件以结束会话。
     * - 过渡动画：参考数字布局切换，使用自底向上的 Slide + 100ms 动画。
     */
    fun showVoiceOverlay() {
        if (voiceOverlay != null) return
        // 选取与当前主题一致的背景色：优先使用键盘面色，其次为整体背景色
        val bgColor = when (val t = theme) {
            is org.fcitx.fcitx5.android.data.theme.Theme.Builtin -> t.keyboardColor
            else -> theme.backgroundColor
        }
        val overlay = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(bgColor)
            isClickable = false; isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val wave = org.fcitx.fcitx5.android.input.voice.WaveformView(context).apply {
            // Use a higher-contrast foreground color so the waveform stays visible on light backgrounds
            val candidateColors = listOf(
                theme.genericActiveForegroundColor,
                theme.accentKeyBackgroundColor,
                theme.keyTextColor
            )
            val lineColor = candidateColors.firstOrNull {
                ColorUtils.calculateContrast(it, bgColor) >= 2.5
            } ?: theme.genericActiveForegroundColor
            setWaveformColor(lineColor)
            visibility = View.VISIBLE
            start()
        }
        overlay.addView(wave, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val ts = TransitionSet().apply {
            addTransition(Slide(Gravity.BOTTOM).apply { addTarget(overlay) })
            duration = 100
        }
        TransitionManager.beginDelayedTransition(keyboardView, ts)
        keyboardView.addView(overlay)
        voiceOverlay = overlay
        voiceWave = wave
    }

    /**
     * 隐藏“语音输入占位”覆盖层。
     */
    fun hideVoiceOverlay() {
        val overlay = voiceOverlay ?: return
        try { voiceWave?.stop() } catch (_: Throwable) {}
        val ts = TransitionSet().apply {
            addTransition(Slide(Gravity.BOTTOM).apply { addTarget(overlay) })
            duration = 100
        }
        TransitionManager.beginDelayedTransition(keyboardView, ts)
        keyboardView.removeView(overlay)
        voiceOverlay = null
        voiceWave = null
    }

    fun updateVoiceOverlayAmplitude(amplitude: Float) {
        voiceWave?.updateAmplitude(amplitude)
    }
}
