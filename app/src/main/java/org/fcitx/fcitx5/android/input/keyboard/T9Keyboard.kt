/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.Keep
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.EngineBridge
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.engine.host.Keyboard
import org.fcitx.fcitx5.android.engine.pinyin.T9Segmenter
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyAppearance
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupAction
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.core.add
import splitties.views.existingOrNewId

/**
 * Pinyin on nine keys (九键), laid out as 搜狗 does: a column on the left offering what the first
 * digits not yet taken may be (the engine's [EngineBridge.t9] syllables), or punctuation while
 * nothing is typed; 1 is the separator (分词); backspace, start over (重输) and 0 on the right;
 * symbols, numbers, space, the language switch and return below. Swiping a digit key types the
 * digit itself; 1 is the engine's to read, a separator while something is typed, else the digit.
 *
 * A syllable is sent to the engine as a character of [Keyboard.SYLLABLES], saying which of
 * which list it is ([Keyboard.syllableKey]).
 *
 * A digit key pressed again soon is its next letter ([MultiTap], as [AppPrefs.Keyboard.t9MultiTap]
 * sets), sent as a character of [Keyboard.LETTERS] in place of the last ([Keyboard.letterKey]);
 * its preview shows the letter. Its long press offers the digit and its letters, a letter chosen
 * typed as itself after what is typed.
 */
