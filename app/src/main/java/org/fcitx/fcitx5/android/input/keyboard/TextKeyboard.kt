/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.annotation.Keep
import androidx.core.view.allViews
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.popup.PopupAction
import org.fcitx.fcitx5.android.input.keyboard.KeyLayoutParam
import splitties.views.imageResource
import timber.log.Timber

@SuppressLint("ViewConstructor")
class TextKeyboard(
    context: Context,
    theme: Theme
) : BaseKeyboardFlex(context, theme, Layout) {

    enum class CapsState { None, Once, Lock }

    companion object {
        const val Name = "Text"

        val Layout: List<KeyDef> = listOf(
            // 第一行（row = 0）
            CapsKey(layoutParam = KeyLayoutParam(0, 0)),
            SymbolKey("!", 0.1f, KeyDef.Appearance.Variant.Alternative, layoutParam = KeyLayoutParam(0, 1)),
            AlphabetKey("Q", "手", "1", layoutParam = KeyLayoutParam(0, 2)),
            AlphabetKey("W", "田", "2", layoutParam = KeyLayoutParam(0, 3)),
            AlphabetKey("C", "金", "\"", layoutParam = KeyLayoutParam(0, 4)),
            AlphabetKey("J", "十", "#", layoutParam = KeyLayoutParam(0, 5)),
            SymbolKey("?", 0.1f, KeyDef.Appearance.Variant.Alternative, layoutParam = KeyLayoutParam(0, 6)),
            BackspaceKey(layoutParam = KeyLayoutParam(0, 7)),

            // 第二行（row = 1）
            LanguageKey(layoutParam = KeyLayoutParam(1, 0)),
            AlphabetKey("Y", "卜", "6", layoutParam = KeyLayoutParam(1, 1)),
            AlphabetKey("O", "人", "9", layoutParam = KeyLayoutParam(1, 2)),
            AlphabetKey("H", "的", "/", layoutParam = KeyLayoutParam(1, 3)),
            AlphabetKey("E", "水", "3", layoutParam = KeyLayoutParam(1, 4)),
            AlphabetKey("V", "女", "?", layoutParam = KeyLayoutParam(1, 5)),
            AlphabetKey("U", "山", "7", layoutParam = KeyLayoutParam(1, 6)),
            LanguageKey(layoutParam = KeyLayoutParam(1, 7)),

            // 第三行（row = 2）
            // Space 跨两行
            SpaceKey(layoutParam = KeyLayoutParam(2, 0, rowSpan = 2, colSpan = 1)),
            AlphabetKey("X", "止", ":", layoutParam = KeyLayoutParam(2, 1)),
            AlphabetKey("R", "口", "4", layoutParam = KeyLayoutParam(2, 2)),
            AlphabetKey("T", "廿", "5", layoutParam = KeyLayoutParam(2, 3)),
            AlphabetKey("A", "日", "@", layoutParam = KeyLayoutParam(2, 4)),
            AlphabetKey("L", "中", ")", layoutParam = KeyLayoutParam(2, 5)),
            AlphabetKey("B", "月", "!", layoutParam = KeyLayoutParam(2, 6)),
            SpaceKeySecond(layoutParam = KeyLayoutParam(2, 7, rowSpan = 2, colSpan = 1)),

            // 第四行（row = 3）
            // 第一列已被上方Space占用
            AlphabetKey("F", "火", "-", layoutParam = KeyLayoutParam(3, 1)),
            AlphabetKey("S", "尸", "*", layoutParam = KeyLayoutParam(3, 2)),
            AlphabetKey("I", "戈", "8", layoutParam = KeyLayoutParam(3, 3)),
            AlphabetKey("N", "弓", "~", layoutParam = KeyLayoutParam(3, 4)),
            AlphabetKey("D", "木", "+", layoutParam = KeyLayoutParam(3, 5)),
            AlphabetKey("Z", "重", "'", layoutParam = KeyLayoutParam(3, 6)),
            // 第8列已被上方Space占用

            // 第五行（row = 4）
            LayoutSwitchKey("?123", "", layoutParam = KeyLayoutParam(4, 0)),
            CommaKey(0.1f, KeyDef.Appearance.Variant.Alternative, layoutParam = KeyLayoutParam(4, 1)),
            AlphabetKey("P", "心", "0", layoutParam = KeyLayoutParam(4, 2)),
            AlphabetKey("M", "一", "\\", layoutParam = KeyLayoutParam(4, 3)),
            AlphabetKey("G", "土", "=", layoutParam = KeyLayoutParam(4, 4)),
            AlphabetKey("K", "大", "(", layoutParam = KeyLayoutParam(4, 5)),
            SymbolKey(".", 0.1f, KeyDef.Appearance.Variant.Alternative, layoutParam = KeyLayoutParam(4, 6)),
            ReturnKey(layoutParam = KeyLayoutParam(4, 7))
        )
    }

    /*val caps: ImageKeyView by lazy { findViewById(R.id.button_caps) }
    val backspace: ImageKeyView by lazy { findViewById(R.id.button_backspace) }
    val quickphrase: ImageKeyView by lazy { findViewById(R.id.button_quickphrase) }
    val lang: ImageKeyView by lazy { findViewById(R.id.button_lang) }
    val lang2: ImageKeyView by lazy { findViewById(R.id.button_lang_second) }
    val space: TextKeyView by lazy { findViewById(R.id.button_space) }
    val space2: TextKeyView by lazy { findViewById(R.id.button_space_second) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }
    */

    // 原来的 lazy 属性改为可空变量
    private var caps: ImageKeyView? = null
    private var backspace: ImageKeyView? = null
    private var quickphrase: ImageKeyView? = null
    private var lang: ImageKeyView? = null
    private var lang2: ImageKeyView? = null
    private var space: TextKeyView? = null
    private var space2: TextKeyView? = null
    private var `return`: ImageKeyView? = null

    private val showLangSwitchKey = AppPrefs.getInstance().keyboard.showLangSwitchKey

    @Keep
    private val showLangSwitchKeyListener = ManagedPreference.OnChangeListener<Boolean> { _, v ->
        updateLangSwitchKey(v)
    }

    private val keepLettersUppercase by AppPrefs.getInstance().keyboard.keepLettersUppercase

    init {
        updateLangSwitchKey(showLangSwitchKey.getValue())
        showLangSwitchKey.registerOnChangeListener(showLangSwitchKeyListener)
    }

    private val textKeys: List<TextKeyView> by lazy {
        allViews.filterIsInstance(TextKeyView::class.java).toList()
    }

    private var capsState: CapsState = CapsState.None

    private fun transformAlphabet(c: String): String {
        return when (capsState) {
            CapsState.None -> c.lowercase()
            else -> c.uppercase()
        }
    }

    private var punctuationMapping: Map<String, String> = mapOf()
    private fun transformPunctuation(p: String) = punctuationMapping.getOrDefault(p, p)

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        var transformed = action
        when (action) {
            is KeyAction.FcitxKeyAction -> when (source) {
                KeyActionListener.Source.Keyboard -> {
                    when (capsState) {
                        CapsState.None -> {
                            transformed = action.copy(act = action.act.lowercase())
                        }
                        CapsState.Once -> {
                            transformed = action.copy(
                                act = action.act.uppercase(),
                                states = KeyStates(KeyState.Virtual, KeyState.Shift)
                            )
                            switchCapsState()
                        }
                        CapsState.Lock -> {
                            transformed = action.copy(
                                act = action.act.uppercase(),
                                states = KeyStates(KeyState.Virtual, KeyState.CapsLock)
                            )
                        }
                    }
                }
                KeyActionListener.Source.Popup -> {
                    if (capsState == CapsState.Once) {
                        switchCapsState()
                    }
                }
            }
            is KeyAction.CapsAction -> switchCapsState(action.lock)
            else -> {}
        }
        super.onAction(transformed, source)
    }

    override fun onAttach() {
        capsState = CapsState.None
        updateCapsButtonIcon()
        updateAlphabetKeys()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        caps = findViewById(R.id.button_caps)
        backspace = findViewById(R.id.button_backspace)
        quickphrase = findViewById(R.id.button_quickphrase)
        lang = findViewById(R.id.button_lang)
        lang2 = findViewById(R.id.button_lang_second)
        space = findViewById(R.id.button_space)
        space2 = findViewById(R.id.button_space_second)
        `return` = findViewById(R.id.button_return)
    }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`?.img?.imageResource = returnDrawable
    }

    override fun onPunctuationUpdate(mapping: Map<String, String>) {
        punctuationMapping = mapping
        updatePunctuationKeys()
    }
    private val labelNeedIme: String = "ㄓ 魔仓"
    private var curImeName: String = "English"
    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        if (capsState != CapsState.None) {
            switchCapsState()
        }
        val spaceLable = buildString {
            append(ime.displayName)
            ime.subMode.run { name.ifEmpty { label.ifEmpty { null } } }?.let { append(" $it") }
        }
        space?.mainText?.text = spaceLable
        space2?.mainText?.text = spaceLable
        curImeName = spaceLable
        updateAlphabetKeys()
    }

    private fun transformPopupPreview(c: String): String {
        if (c.length != 1) return c
        if (c[0].isLetter()) return transformAlphabet(c)
        return transformPunctuation(c)
    }

    override fun onPopupAction(action: PopupAction) {
        val newAction = when (action) {
            is PopupAction.PreviewAction -> {
                var popLabel = action.labelContent
                if (capsState != CapsState.None || curImeName != labelNeedIme) {
                    popLabel = transformPopupPreview(action.content)
                }
                action.copy(content = popLabel)
            }
            is PopupAction.PreviewUpdateAction -> {
                var popLabel = action.labelContent
                if (capsState != CapsState.None || curImeName != labelNeedIme) {
                    popLabel = transformPopupPreview(action.content)
                }
                action.copy(content = popLabel)
            }
            is PopupAction.ShowKeyboardAction -> {
                val label = action.keyboard.label
                if (label.length == 1 && label[0].isLetter())
                    action.copy(keyboard = KeyDef.Popup.Keyboard(transformAlphabet(label)))
                else action
            }
            else -> action
        }
        super.onPopupAction(newAction)
    }

    private fun switchCapsState(lock: Boolean = false) {
        capsState =
            if (lock) {
                when (capsState) {
                    CapsState.Lock -> CapsState.None
                    else -> CapsState.Lock
                }
            } else {
                when (capsState) {
                    CapsState.None -> CapsState.Once
                    else -> CapsState.None
                }
            }
        updateCapsButtonIcon()
        updateAlphabetKeys()
    }

    private fun updateCapsButtonIcon() {
        caps?.img?.apply {
            imageResource = when (capsState) {
                CapsState.None -> R.drawable.ic_capslock_none
                CapsState.Once -> R.drawable.ic_capslock_once
                CapsState.Lock -> R.drawable.ic_capslock_lock
            }
        }
    }

    private fun updateLangSwitchKey(visible: Boolean) {
        lang?.visibility = if (visible) View.VISIBLE else View.GONE
        lang2?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateAlphabetKeys() {
        textKeys.forEach {
            if (it.def !is KeyDef.Appearance.AltText) return
            if (capsState != CapsState.None || curImeName != labelNeedIme) {
                it.mainText.text = it.def.keyCodeString.let { str ->
                    if (str.length != 1 || !str[0].isLetter()) return@forEach
                    if (keepLettersUppercase) str.uppercase() else transformAlphabet(str)
                }
            } else {
                it.mainText.text = it.def.displayText.ifEmpty { it.def.keyCodeString }
            }
        }
    }

    private fun updatePunctuationKeys() {
        textKeys.forEach {
            if (it is AltTextKeyView) {
                it.def as KeyDef.Appearance.AltText
                it.altText.text = transformPunctuation(it.def.altText)
            } else {
                it.def as KeyDef.Appearance.Text
                it.mainText.text = it.def.displayText.let { str ->
                    if (str[0].run { isLetter() || isWhitespace() }) return@forEach
                    transformPunctuation(str)
                }
            }
        }
    }

}
