package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

open class KeyboardLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    var rowCount: Int = 0
    private var colCount: Int = 0 // 新增：全局最大列数

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val cellHeight = if (rowCount > 0) height / rowCount else 0

        // 统计全局最大列数
        colCount = 0
        val rowMap = mutableMapOf<Int, MutableList<Pair<View, LayoutParams>>>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams
            rowMap.getOrPut(lp.row) { mutableListOf() }.add(child to lp)
        }
        for ((row, views) in rowMap) {
            val rowColEnd = views.maxOfOrNull { it.second.col + it.second.colSpan } ?: 0
            if (rowColEnd > colCount) colCount = rowColEnd
        }
        val cellWidth = if (colCount > 0) width / colCount else 0

        // 每个键独立测量
        for ((_, views) in rowMap) {
            for ((child, lp) in views) {
                val w = cellWidth * lp.colSpan
                val h = cellHeight * lp.rowSpan
                val childWidthSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
                val childHeightSpec = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
                child.measure(childWidthSpec, childHeightSpec)
            }
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val height = b - t
        val cellHeight = if (rowCount > 0) height / rowCount else 0
        val cellWidth = if (colCount > 0) width / colCount else 0

        // 按行分组
        val rowMap = mutableMapOf<Int, MutableList<Pair<View, LayoutParams>>>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams
            rowMap.getOrPut(lp.row) { mutableListOf() }.add(child to lp)
        }

        // 记录所有被跨行/跨列占用的格子
        val occupied = mutableMapOf<Pair<Int, Int>, Boolean>()
        // 先预占所有跨行跨列的格子
        for ((_, views) in rowMap) {
            for ((_, lp) in views) {
                for (dr in 0 until lp.rowSpan) {
                    for (dc in 0 until lp.colSpan) {
                        val pos = Pair(lp.row + dr, lp.col + dc)
                        if (dr != 0 || dc != 0) { // 除了起始格子外都标记为占用
                            occupied[pos] = true
                        }
                    }
                }
            }
        }

        // 逐行布局
        for (row in 0 until rowCount) {
            val views = rowMap[row] ?: continue
            // 按col排序
            val sortedViews = views.sortedBy { it.second.col }
            var col = 0
            for ((child, lp) in sortedViews) {
                // 找到下一个未被占用的起始格子
                while (col < colCount && occupied[Pair(row, col)] == true) {
                    col++
                }
                if (col >= colCount) break // 超出本行最大列数
                val left = col * cellWidth
                val right = left + cellWidth * lp.colSpan
                val top = row * cellHeight
                val bottom = top + cellHeight * lp.rowSpan
                child.layout(left, top, right, bottom)
                // 标记所有被占用的格子（包括自己）
                for (dr in 0 until lp.rowSpan) {
                    for (dc in 0 until lp.colSpan) {
                        occupied[Pair(row + dr, col + dc)] = true
                    }
                }
                col += lp.colSpan
            }
        }
    }

    class LayoutParams(
        val row: Int,
        val col: Int,
        val rowSpan: Int = 1,
        val colSpan: Int = 1
    ) : ViewGroup.LayoutParams(0, 0)
}
