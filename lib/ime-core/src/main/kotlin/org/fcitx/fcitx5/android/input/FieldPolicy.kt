/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input

/** The field-specific keyboard policies shared by Android glue and pure JVM tests. */
class FieldPolicy {

    var password = false
        private set

    fun focus(password: Boolean) {
        this.password = password
    }

    fun finish() {
        password = false
    }

    fun pairs(autoPairs: Boolean, rawKeys: Boolean): Boolean = autoPairs && !password && !rawKeys
}
