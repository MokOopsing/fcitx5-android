package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

open class KeyboardLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    var rowCount: Int = 0
    var colCount: Int = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val cellWidth = width / colCount
        val cellHeight = height / rowCount

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams
            val w = cellWidth * lp.colSpan
            val h = cellHeight * lp.rowSpan
            val childWidthSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
            val childHeightSpec = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
            child.measure(childWidthSpec, childHeightSpec)
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cellWidth = width / colCount
        val cellHeight = height / rowCount

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams
            val left = lp.col * cellWidth
            val top = lp.row * cellHeight
            val right = left + cellWidth * lp.colSpan
            val bottom = top + cellHeight * lp.rowSpan
            child.layout(left, top, right, bottom)
        }
    }

    class LayoutParams(
        val row: Int,
        val col: Int,
        val rowSpan: Int = 1,
        val colSpan: Int = 1
    ) : ViewGroup.LayoutParams(0, 0)
}