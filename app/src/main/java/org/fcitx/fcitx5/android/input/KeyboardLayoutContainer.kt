package org.fcitx.fcitx5.android.input

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.roundToInt

class KeyboardLayoutContainer(context: Context) : FrameLayout(context) {

    companion object {
        private const val FLOATING_WIDTH_FRACTION = 0.85f
        private const val DRAG_HANDLE_HEIGHT_DP = 18
        private const val CARD_RADIUS_DP = 16f
    }

    private val density = resources.displayMetrics.density
    private val dragHandleHeight = (DRAG_HANDLE_HEIGHT_DP * density).roundToInt()
    private val dragHandle = View(context)
    private var content: View? = null
    private var floating = false
    private var offsetX = 0
    private var offsetY = 0
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var bounds = Rect()

    var onDrag: ((dxDp: Float, dyDp: Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null
    var onBoundsChanged: ((Rect) -> Unit)? = null

    init {
        clipChildren = false
        dragHandle.setBackgroundColor(Color.TRANSPARENT)
        dragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - lastTouchX) / density
                    val dy = (lastTouchY - event.rawY) / density
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    onDrag?.invoke(dx, dy)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onDragEnd?.invoke()
                    true
                }

                else -> false
            }
        }
        addView(dragHandle, LayoutParams(0, 0))
    }

    fun setContentView(view: View) {
        content?.let(::removeView)
        content = view
        addView(view, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        requestLayout()
    }

    fun setFloatingMode(enabled: Boolean, x: Int, y: Int) {
        floating = enabled
        offsetX = x
        offsetY = y
        background = if (enabled) GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = CARD_RADIUS_DP * density
        } else null
        clipToOutline = enabled
        elevation = if (enabled) 12f * density else 0f
        dragHandle.visibility = if (enabled) VISIBLE else GONE
        requestLayout()
    }

    fun updateOffset(x: Int, y: Int) {
        offsetX = x
        offsetY = y
        requestLayout()
    }

    fun moveBy(dxDp: Float, dyDp: Float): Pair<Int, Int> {
        if (!floating || content == null) return offsetX to offsetY
        val maxX = (((width - content!!.width) / 2) / density).roundToInt().coerceAtLeast(0)
        val maxY = ((height - dragHandleHeight - content!!.height) / density).roundToInt().coerceAtLeast(0)
        offsetX = (offsetX + dxDp.roundToInt()).coerceIn(-maxX, maxX)
        offsetY = (offsetY + dyDp.roundToInt()).coerceIn(0, maxY)
        requestLayout()
        return offsetX to offsetY
    }

    fun keyboardBounds(): Rect = Rect(bounds)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!floating) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val cardWidth = (width * FLOATING_WIDTH_FRACTION).roundToInt()
        val contentWidthSpec = MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY)
        val contentHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
        content?.measure(contentWidthSpec, contentHeightSpec)
        dragHandle.measure(
            MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dragHandleHeight, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = content ?: return
        if (!floating) {
            child.layout(0, 0, width, child.measuredHeight)
            dragHandle.layout(0, 0, 0, 0)
            updateBounds()
            return
        }

        val offsetXPx = (offsetX * density).roundToInt()
        val offsetYPx = (offsetY * density).roundToInt()
        val cardLeft = ((width - child.measuredWidth) / 2 + offsetXPx).coerceIn(
            0,
            (width - child.measuredWidth).coerceAtLeast(0)
        )
        val totalHeight = dragHandleHeight + child.measuredHeight
        val cardTop = (height - totalHeight - offsetYPx).coerceIn(
            0,
            (height - totalHeight).coerceAtLeast(0)
        )
        dragHandle.layout(cardLeft, cardTop, cardLeft + child.measuredWidth, cardTop + dragHandleHeight)
        child.layout(
            cardLeft,
            cardTop + dragHandleHeight,
            cardLeft + child.measuredWidth,
            cardTop + totalHeight
        )
        updateBounds()
    }

    private fun updateBounds() {
        val child = content ?: return
        if (!floating || child.width == 0) return
        val location = IntArray(2)
        getLocationInWindow(location)
        val left = location[0] + dragHandle.left
        val top = location[1] + dragHandle.top
        val newBounds = Rect(left, top, left + child.width, location[1] + child.bottom)
        if (newBounds != bounds) {
            bounds = newBounds
            onBoundsChanged?.invoke(Rect(newBounds))
        }
    }
}
