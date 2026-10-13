/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input

import android.content.Intent
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The test editor keeps a Return key event a newline in its multi-line
 * field. TextView offers that key to the editor-action listener as IME_NULL first, on key down and
 * on key up; the listener took it, so the field got no newline and [TypingHostActivity.actions]
 * read [0, 0].
 */
class TypingHostActivityTest {

    @Test
    fun returnKeyEventsInTheMultiLineFieldTypeANewlineAndAreNoAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var text = ""
        var actions = emptyList<Int>()
        ActivityScenario.launch<TypingHostActivity>(Intent(context, TypingHostActivity::class.java)).use { scenario ->
            scenario.onActivity {
                it.field.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                it.field.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                text = it.field.text.toString()
                actions = it.actions.toList()
            }
        }
        assertEquals("\n", text)
        assertEquals(emptyList<Int>(), actions)
    }

    /** IME_ACTION_UNSPECIFIED is 0, as IME_NULL is: sent as an editor action, it is still recorded as one. */
    @Test
    fun anUnspecifiedEditorActionIsAnAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var actions = emptyList<Int>()
        ActivityScenario.launch<TypingHostActivity>(Intent(context, TypingHostActivity::class.java)).use { scenario ->
            scenario.onActivity {
                it.field.onEditorAction(EditorInfo.IME_ACTION_UNSPECIFIED)
                actions = it.actions.toList()
            }
        }
        assertEquals(listOf(EditorInfo.IME_ACTION_UNSPECIFIED), actions)
    }
}
