/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.input.FcitxInputMethodService

internal fun Array<Action>.rimeSchemaChoices(currentSchemaName: String?): List<Action> {
    val excludedNames = setOf(
        "fcitx-rime-separator",
        "fcitx-rime-next-schema",
        "fcitx-rime-deploy",
        "fcitx-rime-sync",
        "fcitx_rime_latin"
    )
    val choices = drop(1).filterNot { it.isSeparator || it.name in excludedNames }
    val matches = choices.withIndex().filter { it.value.shortText == currentSchemaName }
    if (matches.size != 1) return choices
    val currentIndex = matches.single().index
    return listOf(choices[currentIndex]) + choices.filterIndexed { index, _ -> index != currentIndex }
}

internal fun FcitxInputMethodService.activateRimeSchema(actionId: Int) {
    finishComposing()
    postFcitxJob {
        reset()
        activateAction(actionId)
    }
}
