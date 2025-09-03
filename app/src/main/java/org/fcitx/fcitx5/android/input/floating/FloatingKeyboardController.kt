package org.fcitx.fcitx5.android.input.floating

import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import kotlin.math.max
import kotlin.math.min

class FloatingKeyboardController(
    private val anchorView: View,
    private val lifecycleOwner: LifecycleOwner
) {

    private val ctx = anchorView.context
    private val prefs = AppPrefs.getInstance().keyboard

    private var container: FrameLayout? = null
    private var popup: PopupWindow? = null

    private var lastX = 0f
    private var lastY = 0f

    fun isEnabled(): Boolean = prefs.floatingKeyboard.getValue()

    fun isShowing(): Boolean = popup?.isShowing == true

    fun showWith(content: View) {
        if (!isEnabled()) return
        if (popup?.isShowing == true) return
        val dm = ctx.resources.displayMetrics
        val width = dm.widthPixels * prefs.floatingWidthPercent.getValue() / 100
        val height = dm.heightPixels * prefs.floatingHeightPercent.getValue() / 100
        val container = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(content)
            // Only handle drag on the container background, not on keyboard content
            setOnTouchListener { _, event ->
                // Let keyboard content handle its own touch events
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    lastX = event.rawX
                    lastY = event.rawY
                    return@setOnTouchListener false // Don't consume, let it pass through
                }
                false
            }
            // Set lifecycle owner for the floating container
            setViewTreeLifecycleOwner(lifecycleOwner)
        }
        this.container = container
        val popup = PopupWindow(container, width, height, true).apply {
            animationStyle = 0
            isClippingEnabled = true
            isFocusable = false // Don't steal focus
            isOutsideTouchable = true // Allow touches outside to pass through
        }
        this.popup = popup
        // Add drag handle for moving the popup
        addDragHandle(container)
        // Position
        val defaultX = (dm.widthPixels - width) / 2
        val defaultY = dm.heightPixels - height
        val startX = prefs.floatingPosX.getValue().takeIf { it != 0 } ?: defaultX
        val startY = prefs.floatingPosY.getValue().takeIf { it != 0 } ?: defaultY
        popup.showAtLocation(anchorView, Gravity.TOP or Gravity.START, startX, startY)
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
        container = null
    }

    private val tmpRect = Rect()

    private fun moveBy(dx: Int, dy: Int) {
        val popup = popup ?: return
        val contentView = popup.contentView ?: return
        val location = IntArray(2)
        contentView.getLocationOnScreen(location)
        val dm = ctx.resources.displayMetrics
        val newX = min(max(0, location[0] + dx), dm.widthPixels - popup.width)
        val newY = min(max(0, location[1] + dy), dm.heightPixels - popup.height)
        popup.update(newX, newY, -1, -1)
        // persist
        AppPrefs.getInstance().keyboard.floatingPosX.setValue(newX)
        AppPrefs.getInstance().keyboard.floatingPosY.setValue(newY)
    }

    // Add a separate drag handle view for moving the popup
    private fun addDragHandle(container: FrameLayout) {
        val dragHandle = View(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20))
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.rawX
                        lastY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - lastX).toInt()
                        val dy = (event.rawY - lastY).toInt()
                        if (dx != 0 || dy != 0) moveBy(dx, dy)
                        lastX = event.rawX
                        lastY = event.rawY
                        true
                    }
                    else -> false
                }
            }
        }
        container.addView(dragHandle, 0) // Add at top
    }
} 