@SuppressLint("ViewConstructor")
class T9Keyboard(
    context: Context,
    theme: Theme,
) : BaseKeyboard(context, theme, Layout) {

    companion object {
        const val Name = "T9"

        // the column's share of the width, and of the right one
        private const val SIDE = 0.16f
        private const val RIGHT = 0.18f
        private const val DIGIT = (1f - SIDE - RIGHT) / 3

        /** Shown in the column while nothing is typed, committed as they are. */
        private val PUNCTUATION = listOf("，", "。", "？", "！", "…", "、", "：", "~", "@")

        /** How many items of the column show at once. */
        private const val SHOWN = 4

        // nothing typed, or nothing of it left to take: the column offers punctuation
        private fun EngineBridge.T9State.offersSyllables() = composing && syllables.isNotEmpty()

        /** The column's items for [state]: its syllables while typing, else punctuation. */
        internal fun labels(state: EngineBridge.T9State): List<String> =
            if (state.offersSyllables()) state.syllables else PUNCTUATION

        /** What picking item [i] of [state]'s column types. */
        internal fun pick(state: EngineBridge.T9State, i: Int): KeyAction =
            if (state.offersSyllables()) {
                // with the list's id: shown before a key typed since replaced it, the pick is dropped
                KeyAction.SymAction(KeySym(UNICODE + Keyboard.syllableKey(i, state.id).code), KeyStates.Virtual)
            } else {
                KeyAction.CommitAction(PUNCTUATION[i])
            }

        private fun digitKey(digit: Char, letters: String) = KeyDef(
            KeyDef.Appearance.AltText(displayText = letters, altText = digit.toString(), textSize = 20f, percentWidth = DIGIT),
            setOf(
                KeyDef.Behavior.Press(KeyAction.FcitxKeyAction(digit.toString())),
                KeyDef.Behavior.Swipe(KeyAction.CommitAction(digit.toString())),
            ),
            arrayOf(
                KeyDef.Popup.AltPreview(letters, digit.toString()),
                KeyDef.Popup.Keyboard.Explicit(arrayOf(digit.toString()) + letters.lowercase().map { it.toString() }),
            ),
        )

        /**
         * What [action] from [source] types: a digit key's press [MultiTap] reads at [time], a
         * letter or digit of its long press; anything else resets [tap] and types as it is.
         */
        internal fun typed(action: KeyAction, source: KeyActionListener.Source, tap: MultiTap, time: Long): KeyAction {
            val key = (action as? KeyAction.FcitxKeyAction)?.act?.singleOrNull()
            if (key == null || key !in '2'..'9' && key !in 'a'..'z') {
                tap.reset()
                return action
            }
            if (source == KeyActionListener.Source.Popup) {
                tap.reset()
                // the digit itself, as the swipe types it, not a key
                return if (key in '2'..'9') KeyAction.CommitAction(key.toString()) else letter(key, again = false)
            }
            if (key !in '2'..'9') return action
            val letter = tap.press(key, T9Segmenter.letters(key), time) ?: return action
            return letter(letter, again = true)
        }

        private fun letter(c: Char, again: Boolean) =
            KeyAction.SymAction(KeySym(UNICODE + Keyboard.letterKey(c, again)), KeyStates.Virtual)

        // the column is drawn over these placeholders, so they are never touched
        private fun spacer() = KeyDef(
            KeyDef.Appearance.Text("", 16f, percentWidth = SIDE, border = KeyDef.Appearance.Border.Off, margin = false),
            emptySet(),
        )

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                spacer(),
                KeyDef(
                    KeyDef.Appearance.AltText(displayText = "分词", altText = "1", textSize = 16f, percentWidth = DIGIT),
                    setOf(
                        KeyDef.Behavior.Press(KeyAction.FcitxKeyAction("1")),
                        KeyDef.Behavior.Swipe(KeyAction.CommitAction("1")),
                    ),
                ),
                digitKey('2', "ABC"),
                digitKey('3', "DEF"),
                BackspaceKey(RIGHT),
            ),
            listOf(
                spacer(),
                digitKey('4', "GHI"),
                digitKey('5', "JKL"),
                digitKey('6', "MNO"),
                KeyDef(
                    KeyDef.Appearance.Text("重输", 16f, percentWidth = RIGHT, variant = KeyDef.Appearance.Variant.Alternative),
                    setOf(KeyDef.Behavior.Press(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_Escape)))),
                ),
            ),
            listOf(
                spacer(),
                digitKey('7', "PQRS"),
                digitKey('8', "TUV"),
                digitKey('9', "WXYZ"),
                KeyDef(
                    KeyDef.Appearance.Text("0", 20f, percentWidth = RIGHT, variant = KeyDef.Appearance.Variant.Alternative),
                    setOf(KeyDef.Behavior.Press(KeyAction.FcitxKeyAction("0"))),
                ),
            ),
            listOf(
                LayoutSwitchKey("符", PickerWindow.Key.Symbol.name, SIDE),
                LayoutSwitchKey("123", NumberKeyboard.Name, 0.14f),
                SpaceKey(),
                LanguageKey(),
                ReturnKey(RIGHT),
            ),
        )
    }

    private val space: TextKeyView by lazy { findViewById(R.id.button_space) }
    private val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }
    private val lang: ImageKeyView by lazy { findViewById(R.id.button_lang) }

    private val showLangSwitchKey = AppPrefs.getInstance().keyboard.showLangSwitchKey

    private val multiTapPref = AppPrefs.getInstance().keyboard.t9MultiTap
    private var multiTap = MultiTap(multiTapPref.getValue().timeout)

    @Keep
    private val multiTapListener = ManagedPreference.OnChangeListener<T9MultiTap> { _, v ->
        multiTap = MultiTap(v.timeout)
    }

    @Keep
    private val showLangSwitchKeyListener = ManagedPreference.OnChangeListener<Boolean> { _, v ->
        lang.isVisible = v
    }

    private val items = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val column = object : ScrollView(context) {
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            itemHeight = (h - paddingTop - paddingBottom) / SHOWN
            for (i in 0 until items.childCount) items.getChildAt(i).layoutParams.height = itemHeight
            post { items.requestLayout() }
        }
    }.apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        val prefs = ThemeManager.prefs
        val h = dp(prefs.keyHorizontalMargin.getValue())
        val v = dp(prefs.keyVerticalMargin.getValue())
        background = insetRadiusDrawable(h, v, dp(prefs.keyRadius.getValue().toFloat()), theme.altKeyBackgroundColor)
        setPadding(h, v, h, v)
        addView(items)
    }

    private var itemHeight = 0

    /** What the column shows, read by its items when picked. */
    private var columnState = EngineBridge.T9State()

    init {
        add(column, lParams {
            topToTop = keyRows[0].existingOrNewId
            bottomToBottom = keyRows[2].existingOrNewId
            leftOfParent()
            width = 0
            height = 0
            matchConstraintPercentWidth = SIDE
        })
        // under the column, out of reach: TalkBack would stop on them, a second finger under the vivo workaround hit them
        keyRows.take(3).forEach { row ->
            row.getChildAt(0).apply {
                isEnabled = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
        lang.isVisible = showLangSwitchKey.getValue()
        showLangSwitchKey.registerOnChangeListener(showLangSwitchKeyListener)
        multiTapPref.registerOnChangeListener(multiTapListener)
        show(EngineBridge.T9State())
    }

    private var scope: CoroutineScope? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(Dispatchers.Main.immediate + Job()).also { s ->
            s.launch { EngineBridge.t9.collectLatest(::show) }
        }
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    private val columnHit = Rect()

    // the vivo workaround takes every touch for a key: the column's are the column's
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        column.getHitRect(columnHit)
        if (columnHit.contains(ev.x.toInt(), ev.y.toInt())) return false
        return super.onInterceptTouchEvent(ev)
    }

    /** The column for [state]: its syllables while typing, else punctuation; its items kept, made only when more are needed. */
    internal fun show(state: EngineBridge.T9State) {
        // a press on the list replaced is cancelled, as removing its items did: its pick would take what now sits there
        MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_CANCEL, 0f, 0f, 0).also {
            items.dispatchTouchEvent(it)
            it.recycle()
        }
        // and so is a click released but not run yet (View posts it on ACTION_UP), as a removed item's was
        items.cancelPendingInputEvents()
        columnState = state
        // nothing typed (a candidate picked on the bar, which is not this keyboard's): the next
        // press is a key of its own
        if (!state.composing) multiTap.reset()
        val labels = labels(state)
        while (items.childCount < labels.size) items.addView(item(items.childCount))
        for (i in 0 until items.childCount) {
            val view = items.getChildAt(i) as TextView
            view.isVisible = i < labels.size
            if (view.isVisible) view.text = labels[i]
        }
        column.scrollTo(0, 0)
    }

    private fun item(i: Int) = TextView(context).apply {
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(theme.altKeyTextColor)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, itemHeight.takeIf { it > 0 } ?: dp(40))
        background = pressHighlight()
        isClickable = true
        setOnClickListener {
            InputFeedbacks.hapticFeedback(this)
            InputFeedbacks.soundEffect(InputFeedbacks.SoundEffect.Standard)
            onAction(pick(columnState, i))
        }
    }

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        // a digit key's press as its preview counted it, when it went down
        val key = (action as? KeyAction.FcitxKeyAction)?.act?.singleOrNull()
        val time = pressed?.takeIf { it.first == key }?.second ?: SystemClock.uptimeMillis()
        pressed = null
        super.onAction(typed(action, source, multiTap, time), source)
    }

    // the letters a digit key's preview shows in place of its own (GHI), while it is held
    private var previewing: Pair<String, String>? = null

    // the digit key gone down last, and when: the press counts from then, as its preview did
    private var pressed: Pair<Char, Long>? = null

    // a digit key's preview shows the letter pressing it again now types
    override fun onPopupAction(action: PopupAction) {
        val shown = when (action) {
            is PopupAction.PreviewAction -> {
                // a digit key's preview is its letters, upper case
                val digit = ('2'..'9').firstOrNull { T9Segmenter.letters(it).uppercase() == action.content }
                val now = SystemClock.uptimeMillis()
                pressed = digit?.let { it to now }
                val letter = digit?.let { multiTap.peek(it, T9Segmenter.letters(it), now) }
                previewing = letter?.let { action.content to it.uppercase() }
                previewing?.let { action.copy(content = it.second) }
            }
            // back from the digit a swipe shows
            is PopupAction.PreviewUpdateAction -> previewing?.takeIf { it.first == action.content }?.let { action.copy(content = it.second) }
            else -> null
        }
        super.onPopupAction(shown ?: action)
    }

    private fun pressHighlight() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(theme.keyPressHighlightColor))
    }

    override fun onReturnDrawableUpdate(appearance: ReturnKeyAppearance) {
        `return`.showReturnAction(appearance)
    }

    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        showInputMethodName(space, ime)
    }
}

/** fcitx's key for a Unicode character: this plus its code point. */
private const val UNICODE = 0x01000000
