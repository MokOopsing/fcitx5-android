package org.fcitx.fcitx5.android.input.floating

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import splitties.dimensions.dp
import kotlin.math.max
import kotlin.math.min

class FloatingKeyboardController(
    private val anchorView: View,
    private val lifecycleOwner: LifecycleOwner
) {

    private val ctx = anchorView.context
    private val prefs = AppPrefs.getInstance().keyboard
    private val windowManager = ctx.getSystemService(WindowManager::class.java)

    private var container: FrameLayout? = null
    private var isShowing = false

    private var lastX = 0f
    private var lastY = 0f

    fun isEnabled(): Boolean = prefs.floatingKeyboard.getValue()

    fun isShowing(): Boolean = isShowing

    fun showWith(content: View) {
        if (!isEnabled()) return
        if (isShowing) return
        val dm = ctx.resources.displayMetrics
        val width = dm.widthPixels * prefs.floatingWidthPercent.getValue() / 100
        val height = dm.heightPixels * prefs.floatingHeightPercent.getValue() / 100
        val container = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(content)
            // Allow content to handle its own touch events; container does not consume
            setOnTouchListener { _, _ -> false }
            // Set lifecycle owner for the floating container
            setViewTreeLifecycleOwner(lifecycleOwner)
        }
        this.container = container
        // Position
        val defaultX = (dm.widthPixels - width) / 2
        val defaultY = dm.heightPixels - height
        val startX = prefs.floatingPosX.getValue().takeIf { it != 0 } ?: defaultX
        val startY = prefs.floatingPosY.getValue().takeIf { it != 0 } ?: defaultY
        val params = WindowManager.LayoutParams().apply {
            this.width = width
            this.height = height
            x = startX
            y = startY
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
        }
        try {
            windowManager.addView(container, params)
            isShowing = true
            // Add drag handle for moving the popup
            addDragHandle(container)
        } catch (e: Exception) {
            // Fallback to embedded if window manager fails
            isShowing = false
            this.container = null
        }
    }

    fun dismiss() {
        if (isShowing && container != null) {
            try {
                windowManager.removeView(container)
            } catch (e: Exception) {
                // ignore
            }
            isShowing = false
            container = null
        }
    }

    private val tmpRect = Rect()

    private fun moveBy(dx: Int, dy: Int) {
        val container = container ?: return
        if (!isShowing) return
        val params = container.layoutParams as WindowManager.LayoutParams
        val dm = ctx.resources.displayMetrics
        val newX = min(max(0, params.x + dx), dm.widthPixels - params.width)
        val newY = min(max(0, params.y + dy), dm.heightPixels - params.height)
        params.x = newX
        params.y = newY
        try {
            windowManager.updateViewLayout(container, params)
            // persist
            AppPrefs.getInstance().keyboard.floatingPosX.setValue(newX)
            AppPrefs.getInstance().keyboard.floatingPosY.setValue(newY)
        } catch (e: Exception) {
            // ignore update failures
        }
    }

    // Add a separate drag handle view for moving the popup
    private fun addDragHandle(container: FrameLayout) {
        val dragHandle = View(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20))
            setBackgroundColor(Color.TRANSPARENT)
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