package com.twinsanityevolved.app

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

    /**
     * True while the game is in a menu: the controls are hidden and do nothing, and a finger swipe steps the menu
     * (the d-pad). Taps still press Cross. In the edit mode the controls always show.
     */
    var menuMode = false
        set(value) {
            if (field == value) return
            field = value
            releaseAll()
            invalidate()
        }

    /** The controls are drawn at this share of their set opacity: the picture stays visible under them. */
    private val gameOpacity = 0.5f

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

    private fun hit(x: Float, y: Float): Slot? = if (menuMode && !editing) null else slots.lastOrNull { s ->
        val r = boundsOf(s)
        hypot(x - r.centerX(), y - r.centerY()) <= r.width() / 2 * 1.15f
    }

    fun refresh() = invalidate()

    /** A button pressed for a moment: the menu's Start, or one step of a finger swipe. */
    fun pulse(bit: Int, ms: Long = 120L) {
        pulseBits = bit
        publish()
        val seq = ++pulseSeq
        postDelayed({ if (seq == pulseSeq) { pulseBits = 0; publish() } }, ms)
    }

    private var pulseSeq = 0

    /**
     * Lets go of everything: no finger is down, the stick is centred. Called when the game loses focus or pauses,
     * because the touch-up of a finger held then may never arrive, and the pad would stay pressed.
     */
    fun releaseAll() {
        pointerOwner.clear()
        stickVector.set(0f, 0f)
        tapPointer = -1
        dragging = false
        pulseBits = 0
        publish()
    }

    fun commitEdit() {
        onEdited?.invoke()
    }

    override fun onDraw(canvas: Canvas) {
        if (menuMode && !editing) return
        for (s in slots) {
            val r = boundsOf(s)
            paint.alpha = (s.alpha * gameOpacity * 255).toInt()
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
        // A finger the event no longer lists has lifted (its up was lost): let it go, or the stick stays deflected.
        val present = HashSet<Int>()
        for (i in 0 until e.pointerCount) present.add(e.getPointerId(i))
        if (pointerOwner.keys.any { it !in present }) {
            pointerOwner.keys.retainAll(present)
            if (pointerOwner.values.none { it.control.isStick }) stickVector.set(0f, 0f)
        }
        if (tapPointer != -1 && tapPointer !in present) tapPointer = -1
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val s = hit(e.getX(i), e.getY(i))
                if (s != null) {
                    pointerOwner[e.getPointerId(i)] = s
                    if (s.control.isStick) moveStick(s, e.getX(i), e.getY(i))
                } else {
                    startTap(e.getPointerId(i), e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    if (e.getPointerId(i) == tapPointer) swipe(e.getX(i), e.getY(i))
                }
                for (i in 0 until e.pointerCount) {
                    val s = pointerOwner[e.getPointerId(i)] ?: continue
                    if (s.control.isStick) moveStick(s, e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                endTap(e.getPointerId(e.actionIndex), e.getX(e.actionIndex), e.getY(e.actionIndex))
                release(e.getPointerId(e.actionIndex))
            }
            MotionEvent.ACTION_CANCEL -> {
                pointerOwner.clear()
                stickVector.set(0f, 0f)
                tapPointer = -1
                swiping = false
            }
        }
        publish()
        return true
    }

    // A tap: a short touch that did not move, on an empty area of the screen. It presses CROSS (the menus' confirm),
    // so the menus work by touch without knowing where their items are.
    // A finger that starts on empty screen is a tap, or a swipe once it moves. A swipe steps the menu: each
    // stride of the finger is one press of the d-pad in that direction, so the menus follow the finger.
    private var tapPointer = -1
    private var tapX = 0f
    private var tapY = 0f
    private var swipeX = 0f
    private var swipeY = 0f
    private var tapStart = 0L
    private fun tapSlop(): Float = 24f * resources.displayMetrics.density
    private fun swipeStride(): Float = 36f * resources.displayMetrics.density

    private fun startTap(pointerId: Int, x: Float, y: Float) {
        if (tapPointer != -1) return   // one tap at a time
        tapPointer = pointerId
        tapX = x
        tapY = y
        swipeX = x
        swipeY = y
        tapStart = System.currentTimeMillis()
    }

    private fun swipe(x: Float, y: Float) {
        if (tapPointer == -1 || !menuMode) return
        if (!swiping && hypot(x - tapX, y - tapY) <= tapSlop()) return
        swiping = true
        val dx = x - swipeX
        val dy = y - swipeY
        val stride = swipeStride()
        when {
            kotlin.math.abs(dy) >= stride && kotlin.math.abs(dy) >= kotlin.math.abs(dx) -> {
                pulse(if (dy > 0) Pad.DOWN else Pad.UP, 90L)
                swipeX = x; swipeY = y
            }
            kotlin.math.abs(dx) >= stride -> {
                pulse(if (dx > 0) Pad.RIGHT else Pad.LEFT, 90L)
                swipeX = x; swipeY = y
            }
        }
    }
    private var swiping = false

    private fun endTap(pointerId: Int, x: Float, y: Float) {
        if (pointerId != tapPointer) return
        tapPointer = -1
        val quick = System.currentTimeMillis() - tapStart < 250L
        val still = hypot(x - tapX, y - tapY) <= tapSlop()
        if (quick && still && !swiping) pulse(Pad.CROSS)
        swiping = false
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
