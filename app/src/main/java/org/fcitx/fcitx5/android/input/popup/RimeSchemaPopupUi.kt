/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.popup

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import splitties.dimensions.dp
import kotlin.math.ceil
import kotlin.math.max

data class RimeSchemaPopupItem(val label: String, val action: KeyAction)

class RimeSchemaPopupUi(
    override val ctx: Context,
    theme: Theme,
    outerBounds: Rect,
    triggerBounds: Rect,
    private val items: List<RimeSchemaPopupItem>,
    selectedIndex: Int,
    private val onSelect: (KeyAction) -> Unit
) : PopupContainerUi(ctx, theme, outerBounds, triggerBounds, {}) {

    private val rowHeight = ctx.dp(48)
    private val gap = 0
    private val horizontalPadding = ctx.dp(16)
    private val edgePadding = ctx.dp(12)
    private val textPaint = TextView(ctx).apply { textSize = TEXT_SIZE }.paint
    private val availableWidth = (
        (outerBounds.width().takeIf { it > 0 } ?: ctx.resources.displayMetrics.widthPixels) -
            2 * edgePadding
        ).coerceAtLeast(1)
    private val contentWidth = items.maxOfOrNull {
        textPaint.measureText(it.label)
    }?.let(::ceil)?.toInt() ?: 0
    private val popupWidth = max(
        ctx.dp(MIN_WIDTH),
        contentWidth + 2 * horizontalPadding
    ).coerceAtMost(availableWidth)
    private val popupHeight = minOf(
        items.size * rowHeight,
        (triggerBounds.top - outerBounds.top - gap).coerceAtLeast(rowHeight)
    )
    private val selected = selectedIndex.takeIf { it in items.indices } ?: 0
    private var focusedIndex = selected
    private var dragged = false

    override val offsetX = (
        triggerBounds.centerX() - popupWidth / 2
    ).coerceIn(
        outerBounds.left + edgePadding,
        (outerBounds.right - edgePadding - popupWidth).coerceAtLeast(outerBounds.left + edgePadding)
    ) - triggerBounds.left

    override val offsetY = -popupHeight - gap

    private val rows = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        items.forEachIndexed { index, item ->
            addView(
                TextView(ctx).apply {
                    text = item.label
                    textSize = 16f
                    setTextColor(theme.popupTextColor)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(horizontalPadding, 0, horizontalPadding, 0)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        focusedIndex = index
                        onSelect(item.action)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        rowHeight
                    )
                }
            )
        }
    }

    override val root = ScrollView(ctx).apply {
        isFillViewport = false
        isVerticalScrollBarEnabled = items.size * rowHeight > popupHeight
        background = GradientDrawable().apply {
            cornerRadius = ctx.dp(12).toFloat()
            setColor(theme.popupBackgroundColor)
        }
        clipToOutline = true
        elevation = ctx.dp(2).toFloat()
        addView(rows, ViewGroup.LayoutParams(popupWidth, items.size * rowHeight))
        layoutParams = ViewGroup.LayoutParams(popupWidth, popupHeight)
        updateFocus()
    }

    private fun updateFocus() {
        rows.forEachChild { index, row ->
            row.background = if (index == focusedIndex) {
                GradientDrawable().apply {
                    cornerRadius = ctx.dp(8).toFloat()
                    setColor(theme.genericActiveBackgroundColor)
                }
            } else {
                null
            }
            row.setTextColor(
                if (index == focusedIndex) theme.genericActiveForegroundColor else theme.popupTextColor
            )
        }
    }

    private inline fun LinearLayout.forEachChild(action: (Int, TextView) -> Unit) {
        for (index in 0 until childCount) {
            action(index, getChildAt(index) as TextView)
        }
    }

    override fun onChangeFocus(x: Float, y: Float): Boolean {
        if (x < 0 || x >= popupWidth) return false
        if (y < 0 || y >= popupHeight) {
            if (!dragged) return false
            root.scrollBy(0, if (y < 0) -rowHeight else rowHeight)
            focusedIndex = if (y < 0) {
                root.scrollY / rowHeight
            } else {
                (root.scrollY + popupHeight - 1) / rowHeight
            }.coerceIn(items.indices)
            updateFocus()
            return false
        }
        dragged = true
        val index = ((y + root.scrollY) / rowHeight).toInt().coerceIn(items.indices)
        if (index != focusedIndex) {
            focusedIndex = index
            updateFocus()
        }
        return false
    }

    override fun onTrigger(): KeyAction? =
        if (dragged) items[focusedIndex].action else null

    private companion object {
        const val TEXT_SIZE = 16f
        const val MIN_WIDTH = 48
    }
}
