/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main

import android.database.SQLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.toast
import timber.log.Timber
import java.io.IOException

/**
 * Runs [block] on the app's scope, for what a page starts that must outlive it: leaving a page
 * cancels the page's own scope right after onStop. It starts at once on the main thread, so what
 * it reads of the page is read before the page goes; a failure is logged under [tag] and shown.
 */
fun launchOutlivingPage(tag: String, block: suspend () -> Unit): Job =
    FcitxApplication.getInstance().coroutineScope.launch(Dispatchers.Main.immediate) {
        reportingFailure({
            Timber.w(it, tag)
            // the page may be gone by now
            appContext.toast(it)
        }, block)
    }

/**
 * Runs [block], handing [onFailure] what a save may meet: a file not written (IOException), the
 * database (SQLException), or a provider or the clipboard refusing (SecurityException). Anything
 * else is a bug and goes on to the crash handler, as PickedFiles' showingFileErrors lets it; a
 * cancellation is no failure and goes on too.
 */
internal suspend fun reportingFailure(onFailure: (Throwable) -> Unit, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: IOException) {
        onFailure(e)
    } catch (e: SQLException) {
        onFailure(e)
    } catch (e: SecurityException) {
        onFailure(e)
    }
}
