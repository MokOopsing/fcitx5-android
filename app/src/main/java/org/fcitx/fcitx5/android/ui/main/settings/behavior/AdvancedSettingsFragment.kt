/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.UserDataManager
import org.fcitx.fcitx5.android.data.clipboard.ClipboardManager
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.ui.common.withLoadingDialog
import org.fcitx.fcitx5.android.ui.main.MainViewModel
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.AppUtil
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.navigateWithAnim
import org.fcitx.fcitx5.android.utils.formatDateTime
import org.fcitx.fcitx5.android.utils.importErrorDialog
import org.fcitx.fcitx5.android.utils.iso8601UTCDateTime
import org.fcitx.fcitx5.android.utils.queryFileName
import org.fcitx.fcitx5.android.utils.setup
import org.fcitx.fcitx5.android.utils.toast
import timber.log.Timber

class AdvancedSettingsFragment : ManagedPreferenceFragment(AppPrefs.getInstance().advanced) {

    private val viewModel: MainViewModel by activityViewModels()

    private var exportTimestamp = System.currentTimeMillis()

    private lateinit var exportLauncher: ActivityResultLauncher<String>

    private lateinit var importLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri == null) return@registerForActivityResult
                val ctx = requireContext()
                val cr = ctx.contentResolver
                lifecycleScope.withLoadingDialog(ctx) {
                    val name = cr.queryFileName(uri) ?: return@withLoadingDialog
                    if (!name.endsWith(".zip")) {
                        ctx.importErrorDialog(R.string.exception_user_data_filename, name)
                        return@withLoadingDialog
                    }
                    // outlives the page, which a rotation cancels: once the engine's files are
                    // replaced the app must exit, else fcitx comes back writing to the old ones
                    val failed = FcitxApplication.getInstance().coroutineScope.async(Dispatchers.Main.immediate) {
                        try {
                            // stop fcitx before overwriting files
                            FcitxDaemon.stopFcitx()
                            val metadata = withContext(Dispatchers.IO) {
                                ClipboardManager.closeDatabase()
                                val inputStream = cr.openInputStream(uri)!!
                                UserDataManager.import(inputStream).getOrThrow()
                            }
                            val exportTime = formatDateTime(metadata.exportTime)
                            ctx.toast(ctx.getString(R.string.user_data_imported, exportTime))
                            // the toast made before the process ends
                            delay(400L)
                            AppUtil.restart(ctx.applicationContext)
                            null
                        } catch (e: Exception) {
                            // reopen the clipboard and restart fcitx in case importing failed
                            restartAfterFailedImport({ ClipboardManager.reopenDatabase() }) { FcitxDaemon.startFcitx() }
                            e
                        }
                    }
                    failed.await()?.let { ctx.importErrorDialog(it) }
                }
            }
        exportLauncher =
            registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                if (uri == null) return@registerForActivityResult
                val ctx = requireContext()
                lifecycleScope.withLoadingDialog(ctx) {
                    try {
                        withContext(Dispatchers.IO) {
                            val outputStream = ctx.contentResolver.openOutputStream(uri)!!
                            UserDataManager.export(outputStream, exportTimestamp).getOrThrow()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        ctx.toast(e)
                    }
                }
            }
    }

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        val ctx = requireContext()
        // what a phone's user seldom needs, kept off the main page
        val more = PreferenceCategory(ctx).apply {
            setTitle(R.string.more_settings)
            isIconSpaceReserved = false
        }
        screen.addPreference(more)
        more.addPreference(R.string.candidates_window) { navigateWithAnim(SettingsRoute.CandidatesWindow) }
        // the punctuation addon's own page went with the list of addons
        more.addPreference(R.string.punctuation_map) {
            navigateWithAnim(SettingsRoute.Punctuation(getString(R.string.punctuation_map)))
        }
        // the whole of the user's data in and out, as one file: where it lies on the phone is no
        // business of theirs (fcitx's global options and its data folder are not shown either)
        val userData = PreferenceCategory(ctx).apply {
            setTitle(R.string.user_data)
            isIconSpaceReserved = false
        }
        screen.addPreference(userData)
        userData.addPreference(Preference(ctx).apply {
            key = EXPORT_USER_DATA_KEY
            setup(ctx.getString(EXPORT_USER_DATA), ctx.getString(EXPORT_USER_DATA_SUMMARY)) {
                lifecycleScope.withLoadingDialog(ctx) {
                    viewModel.fcitx.runOnReady {
                        save()
                    }
                    exportTimestamp = System.currentTimeMillis()
                    exportLauncher.launch("youmo_${iso8601UTCDateTime(exportTimestamp)}.zip")
                }
            }
        })
        userData.addPreference(Preference(ctx).apply {
            key = IMPORT_USER_DATA_KEY
            setup(ctx.getString(IMPORT_USER_DATA)) {
                AlertDialog.Builder(ctx)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.import_user_data)
                    .setMessage(R.string.confirm_import_user_data)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        importLauncher.launch("application/zip")
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        })
    }

    companion object {
        // drawn here by hand: SettingsIndex finds them by these, their titles and their keys
        @StringRes val EXPORT_USER_DATA = R.string.export_user_data
        @StringRes val EXPORT_USER_DATA_SUMMARY = R.string.export_user_data_summary
        @StringRes val IMPORT_USER_DATA = R.string.import_user_data
        const val EXPORT_USER_DATA_KEY = "export_user_data"
        const val IMPORT_USER_DATA_KEY = "import_user_data"
    }
}

/**
 * Brings back what a failed import stopped: the clipboard if it can, and fcitx whatever became of it,
 * as reopening the clipboard may still throw (counting its clips, say); the import's error is the one shown.
 * fcitx is started first in any case: an Error is then rethrown, and so is a cancellation, which ends the coroutine quietly.
 */
internal suspend fun restartAfterFailedImport(reopenClipboard: suspend () -> Unit, startFcitx: () -> Unit) {
    try {
        reopenClipboard()
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        // a CancellationException is an Exception too
        currentCoroutineContext().ensureActive()
        Timber.w(e, "Failed to reopen the clipboard after an import")
    } finally {
        startFcitx()
    }
}
