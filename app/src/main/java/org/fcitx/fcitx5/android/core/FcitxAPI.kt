/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.core

import kotlinx.coroutines.flow.SharedFlow

/**
 * API of fcitx that hides lifecycle stuffs from [Fcitx]
 *
 * Functions can be safely used in any coroutine,
 * as the underlying operation is always dispatched in fcitx thread.
 */
interface FcitxAPI {

    /**
     * Subscribe this flow to receive event sent from fcitx
     */
    val eventFlow: SharedFlow<FcitxEvent<*>>

    val isReady: Boolean

    val inputMethodEntryCached: InputMethodEntry

    val statusAreaActionsCached: Array<Action>

    val clientPreeditCached: FormattedText

    val inputPanelCached: FcitxEvent.InputPanelEvent.Data

    fun setLogRule(verbose: Boolean)

    fun translate(str: String, domain: String = "fcitx5"): String

    suspend fun save()

    suspend fun reloadConfig()

    suspend fun sendKey(key: String, states: UInt = 0u, code: Int = 0, up: Boolean = false, timestamp: Int = -1)

    suspend fun sendKey(c: Char, states: UInt = 0u, code: Int = 0, up: Boolean = false, timestamp: Int = -1)

    suspend fun sendKey(sym: Int, states: UInt = 0u, code: Int = 0, up: Boolean = false, timestamp: Int = -1)

    suspend fun sendKey(sym: KeySym, states: KeyStates, code: Int = 0, up: Boolean = false, timestamp: Int = -1)

    /**
     * Picks the [idx]th candidate if it still shows [text], what the user tapped: a list refined
     * since may hold another word there, and false is returned. A null [text] (a key's pick) is
     * not checked.
     */
    suspend fun select(idx: Int, text: String? = null): Boolean
    suspend fun simulateShift(): Boolean
    suspend fun isEmpty(): Boolean
    suspend fun reset()
    suspend fun moveCursor(position: Int)

    suspend fun availableIme(): Array<InputMethodEntry>
    suspend fun enabledIme(): Array<InputMethodEntry>

    suspend fun setEnabledIme(array: Array<String>)

    suspend fun toggleIme()
    suspend fun activateIme(ime: String)

    suspend fun currentIme(): InputMethodEntry

    suspend fun getGlobalConfig(): RawConfig

    suspend fun setGlobalConfig(config: RawConfig)

    suspend fun getAddonSubConfig(addon: String, path: String): RawConfig

    suspend fun setAddonSubConfig(addon: String, path: String, config: RawConfig = RawConfig())

    suspend fun getImConfig(key: String): RawConfig

    suspend fun setImConfig(key: String, config: RawConfig)

    suspend fun triggerQuickPhrase()
    suspend fun triggerUnicode()

    suspend fun focus(focus: Boolean = true)
    suspend fun focusOutIn()
    suspend fun activate(uid: Int, pkgName: String)
    suspend fun deactivate(uid: Int)
    suspend fun setCapFlags(flags: CapabilityFlags)

    suspend fun statusArea(): Array<Action>

    suspend fun activateAction(id: Int)

    suspend fun getCandidates(offset: Int, limit: Int): Array<CandidateWord>

    suspend fun getCandidateActions(idx: Int): Array<CandidateAction>

    /** Does nothing once the [idx]th candidate no longer shows [text], the one long-pressed: see [select]. */
    suspend fun triggerCandidateAction(idx: Int, actionIdx: Int, text: String)

    suspend fun setCandidatePagingMode(mode: Int)
    suspend fun offsetCandidatePage(delta: Int)

    suspend fun triggerCandidateListTabAction(id: Int)

}