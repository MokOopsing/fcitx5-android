/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
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
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import splitties.views.imageResource
import timber.log.Timber

@SuppressLint("ViewConstructor")
class TextKeyboard(
    context: Context,
    theme: Theme,
    splitLandscape: Boolean = false
) : BaseKeyboard(context, theme, if (splitLandscape) SplitLayout else Layout) {

    enum class CapsState { None, Once, Lock }

    companion object {
        const val Name = "Text"
        const val SplitName = "TextSplit"

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                AlphabetKey("Q", "手", "1"),
                AlphabetKey("W", "田", "2"),
                AlphabetKey("E", "水", "3"),
                AlphabetKey("R", "口", "4"),
                AlphabetKey("T", "廿", "5"),
                AlphabetKey("Y", "卜", "6"),
                AlphabetKey("U", "山", "7"),
                AlphabetKey("I", "戈", "8"),
                AlphabetKey("O", "人", "9"),
                AlphabetKey("P", "心", "0")
            ),
            listOf(
                AlphabetKey("A", "日", "@"),
                AlphabetKey("S", "尸", "*"),
                AlphabetKey("D", "木", "+"),
                AlphabetKey("F", "火", "-"),
                AlphabetKey("G", "土", "="),
                AlphabetKey("H", "的", "/"),
                AlphabetKey("J", "十", "#"),
                AlphabetKey("K", "大", "("),
                AlphabetKey("L", "中", ")")
            ),
            listOf(
                CapsKey(),
                AlphabetKey("Z", "重", "'"),
                AlphabetKey("X", "止", ":"),
                AlphabetKey("C", "金", "\""),
                AlphabetKey("V", "女", "?"),
                AlphabetKey("B", "月", "!"),
                AlphabetKey("N", "弓", "~"),
                AlphabetKey("M", "一", "\\"),
                BackspaceKey()
            ),
            listOf(
                LayoutSwitchKey("?123", ""),
                CommaKey(0.1f, KeyDef.Appearance.Variant.Alternative),
                LanguageKey(),
                SpaceKey(),
                SymbolKey(".", 0.1f, KeyDef.Appearance.Variant.Alternative),
                ReturnKey()
            )
        )

        private fun spacer(width: Float) = KeyDef(
            Appearance.Text(
                keyCodeString = "",
                displayText = " ",
                textSize = 0f,
                percentWidth = width,
                border = Appearance.Border.Off,
                margin = false,
            ),
            emptySet()
        )

        val SplitLayout: List<List<KeyDef>> = listOf(
            listOf(
                AlphabetKey("Q", "手", "1", percentWidth = 0.075f),
                AlphabetKey("W", "田", "2", percentWidth = 0.075f),
                AlphabetKey("E", "水", "3", percentWidth = 0.075f),
                AlphabetKey("R", "口", "4", percentWidth = 0.075f),
                AlphabetKey("T", "廿", "5", percentWidth = 0.075f),
                spacer(0.25f),
                AlphabetKey("Y", "卜", "6", percentWidth = 0.075f),
                AlphabetKey("U", "山", "7", percentWidth = 0.075f),
                AlphabetKey("I", "戈", "8", percentWidth = 0.075f),
                AlphabetKey("O", "人", "9", percentWidth = 0.075f),
                AlphabetKey("P", "心", "0", percentWidth = 0.075f),
            ),
            listOf(
                spacer(0.0375f),
                AlphabetKey("A", "日", "@", percentWidth = 0.075f),
                AlphabetKey("S", "尸", "*", percentWidth = 0.075f),
                AlphabetKey("D", "木", "+", percentWidth = 0.075f),
                AlphabetKey("F", "火", "-", percentWidth = 0.075f),
                AlphabetKey("G", "土", "=", percentWidth = 0.075f),
                spacer(0.175f),
                AlphabetKey("H", "的", "/", percentWidth = 0.075f),
                AlphabetKey("J", "十", "#", percentWidth = 0.075f),
                AlphabetKey("K", "大", "(", percentWidth = 0.075f),
                AlphabetKey("L", "中", ")", percentWidth = 0.075f),
                spacer(0.0375f),
            ),
            listOf(
                CapsKey(percentWidth = 0.1125f),
                AlphabetKey("Z", "重", "'", percentWidth = 0.075f),
                AlphabetKey("X", "止", ":", percentWidth = 0.075f),
                AlphabetKey("C", "金", "\"", percentWidth = 0.075f),
                AlphabetKey("V", "女", "?", percentWidth = 0.075f),
                spacer(0.175f),
                AlphabetKey("V", "女", "?", percentWidth = 0.075f),
                AlphabetKey("B", "月", "!", percentWidth = 0.075f),
                AlphabetKey("N", "弓", "~", percentWidth = 0.075f),
                AlphabetKey("M", "一", "\\", percentWidth = 0.075f),
                BackspaceKey(percentWidth = 0.1125f),
            ),
            listOf(
                LayoutSwitchKey("?123", percentWidth = 0.15f),
                CommaKey(0.1f, Variant.Alternative),
                spacer(0.15f),
                SpaceKey(0.35f),
                LanguageKey(0.1f),
                ReturnKey(0.15f),
            )
        )
    }

    val caps: ImageKeyView by lazy { findViewById(R.id.button_caps) }
    val backspace: ImageKeyView by lazy { findViewById(R.id.button_backspace) }
    val quickphrase: ImageKeyView by lazy { findViewById(R.id.button_quickphrase) }
    val lang: ImageKeyView by lazy { findViewById(R.id.button_lang) }
    val space: TextKeyView by lazy { findViewById(R.id.button_space) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

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

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
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
        space.mainText.text = spaceLable
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
                when (action.keyboard) {
                    is KeyDef.Popup.Keyboard.Preset -> {
                        val label = action.keyboard.label
                        if (label.length == 1 && label[0].isLetter())
                            action.copy(
                                keyboard = action.keyboard.copy(label = transformAlphabet(label))
                            )
                        else action
                    }
                    is KeyDef.Popup.Keyboard.Explicit -> action
                }
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
        caps.img.apply {
            imageResource = when (capsState) {
                CapsState.None -> R.drawable.ic_capslock_none
                CapsState.Once -> R.drawable.ic_capslock_once
                CapsState.Lock -> R.drawable.ic_capslock_lock
            }
        }
    }

    private fun updateLangSwitchKey(visible: Boolean) {
        lang.visibility = if (visible) View.VISIBLE else View.GONE
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
