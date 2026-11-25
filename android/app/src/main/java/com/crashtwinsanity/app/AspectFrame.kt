package com.crashtwinsanity.app

import android.content.Context
import android.view.SurfaceView
import android.widget.FrameLayout
import kotlin.math.roundToInt

/**
 * Holds the game's surface at the aspect the player chose and never stretches it. A fixed aspect is letterboxed or
 * pillarboxed inside the screen; "sin estirar" gives the engine the screen's own aspect, so an ultrawide screen
 * gets the wider picture instead of a stretched one.
 */
class AspectFrame(context: Context) : FrameLayout(context) {

    val surface = SurfaceView(context)

    var aspect: Float = 0f
        set(value) {
            field = value
            requestLayout()
        }

    init {
        addView(surface)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val pw = right - left
        val ph = bottom - top
        var w = pw
        var h = ph
        if (aspect > 0f) {
            if (pw.toFloat() / ph > aspect) {
                w = (ph * aspect).roundToInt()
            } else {
                h = (pw / aspect).roundToInt()
            }
        }
        val x = (pw - w) / 2
        val y = (ph - h) / 2
        surface.layout(x, y, x + w, y + h)
    }

    /** The aspect the engine renders at: the chosen one, or the surface's own (sin estirar). */
    fun renderAspect(): Float =
        if (aspect > 0f) aspect else surface.width.toFloat() / surface.height.coerceAtLeast(1)
}
