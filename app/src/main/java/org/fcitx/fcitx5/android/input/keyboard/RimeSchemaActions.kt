/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.input.FcitxInputMethodService

internal fun Array<Action>.rimeSchemaChoices(): List<Action> {
    val excludedNames = setOf(
        "fcitx-rime-separator",
        "fcitx-rime-next-schema",
        "fcitx-rime-deploy",
        "fcitx-rime-sync",
        "fcitx_rime_latin"
    )
    val choices = drop(1).filterNot { it.isSeparator || it.name in excludedNames }
    val current = choices.firstOrNull { it.isChecked } ?: return choices
    return listOf(current) + choices.filterNot { it.isChecked }
}

internal fun FcitxInputMethodService.activateRimeSchema(actionId: Int) {
    finishComposing()
    postFcitxJob {
        reset()
        activateAction(actionId)
    }
}
