package com.twinsanityevolved.app

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * The gamepad's buttons for the PS2 pad's controls: which controller button presses each one. A control is named as in
 * the engine's bindings.ini [pad] section (cross=a, dpup, leftshoulder ...). The text the app keeps is the same
 * "key=value" lines, and NativeBridge.setPadMap hands it to the engine.
 */
object PadMapping {

    class Control(val key: String, val label: String, val default: String)

    val controls = listOf(
        Control("cross", "Cross", "a"),
        Control("circle", "Circle", "b"),
        Control("square", "Square", "x"),
        Control("triangle", "Triangle", "y"),
        Control("l1", "L1", "leftshoulder"),
        Control("r1", "R1", "rightshoulder"),
        Control("l2", "L2", "lefttrigger"),
        Control("r2", "R2", "righttrigger"),
        Control("l3", "L3", "leftstick"),
        Control("r3", "R3", "rightstick"),
        Control("start", "Start", "start"),
        Control("select", "Select", "back"),
        Control("up", "D-pad up", "dpup"),
        Control("down", "D-pad down", "dpdown"),
        Control("left", "D-pad left", "dpleft"),
        Control("right", "D-pad right", "dpright"),
    )

    fun defaults(): MutableMap<String, String> = controls.associate { it.key to it.default }.toMutableMap()

    /** The saved map; a missing or unreadable line keeps that control's default. */
    fun parse(text: String?): MutableMap<String, String> {
        val map = defaults()
        if (text == null) return map
        for (line in text.lines()) {
            val eq = line.indexOf('=')
            if (eq < 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            if (key in map && value.isNotEmpty()) map[key] = value
        }
        return map
    }

    fun serialize(map: Map<String, String>): String =
        controls.joinToString("\n") { "${it.key}=${map[it.key] ?: it.default}" }

    /**
     * Sets [key] to the controller button [name]. A button already used by another control moves to [key]'s old button,
     * so no two controls share one button by accident.
     */
    fun assign(map: MutableMap<String, String>, key: String, name: String) {
        val previous = map[key] ?: controls.first { it.key == key }.default
        for (c in controls) {
            if (c.key != key && map[c.key] == name) map[c.key] = previous
        }
        map[key] = name
    }

    /** What a controller button is called on the label: "A", "LB", "D-pad up" ... */
    fun controllerLabel(name: String): String = when (name) {
        "a" -> "A"
        "b" -> "B"
        "x" -> "X"
        "y" -> "Y"
        "leftshoulder" -> "LB"
        "rightshoulder" -> "RB"
        "lefttrigger" -> "LT"
        "righttrigger" -> "RT"
        "leftstick" -> "left stick click"
        "rightstick" -> "right stick click"
        "start" -> "Start"
        "back" -> "Back"
        "guide" -> "Guide"
        "dpup" -> "D-pad up"
        "dpdown" -> "D-pad down"
        "dpleft" -> "D-pad left"
        "dpright" -> "D-pad right"
        else -> name
    }

    fun isGamepad(source: Int): Boolean = (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD

    fun isJoystick(source: Int): Boolean = (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

    /** The SDL controller name of an Android gamepad button, or null for a key that is not a controller button. */
    fun keyName(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> "a"
        KeyEvent.KEYCODE_BUTTON_B -> "b"
        KeyEvent.KEYCODE_BUTTON_X -> "x"
        KeyEvent.KEYCODE_BUTTON_Y -> "y"
        KeyEvent.KEYCODE_BUTTON_L1 -> "leftshoulder"
        KeyEvent.KEYCODE_BUTTON_R1 -> "rightshoulder"
        KeyEvent.KEYCODE_BUTTON_L2 -> "lefttrigger"
        KeyEvent.KEYCODE_BUTTON_R2 -> "righttrigger"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "leftstick"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "rightstick"
        KeyEvent.KEYCODE_BUTTON_START -> "start"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "back"
        KeyEvent.KEYCODE_BUTTON_MODE -> "guide"
        KeyEvent.KEYCODE_DPAD_UP -> "dpup"
        KeyEvent.KEYCODE_DPAD_DOWN -> "dpdown"
        KeyEvent.KEYCODE_DPAD_LEFT -> "dpleft"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "dpright"
        else -> null
    }

    /** The axes a controller's trigger or d-pad reports: each one at rest is zero. */
    private val pressAxes = intArrayOf(
        MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS,
        MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
    )

    /** The controller button an axis motion is pressing, or null while none is past half-way. */
    fun motionName(e: MotionEvent): String? {
        val lt = e.getAxisValue(MotionEvent.AXIS_LTRIGGER)
        val brake = e.getAxisValue(MotionEvent.AXIS_BRAKE)
        val rt = e.getAxisValue(MotionEvent.AXIS_RTRIGGER)
        val gas = e.getAxisValue(MotionEvent.AXIS_GAS)
        val hx = e.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y)
        return when {
            lt >= HALF || brake >= HALF -> "lefttrigger"
            rt >= HALF || gas >= HALF -> "righttrigger"
            hx >= HALF -> "dpright"
            hx <= -HALF -> "dpleft"
            hy >= HALF -> "dpdown"
            hy <= -HALF -> "dpup"
            else -> null
        }
    }

    /** True when a controller's stick, trigger or d-pad is pushed past half-way: the touch pad steps back for it. */
    fun moved(e: MotionEvent): Boolean {
        for (axis in pressAxes) if (abs(e.getAxisValue(axis)) >= HALF) return true
        for (axis in intArrayOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ)) {
            if (abs(e.getAxisValue(axis)) >= HALF) return true
        }
        return false
    }

    private const val HALF = 0.5f
}
