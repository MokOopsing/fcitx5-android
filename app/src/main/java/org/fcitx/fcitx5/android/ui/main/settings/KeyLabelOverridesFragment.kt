/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.R as MaterialR
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.keyboard.KeyLabelOverrides
import org.fcitx.fcitx5.android.input.popup.PopupOverrides
import org.fcitx.fcitx5.android.input.popup.PopupPreset
import org.fcitx.fcitx5.android.utils.onPositiveButtonClick
import org.fcitx.fcitx5.android.utils.styledColor
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.core.wrapInScrollView

/** Edits the small corner labels without changing a key's long-press or swipe characters. */
class KeyLabelOverridesFragment : ProgressFragment() {

    private val pref get() = AppPrefs.getInstance().internal.keyLabelOverrides

    private var selectedInputMethod = ""
    private var selectedSubMode: String? = null
    private var inputMethods = emptyList<InputMethodEntry>()
    private var activeInputMethod: InputMethodEntry? = null
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedInputMethod = savedInstanceState?.getString(STATE_INPUT_METHOD).orEmpty()
        selectedSubMode = savedInstanceState?.getString(STATE_SUB_MODE)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_INPUT_METHOD, selectedInputMethod)
        outState.putString(STATE_SUB_MODE, selectedSubMode)
        super.onSaveInstanceState(outState)
    }

    override suspend fun initialize(): View {
        val (available, active) = fcitx.runOnReady {
            availableIme().toList() to currentIme()
        }
        inputMethods = available
        activeInputMethod = active
        if (inputMethods.none { it.uniqueName == selectedInputMethod }) {
            selectedInputMethod = active.uniqueName
                .takeIf { name -> inputMethods.any { it.uniqueName == name } }
                ?: inputMethods.firstOrNull()?.uniqueName.orEmpty()
            selectedSubMode = null
        }

        val ctx = requireContext()
        content = ctx.verticalLayout {
            val pad = ctx.dp(16)
            setPadding(pad, ctx.dp(8), pad, pad)
        }
        rebuild()
        return content.wrapInScrollView {
            clipToPadding = false
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                view.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
                insets
            }
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            AppPrefs.getInstance().syncToDeviceEncryptedStorage()
        }
        super.onStop()
    }

    private fun overrides() = KeyLabelOverrides.parse(pref.getValue())

    private fun scope() = KeyLabelOverrides.Scope(selectedInputMethod, selectedSubMode)

    private fun save(value: KeyLabelOverrides) {
        pref.setValue(value.serialize())
    }

    private fun rebuild() {
        val ctx = requireContext()
        val current = overrides()
        val bindingScope = scope()
        val labels = current.labelsFor(bindingScope)
        val inheritedLabels = if (selectedSubMode == null) emptyMap()
        else current.labelsFor(KeyLabelOverrides.Scope(selectedInputMethod))
        val popupOverrides = PopupOverrides.parse(AppPrefs.getInstance().internal.popupOverrides.getValue())
        val activeSubMode = activeInputMethod
            ?.takeIf { it.uniqueName == selectedInputMethod }
            ?.subMode
            ?.takeIf { it.name.isNotEmpty() }
        val subModes = (current.subModesFor(selectedInputMethod) + listOfNotNull(activeSubMode?.name))
            .distinct()

        content.apply {
            removeAllViews()
            if (inputMethods.isEmpty()) {
                add(text(ctx, getString(R.string.key_labels_no_input_methods), body = true), lParams(matchParent))
                return@apply
            }
            add(text(ctx, getString(R.string.key_labels_page_hint), body = true), lParams(matchParent))
            add(MaterialButton(ctx, null, MaterialR.attr.materialButtonOutlinedStyle).apply {
                text = inputMethods.firstOrNull { it.uniqueName == selectedInputMethod }
                    ?.displayName
                    ?: selectedInputMethod
                isEnabled = inputMethods.size > 1
                setOnClickListener { chooseInputMethod() }
            }, lParams { topMargin = ctx.dp(12) })
            add(text(ctx, getString(R.string.key_labels_input_method_hint), body = false), lParams(matchParent) {
                topMargin = ctx.dp(4)
            })
            add(MaterialButton(ctx, null, MaterialR.attr.materialButtonOutlinedStyle).apply {
                text = selectedSubMode
                    ?.let { getString(R.string.key_labels_scope_submode, it) }
                    ?: getString(R.string.key_labels_scope_input_method)
                setOnClickListener { chooseScope(subModes) }
            }, lParams { topMargin = ctx.dp(8) })
            if (activeSubMode == null && subModes.isEmpty()) {
                add(text(ctx, getString(R.string.key_labels_submode_unavailable), body = false), lParams(matchParent) {
                    topMargin = ctx.dp(4)
                })
            }

            add(heading(ctx, R.string.letters), lParams(matchParent))
            Rows.forEachIndexed { i, row ->
                add(ctx.horizontalLayout {
                    weightSum = Rows[0].length.toFloat()
                    gravity = Gravity.CENTER_HORIZONTAL
                    row.forEach { char ->
                        val key = char.toString()
                        val directLabel = labels[key]
                        val label = directLabel ?: inheritedLabels[key].orEmpty()
                        val inherited = directLabel == null && label.isNotEmpty()
                        val symbol = popupOverrides.resolve(key, PopupPreset[key])?.firstOrNull().orEmpty()
                        add(
                            KeyCaps.key(
                                ctx, label.ifEmpty { key.uppercase() }, symbol, marked = directLabel != null,
                                dimmed = inherited,
                                state = when {
                                    directLabel != null -> getString(R.string.key_label_is, directLabel)
                                    inherited -> getString(R.string.key_label_inherited, label)
                                    else -> null
                                }
                            ).apply {
                                setOnClickListener { edit(key, label, directLabel != null) }
                            },
                            lParams(0, ctx.dp(KeyHeight)) {
                                weight = 1f
                                marginStart = ctx.dp(2)
                                marginEnd = ctx.dp(2)
                            }
                        )
                    }
                }, lParams(matchParent) { if (i > 0) topMargin = ctx.dp(6) })
            }

            if (labels.isNotEmpty()) {
                add(MaterialButton(ctx, null, MaterialR.attr.materialButtonOutlinedStyle).apply {
                    setText(R.string.key_labels_reset_all)
                    setOnClickListener { confirmResetAll() }
                }, lParams { topMargin = ctx.dp(16) })
            }
        }
    }

    private fun chooseInputMethod() {
        val ctx = requireContext()
        val names = inputMethods.map { it.displayName }.toTypedArray()
        AlertDialog.Builder(ctx)
            .setTitle(R.string.key_labels_choose_input_method)
            .setSingleChoiceItems(names, inputMethods.indexOfFirst { it.uniqueName == selectedInputMethod }) { dialog, which ->
                selectedInputMethod = inputMethods[which].uniqueName
                selectedSubMode = null
                dialog.dismiss()
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseScope(subModes: List<String>) {
        val ctx = requireContext()
        val choices = listOf(getString(R.string.key_labels_scope_input_method)) +
            subModes
        val selected = selectedSubMode?.let(subModes::indexOf)?.takeIf { it >= 0 }?.plus(1) ?: 0
        AlertDialog.Builder(ctx)
            .setTitle(R.string.key_labels_choose_scope)
            .setSingleChoiceItems(choices.toTypedArray(), selected) { dialog, which ->
                selectedSubMode = subModes.getOrNull(which - 1)
                dialog.dismiss()
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun edit(key: String, initialValue: String, hasOverride: Boolean) {
        val ctx = requireContext()
        val field = EditText(ctx).apply {
            setText(initialValue)
            hint = getString(R.string.key_labels_edit_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
        }
        val box = ctx.horizontalLayout {
            val pad = ctx.dp(24)
            setPadding(pad, ctx.dp(8), pad, 0)
            add(field, lParams(matchParent))
        }
        val dialog = AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.key_label_for, key.uppercase()))
            .setView(box)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .apply {
                if (hasOverride) {
                    setNeutralButton(R.string.reset) { _, _ ->
                        save(overrides().without(scope(), key))
                        rebuild()
                    }
                }
            }
            .show()
        dialog.onPositiveButtonClick onClick@{
            val label = field.text.toString().trim()
            if (!KeyLabelOverrides.isValidLabel(label)) {
                field.error = getString(R.string.key_label_invalid)
                field.requestFocus()
                return@onClick false
            }
            save(overrides().with(scope(), key, label))
            rebuild()
            true
        }
        dialog.setCanceledOnTouchOutside(false)
    }

    private fun confirmResetAll() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.key_labels_reset_all)
            .setMessage(R.string.key_labels_reset_scope_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                save(overrides().without(scope()))
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun heading(ctx: Context, title: Int) = TextView(ctx).apply {
        setText(title)
        textSize = 14f
        setTextColor(ctx.styledColor(android.R.attr.colorPrimary))
        setPadding(0, ctx.dp(20), 0, ctx.dp(8))
    }

    private fun text(ctx: Context, value: String, body: Boolean) = TextView(ctx).apply {
        text = value
        textSize = if (body) 14f else 12f
        setTextColor(ctx.styledColor(MaterialR.attr.colorOnSurfaceVariant))
    }

    private companion object {
        const val STATE_INPUT_METHOD = "input_method"
        const val STATE_SUB_MODE = "sub_mode"
        const val KeyHeight = 52
        val Rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    }
}
