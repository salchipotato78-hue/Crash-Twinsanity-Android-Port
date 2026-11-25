package com.crashtwinsanity.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * The on-screen pad. Play: every finger on a control holds its bit, and the stick follows its finger.
 * Edit: a finger drags the selected control; GameActivity's bars set its size and opacity.
 */
class TouchOverlay(context: Context) : View(context) {

    var slots: MutableList<Slot> = mutableListOf()
    var onPad: ((bits: Int, lx: Float, ly: Float) -> Unit)? = null
    var onEdited: (() -> Unit)? = null
    var onSelectionChanged: ((Slot?) -> Unit)? = null

    var editing = false
        set(value) {
            field = value
            dragging = false
            if (!value) selected = null
            invalidate()
        }

    var selected: Slot? = null
        set(value) {
            field = value
            onSelectionChanged?.invoke(value)
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
    }
    private val bitmaps = HashMap<Control, Bitmap>()
    private val knob: Bitmap by lazy { load("joy_knob") }
    private val pointerOwner = HashMap<Int, Slot>()
    private val stickVector = PointF()
    private val dragOffset = PointF()
    private var dragging = false
    private var pulseBits = 0

    private fun load(name: String): Bitmap = BitmapFactory.decodeResource(
        resources, resources.getIdentifier(name, "drawable", context.packageName)
    )

    private fun bitmap(c: Control): Bitmap = bitmaps.getOrPut(c) { load(c.drawable) }

    /** Where a control is on screen now: its centre from the slot, its width from the scale, its height from the art. */
    private fun boundsOf(s: Slot): RectF {
        val b = bitmap(s.control)
        val w = s.control.widthFraction * width * s.scale
        val h = w * b.height / b.width
        val cx = s.x * width
        val cy = s.y * height
        return RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    private fun hit(x: Float, y: Float): Slot? = slots.lastOrNull { s ->
        val r = boundsOf(s)
        hypot(x - r.centerX(), y - r.centerY()) <= r.width() / 2 * 1.15f
    }

    fun refresh() = invalidate()

    /** A button tapped from the menu: pressed for a moment (the Start button in the menu). */
    fun pulse(bit: Int) {
        pulseBits = bit
        publish()
        postDelayed({ pulseBits = 0; publish() }, 120)
    }

    fun commitEdit() {
        onEdited?.invoke()
    }

    override fun onDraw(canvas: Canvas) {
        for (s in slots) {
            val r = boundsOf(s)
            paint.alpha = (s.alpha * 255).toInt()
            canvas.drawBitmap(bitmap(s.control), null, r, paint)
            if (s.control.isStick) {
                val k = r.width() * 0.42f
                val kx = r.centerX() + stickVector.x * r.width() * 0.25f
                val ky = r.centerY() + stickVector.y * r.width() * 0.25f
                canvas.drawBitmap(knob, null, RectF(kx - k / 2, ky - k / 2, kx + k / 2, ky + k / 2), paint)
            }
            if (editing && s === selected) canvas.drawRect(r, outline)
        }
        paint.alpha = 255
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (editing) return editTouch(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val s = hit(e.getX(i), e.getY(i))
                if (s != null) {
                    pointerOwner[e.getPointerId(i)] = s
                    if (s.control.isStick) moveStick(s, e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val s = pointerOwner[e.getPointerId(i)] ?: continue
                    if (s.control.isStick) moveStick(s, e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> release(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> {
                pointerOwner.clear()
                stickVector.set(0f, 0f)
            }
        }
        publish()
        return true
    }

    private fun release(pointerId: Int) {
        val s = pointerOwner.remove(pointerId) ?: return
        if (s.control.isStick) stickVector.set(0f, 0f)
    }

    private fun moveStick(s: Slot, px: Float, py: Float) {
        val r = boundsOf(s)
        val radius = r.width() / 2
        val dx = (px - r.centerX()) / radius
        val dy = (py - r.centerY()) / radius
        val len = hypot(dx, dy)
        val k = if (len > 1f) 1f / len else 1f
        stickVector.set(dx * k, dy * k)
    }

    private fun publish() {
        var bits = pulseBits
        for (s in pointerOwner.values) {
            if (!s.control.isStick) bits = bits or s.control.padBit
        }
        // The screen's y runs down; the pad's stick runs up.
        onPad?.invoke(bits, stickVector.x, -stickVector.y)
        invalidate()
    }

    private fun editTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val s = hit(e.x, e.y)
                if (s != null) {
                    selected = s
                    dragging = true
                    dragOffset.set(e.x - s.x * width, e.y - s.y * height)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val s = selected
                if (dragging && s != null) {
                    s.x = ((e.x - dragOffset.x) / width).coerceIn(0.02f, 0.98f)
                    s.y = ((e.y - dragOffset.y) / height).coerceIn(0.02f, 0.98f)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) commitEdit()
                dragging = false
            }
        }
        invalidate()
        return true
    }
}
