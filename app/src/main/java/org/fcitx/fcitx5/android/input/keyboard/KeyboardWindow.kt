/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.transition.Slide
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyAppearance
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.editing.toEditorTraits
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

    // Create each keyboard only when it is first shown.
    private val makers: Map<String, () -> BaseKeyboard> = mapOf(
        TextKeyboard.Name to { TextKeyboard(context, theme) },
        NumberKeyboard.Name to { NumberKeyboard(context, theme) },
    )
    private val keyboards = hashMapOf<String, BaseKeyboard>()
    private var currentKeyboardName = ""
    private var lastSymbolType: String by AppPrefs.getInstance().internal.lastSymbolLayout

    private val currentKeyboard: BaseKeyboard?
        get() = makers[currentKeyboardName]?.let { keyboards.getOrPut(currentKeyboardName, it) }

    private val spaceKeys = SpaceKeyInputMethod()

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
            (it as? TextKeyboard)?.address = address
            spaceKeys.attach(it) {
                keyboardView.apply { add(it, lParams(matchParent, matchParent)) }
                it.onAttach()
                it.onReturnDrawableUpdate(returnKeyDrawable.appearance)
            }
        }
    }

    private fun showLayout(target: String) {
        detachCurrentLayout()
        attachLayout(target)
        if (windowManager.isAttached(this)) {
            notifyBarLayoutChanged()
        }
    }

    private val lettersLayout by lazy {
        LettersLayout(makers.keys, TextKeyboard.Name, PickerWindow.Key.Symbol.name)
    }

    fun switchLayout(to: String, remember: Boolean = true) {
        // read now, as before: the memory is what it was when the key was pressed
        val lastSymbol = lastSymbolType
        ContextCompat.getMainExecutor(context).execute {
            val outcome = lettersLayout.switchTo(to, lastSymbol, currentKeyboardName, remember)
            outcome.remember?.let { lastSymbolType = it }
            when (val target = outcome.target) {
                LayoutSwitchPolicy.Target.Unchanged -> {}
                is LayoutSwitchPolicy.Target.Keyboard -> showLayout(target.name)
                LayoutSwitchPolicy.Target.SymbolPicker ->
                    windowManager.attachWindow(PickerWindow.Key.Symbol)
            }
        }
    }

    // the field's, for the letters to show what an address is made of: told each layout as it attaches
    private var address = AddressKeys.NONE

    override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags) {
        address = AddressKeys.of(info.toEditorTraits())
        (currentKeyboard as? TextKeyboard)?.address = address
        val targetLayout = when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER -> NumberKeyboard.Name
            InputType.TYPE_CLASS_PHONE -> NumberKeyboard.Name
            else -> TextKeyboard.Name
        }
        switchLayout(targetLayout, remember = false)
    }

    override fun onImeUpdate(ime: InputMethodEntry) {
        spaceKeys.current = ime
        val shown = lettersLayout.inputMethodChanged(TextKeyboard.Name, currentKeyboardName)
        // a layout shown anew is told the input method as it is attached
        if (shown != null) showLayout(shown) else currentKeyboard?.let(spaceKeys::show)
    }

    override fun onPunctuationUpdate(mapping: Map<String, String>) {
        currentKeyboard?.onPunctuationUpdate(mapping)
    }

    override fun onReturnKeyDrawableUpdate(appearance: ReturnKeyAppearance) {
        currentKeyboard?.onReturnDrawableUpdate(appearance)
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
}

/**
 * The input method on the layouts' space keys. A layout announces a change of it only from the name
 * it showed itself (BaseKeyboard.showInputMethodName), and the user may last have seen another
 * layout's: one being shown is first told that one, before it has a parent, as
 * View.announceForAccessibility says nothing then. The change is announced once, on the layout
 * shown, and never the input method being left, nor a window's first.
 */
internal class SpaceKeyInputMethod {

    // as onImeUpdate last told it: asking fcitx would block the main thread on the fcitx one
    var current: InputMethodEntry? = null

    // what the user last saw on a space key (NumberKeyboard has none)
    private var seen: InputMethodEntry? = null

    /** Shows [keyboard], which [adding] puts in the window, the [current] input method. */
    fun attach(keyboard: BaseKeyboard, adding: () -> Unit) {
        seen?.let(keyboard::onInputMethodUpdate)
        adding()
        show(keyboard)
    }

    /** Shows [keyboard], in the window, the [current] input method. */
    fun show(keyboard: BaseKeyboard) {
        val ime = current ?: return
        keyboard.onInputMethodUpdate(ime)
        if (keyboard !is NumberKeyboard) seen = ime
    }
}