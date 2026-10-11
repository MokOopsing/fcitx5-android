/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.content.ComponentName
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.fcitx.fcitx5.android.input.FcitxInputMethodService

/**
 * Fails unless a test may start an engine of its own in this process. While this app is the
 * device's keyboard the system binds its input method here, and that one's engine would share
 * fcitx's events and data files with the test's: fcitx is one per process, and `Fcitx`'s own
 * guard checks only the new instance.
 */
fun checkThisInputMethodIsNotBound() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    val pkg = instrumentation.targetContext.packageName
    val service = FcitxInputMethodService::class.java.name
    val keyboard = device.executeShellCommand("settings get secure default_input_method").trim()
    check(ComponentName.unflattenFromString(keyboard) != ComponentName(pkg, service)) {
        "the device's keyboard is this app, whose input method runs in this process: select another first (adb shell ime set <id>)"
    }
    check(service !in device.executeShellCommand("dumpsys activity services $pkg")) {
        "this app's input method is still bound in this process"
    }
}
