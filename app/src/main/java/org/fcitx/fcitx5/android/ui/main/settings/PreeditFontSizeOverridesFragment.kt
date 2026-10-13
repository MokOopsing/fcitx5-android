/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputType
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
import org.fcitx.fcitx5.android.input.preedit.PreeditFontSizeOverrides
import org.fcitx.fcitx5.android.utils.onPositiveButtonClick
import org.fcitx.fcitx5.android.utils.styledColor
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.wrapInScrollView

/** Sets a preedit font size for an input method or one of its submodes. */
class PreeditFontSizeOverridesFragment : ProgressFragment() {

    private val overridesPref get() = AppPrefs.getInstance().internal.preeditFontSizeOverrides
    private val defaultSize get() = AppPrefs.getInstance().keyboardAppearance.preeditFontSize.getValue()

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

    private fun overrides() = PreeditFontSizeOverrides.parse(overridesPref.getValue())

    private fun scope() = PreeditFontSizeOverrides.Scope(selectedInputMethod, selectedSubMode)

    private fun save(value: PreeditFontSizeOverrides) {
        overridesPref.setValue(value.serialize())
    }

    private fun rebuild() {
        val ctx = requireContext()
        val current = overrides()
        val selectedScope = scope()
        val subModes = (current.subModesFor(selectedInputMethod) +
            listOfNotNull(
                activeInputMethod
                    ?.takeIf { it.uniqueName == selectedInputMethod }
                    ?.subMode
                    ?.name
                    ?.takeIf(String::isNotEmpty)
            )).distinct()
        val effectiveSize = current.fontSizeFor(
            selectedInputMethod,
            selectedSubMode,
            defaultSize
        )

        content.apply {
            removeAllViews()
            if (inputMethods.isEmpty()) {
                add(text(ctx, getString(R.string.preedit_font_size_no_input_methods), body = true), lParams(matchParent))
                return@apply
            }
            add(text(ctx, getString(R.string.preedit_font_size_page_hint), body = true), lParams(matchParent))
            add(MaterialButton(ctx, null, MaterialR.attr.materialButtonOutlinedStyle).apply {
                text = inputMethods.firstOrNull { it.uniqueName == selectedInputMethod }
                    ?.displayName
                    ?: selectedInputMethod
                isEnabled = inputMethods.size > 1
                setOnClickListener { chooseInputMethod() }
            }, lParams { topMargin = ctx.dp(12) })
            add(MaterialButton(ctx, null, MaterialR.attr.materialButtonOutlinedStyle).apply {
                text = selectedSubMode
                    ?.let { getString(R.string.preedit_font_size_scope_submode, it) }
                    ?: getString(R.string.preedit_font_size_scope_input_method)
                setOnClickListener { chooseScope(subModes) }
            }, lParams { topMargin = ctx.dp(8) })

            if (activeInputMethod?.uniqueName != selectedInputMethod && subModes.isEmpty()) {
                add(text(ctx, getString(R.string.preedit_font_size_submode_unavailable), body = false),
                    lParams(matchParent) { topMargin = ctx.dp(4) })
            }

            add(text(ctx, getString(R.string.preedit_font_size_current, effectiveSize), body = true),
                lParams(matchParent) { topMargin = ctx.dp(16) })
            add(MaterialButton(ctx, null, MaterialR.attr.materialButtonOutlinedStyle).apply {
                setText(R.string.preedit_font_size_set)
                setOnClickListener { editSize(effectiveSize) }
            }, lParams { topMargin = ctx.dp(8) })

            if (current.fontSizeFor(selectedScope) != null) {
                add(MaterialButton(ctx, null, MaterialR.attr.materialButtonOutlinedStyle).apply {
                    setText(R.string.preedit_font_size_reset_scope)
                    setOnClickListener { confirmReset() }
                }, lParams { topMargin = ctx.dp(8) })
            }
        }
    }

    private fun chooseInputMethod() {
        val names = inputMethods.map { it.displayName }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.preedit_font_size_choose_input_method)
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
        val choices = listOf(getString(R.string.preedit_font_size_scope_input_method)) +
            subModes.map { getString(R.string.preedit_font_size_scope_submode, it) }
        val selected = selectedSubMode?.let(subModes::indexOf)?.takeIf { it >= 0 }?.plus(1) ?: 0
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.preedit_font_size_choose_scope)
            .setSingleChoiceItems(choices.toTypedArray(), selected) { dialog, which ->
                selectedSubMode = subModes.getOrNull(which - 1)
                dialog.dismiss()
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun editSize(initialSize: Int) {
        val ctx = requireContext()
        val field = EditText(ctx).apply {
            setText(initialSize.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            isSingleLine = true
        }
        val box = ctx.horizontalLayout {
            val pad = ctx.dp(24)
            setPadding(pad, ctx.dp(8), pad, 0)
            add(field, lParams(matchParent))
        }
        val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.preedit_font_size_dialog_title)
            .setView(box)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.onPositiveButtonClick onClick@{
            val size = field.text.toString().toIntOrNull()
            if (size == null ||
                size !in PreeditFontSizeOverrides.MIN_SIZE..PreeditFontSizeOverrides.MAX_SIZE
            ) {
                field.error = getString(R.string.preedit_font_size_invalid)
                field.requestFocus()
                return@onClick false
            }
            save(overrides().with(scope(), size))
            rebuild()
            true
        }
        dialog.setCanceledOnTouchOutside(false)
    }

    private fun confirmReset() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.preedit_font_size_reset_scope)
            .setMessage(R.string.preedit_font_size_reset_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                save(overrides().without(scope()))
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun text(ctx: Context, value: String, body: Boolean) = TextView(ctx).apply {
        text = value
        textSize = if (body) 14f else 12f
        setTextColor(ctx.styledColor(android.R.attr.textColorSecondary))
    }

    private companion object {
        const val STATE_INPUT_METHOD = "input_method"
        const val STATE_SUB_MODE = "sub_mode"
    }
}
