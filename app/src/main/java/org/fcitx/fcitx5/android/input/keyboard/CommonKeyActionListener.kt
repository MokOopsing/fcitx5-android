/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.keyboard

import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.broadcast.PreeditEmptyStateComponent
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dialog.InputMethodPickerDialog
import org.fcitx.fcitx5.android.input.keyboard.KeyAction.BeginSelectionSwipeAction
import org.fcitx.fcitx5.android.input.keyboard.KeyAction.CommitAction
import org.fcitx.fcitx5.android.input.keyboard.KeyAction.DeleteSelectionAction
import org.fcitx.fcitx5.android.input.keyboard.KeyAction.FcitxKeyAction
import org.fcitx.fcitx5.android.input.keyboard.KeyAction.LangSwitchAction
import org.fcitx.fcitx5.android.input.keyboard.KeyAction.MoveSelectionAction
import org.fcitx.fcitx5.android.input.keyboard.KeyAction.PickerSwitchAction
import org.fcitx.fcitx5.android.input.keyboard.KeyAction.QuickPhraseAction
import org.fcitx.fcitx5.android.input.keyboard.KeyAction.ShowInputMethodPickerAction
import org.fcitx.fcitx5.android.input.keyboard.KeyAction.SpaceLongPressAction
import org.fcitx.fcitx5.android.input.keyboard.KeyAction.SymAction
import org.fcitx.fcitx5.android.input.keyboard.KeyAction.UnicodeAction
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.switchToNextIME
import org.mechdancer.dependency.Dependent
import org.mechdancer.dependency.UniqueComponent
import org.mechdancer.dependency.manager.ManagedHandler
import org.mechdancer.dependency.manager.managedHandler
import org.mechdancer.dependency.manager.must

class CommonKeyActionListener :
    UniqueComponent<CommonKeyActionListener>(), Dependent, ManagedHandler by managedHandler() {

    private val context by manager.context()
    private val fcitx by manager.fcitx()
    private val service by manager.inputMethodService()
    private val preeditState: PreeditEmptyStateComponent by manager.must()
    private val windowManager: InputWindowManager by manager.must()

    private var lastPickerType by AppPrefs.getInstance().internal.lastPickerType

    private val kbdPrefs = AppPrefs.getInstance().keyboard

    private val spaceKeyLongPressBehavior by kbdPrefs.spaceKeyLongPressBehavior
    private val langSwitchKeyBehavior by kbdPrefs.langSwitchKeyBehavior

    private val backspaceSwipe = BackspaceSwipeBehavior()

    private suspend fun FcitxAPI.commitAndReset() = with(service) { commitAndReset() }

    private fun showInputMethodPicker() {
        fcitx.launchOnReady {
            service.lifecycleScope.launch {
                service.showDialog(InputMethodPickerDialog.build(it, service, context))
            }
        }
    }

    val listener: KeyActionListener by lazy {
        KeyActionListener { action, _ ->
            when (action) {
                is FcitxKeyAction -> service.postFcitxJob {
                    sendKey(action.act, action.states.states, action.code)
                }
                is SymAction -> service.postFcitxJob {
                    sendKey(action.sym, action.states)
                }
                is CommitAction -> service.commitOverEngine(action.text, action.replacing)
                is QuickPhraseAction -> service.postFcitxJob {
                    commitAndReset()
                    triggerQuickPhrase()
                }
                is UnicodeAction -> service.postFcitxJob {
                    commitAndReset()
                    triggerUnicode()
                }
                is LangSwitchAction -> {
                    when (langSwitchKeyBehavior) {
                        LangSwitchBehavior.ToggleActivate -> {
                            service.postFcitxJob {
                                toggleIme()
                            }
                        }
                        LangSwitchBehavior.NextInputMethodApp -> {
                            service.switchToNextIME()
                        }
                        LangSwitchBehavior.SimulateShiftKey -> service.postFcitxJob {
                            simulateShift()
                        }
                        LangSwitchBehavior.ToggleRimeAscii -> service.postFcitxJob {
                            statusArea().firstOrNull { it.name == "fcitx-rime-im" }?.let {
                                it.menu?.firstOrNull()?.let { targetAction ->
                                    activateAction(targetAction.id)
                                }
                            }
                        }
                        LangSwitchBehavior.SwitchRimeSchema -> {
                            service.finishComposing()
                            service.postFcitxJob {
                                reset()
                                val rimeStatusArea = statusArea().firstOrNull { it.name == "fcitx-rime-im" }
                                rimeStatusArea?.menu
                                    ?.find { it.name == "fcitx-rime-next-schema" }
                                    ?.let { activateAction(it.id) }
                            }
                        }
                    }
                }
                is ShowInputMethodPickerAction -> showInputMethodPicker()
                is BeginSelectionSwipeAction -> backspaceSwipe.onTouchDown()
                is MoveSelectionAction -> {
                    // Candidates without preedit may still belong to the engine; remove them before
                    // applying the swipe to the text before the cursor.
                    val composing = !preeditState.isEmpty
                    when (backspaceSwipe.onMove(composing)) {
                        BackspaceSwipeBehavior.MoveEffect.ExtendSelection ->
                            service.applySelectionOffset(action.start, action.end)
                        BackspaceSwipeBehavior.MoveEffect.None -> {}
                    }
                }
                is DeleteSelectionAction -> {
                    when (backspaceSwipe.onRelease(action.totalCnt)) {
                        // the engine first forgets what it committed, maybe deleted with the
                        // selection: the cursor reports of both are predicted and bring no Context
                        BackspaceSwipeBehavior.ReleaseEffect.DeleteSelection -> {
                            service.postFcitxJob { reset() }
                            service.deleteSelection()
                        }
                        BackspaceSwipeBehavior.ReleaseEffect.ResetComposition ->
                            service.postFcitxJob { reset() }
                        BackspaceSwipeBehavior.ReleaseEffect.None -> {}
                    }
                }
                is PickerSwitchAction -> {
                    // update lastSymbolType only when specified explicitly
                    val key = action.key?.also { k -> lastPickerType = k.name }
                        ?: runCatching { PickerWindow.Key.valueOf(lastPickerType) }.getOrNull()
                        ?: PickerWindow.Key.Emoji
                    ContextCompat.getMainExecutor(context).execute {
                        windowManager.attachWindow(key)
                    }
                }
                SpaceLongPressAction -> {
                    when (spaceKeyLongPressBehavior) {
                        SpaceLongPressBehavior.None -> {}
                        SpaceLongPressBehavior.ToggleActivate -> service.postFcitxJob {
                            toggleIme()
                        }
                        SpaceLongPressBehavior.ShowPicker -> showInputMethodPicker()
                        SpaceLongPressBehavior.SimulateShiftKey -> service.postFcitxJob {
                            simulateShift()
                        }
                        SpaceLongPressBehavior.ToggleRimeAscii -> service.postFcitxJob {
                            statusArea().firstOrNull {it.name == "fcitx-rime-im" }?.let {
                                it.menu?.firstOrNull()?.let {
                                    (id) -> activateAction(id)
                                }
                            }
                        }
                        SpaceLongPressBehavior.SwitchRimeSchema -> {
                            service.finishComposing()
                            service.postFcitxJob {
                                reset()
                                statusArea().firstOrNull { it.name == "fcitx-rime-im" }?.let {
                                    //it.menu?.getOrNull(it.menu.size - 4)?.let {
                                    it.menu?.find { targetAction -> targetAction.name == "fcitx-rime-next-schema" }?.let {
                                        targetAction -> activateAction(targetAction.id)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}
