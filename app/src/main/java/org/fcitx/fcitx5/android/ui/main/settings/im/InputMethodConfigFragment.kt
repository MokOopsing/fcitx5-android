/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.im

import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.core.InputMethodNames

import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.RawConfig
import org.fcitx.fcitx5.android.engine.host.LibimeMigration
import org.fcitx.fcitx5.android.ui.main.MainViewModel
import org.fcitx.fcitx5.android.ui.main.settings.FcitxPreferenceFragment
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.lazyRoute
import java.util.concurrent.atomic.AtomicBoolean

class InputMethodConfigFragment : FcitxPreferenceFragment() {
    val args by lazyRoute<SettingsRoute.InputMethodConfig>()

    private val viewModel: MainViewModel by activityViewModels()

    private val reload = ReloadOnLeave()

    override fun getPageTitle(): String = InputMethodNames.of(requireContext(), args.uniqueName, args.name)

    override fun createScreen(raw: RawConfig, save: () -> Unit): PreferenceScreen =
        InputMethodSettings.of(args.uniqueName)?.let { InputMethodSettings.create(preferenceManager, it, raw["cfg"], save) }
            ?: super.createScreen(raw, save)

    override suspend fun obtainConfig(fcitx: FcitxAPI): RawConfig {
        return fcitx.getImConfig(args.uniqueName)
    }

    override suspend fun saveConfig(fcitx: FcitxAPI, newConfig: RawConfig) {
        fcitx.setImConfig(args.uniqueName, newConfig)
        reload.saved(fcitx, args.uniqueName)
    }

    override fun onStart() {
        super.onStart()
        reload.shown()
    }

    override fun onStop() {
        val fcitx = viewModel.fcitx
        // left now, not once fcitx is ready: by then onStart may have shown the page again
        reload.leaving()
        // the connection checked now, in onStop, while the page has it; run once fcitx is ready, not dropped
        fcitx.lifecycleScope.launch(Dispatchers.Main.immediate) {
            fcitx.runOnReady { reload.left(this) }
        }
        super.onStop()
    }
}

/**
 * Whether [entry] is a table the user imported: one of the engine's that it does not list itself,
 * whose options are its own file (`table/<im>.conf`), not the engine's config.
 */
internal fun isImportedTable(entry: InputMethodEntry): Boolean =
    entry.addon == LibimeMigration.ENGINE_ADDON && entry.uniqueName !in InputMethodSettings.BUILT_IN

/**
 * The engine reads an imported table's options only as it loads the table: loaded again once its
 * page is left, they apply. Not on each option changed, as a load drops every session and closes
 * the table's learning store.
 */
internal class ReloadOnLeave {

    private val pending = AtomicBoolean(false)

    // only Back and Up wait for a save: by Home or another page one can end after the page was left
    private val away = AtomicBoolean(false)

    /** After [uniqueName]'s options were saved; the engine loaded again at once if the page was left meanwhile. */
    suspend fun saved(fcitx: FcitxAPI, uniqueName: String) {
        if (fcitx.availableIme().none { it.uniqueName == uniqueName && isImportedTable(it) }) return
        // set before away is read: unless this sees the page left, left() runs after and finds it
        pending.set(true)
        if (away.get()) reloadIfPending(fcitx)
    }

    /** When the page is shown, again or not: saves wait for it to be left once more. */
    fun shown() = away.set(false)

    /** As the page is left: saves that end from now on load the engine again themselves. */
    fun leaving() = away.set(true)

    /** Once fcitx is ready after [leaving]: the engine loaded again if an imported table's options were saved since. */
    suspend fun left(fcitx: FcitxAPI) = reloadIfPending(fcitx)

    private suspend fun reloadIfPending(fcitx: FcitxAPI) {
        if (pending.getAndSet(false)) fcitx.reloadEngine()
    }
}
