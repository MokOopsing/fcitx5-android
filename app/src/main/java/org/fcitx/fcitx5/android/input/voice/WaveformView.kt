package org.fcitx.fcitx5.android.input.voice

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import jaygoo.widget.wlv.WaveLineView

/**
 * 轻量封装的实时音频波形视图（与言犀保持一致的 API）。
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var isActive = false
    private val waveView: WaveLineView = WaveLineView(context).apply {
        setBackGroundColor(Color.TRANSPARENT)
        setSensibility(15)
        setMoveSpeed(250f)
    }

    init {
        addView(waveView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        visibility = View.GONE
    }

    fun setWaveformColor(@ColorInt color: Int) { waveView.setLineColor(color); invalidate() }

    fun updateAmplitude(amplitude: Float) {
        if (!isActive) return
        val vol = (amplitude.coerceIn(0f, 1f) * 100f).toInt()
        waveView.setVolume(vol)
    }

    fun start() {
        if (isActive) return
        isActive = true
        runCatching { waveView.startAnim() }
    }

    fun stop() {
        if (!isActive) return
        isActive = false
        runCatching { waveView.stopAnim() }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        runCatching { waveView.onWindowFocusChanged(hasWindowFocus) }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView == this && visibility != View.VISIBLE) { if (isActive) stop() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        runCatching { stop(); waveView.release() }
    }
}

