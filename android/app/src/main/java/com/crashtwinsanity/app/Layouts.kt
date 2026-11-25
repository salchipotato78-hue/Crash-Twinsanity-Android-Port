package com.crashtwinsanity.app

/** The PS2 pad's bits, as the engine reads them through NativeBridge.setPad. */
object Pad {
    const val CROSS = 1
    const val CIRCLE = 2
    const val SQUARE = 4
    const val TRIANGLE = 8
    const val L1 = 16
    const val R1 = 32
    const val START = 64
}

/** Every touch control: its drawable, its pad bit and its width at scale 1 (dp). */
enum class Control(val drawable: String, val padBit: Int, val widthDp: Float, val isStick: Boolean = false) {
    CROSS("btn_cross", Pad.CROSS, 56f),
    CIRCLE("btn_circle", Pad.CIRCLE, 56f),
    SQUARE("btn_square", Pad.SQUARE, 56f),
    TRIANGLE("btn_triangle", Pad.TRIANGLE, 56f),
    L1("btn_l1", Pad.L1, 84f),
    R1("btn_r1", Pad.R1, 84f),
    START("btn_start", Pad.START, 40f),
    STICK("joy_base", 0, 140f, isStick = true),
}

/** The two layouts from the reference pictures: Cortex has L1 and R1, Crash y Nina only R1 (on the left). */
enum class Profile(val label: String) {
    CORTEX("Cortex"),
    CRASH_NINA("Crash y Nina"),
}

/**
 * One control's place. x and y are its centre as fractions of the screen (0..1), so the layout keeps its
 * proportions on any phone, ultrawide included. scale goes 0.5..2, alpha 0.1..1.
 */
data class Slot(
    val control: Control,
    var x: Float,
    var y: Float,
    var scale: Float = 1f,
    var alpha: Float = 0.8f,
)

object Layouts {
    fun preset(profile: Profile): MutableList<Slot> {
        val list = mutableListOf(
            Slot(Control.TRIANGLE, 0.17f, 0.62f),
            Slot(Control.STICK, 0.11f, 0.80f),
            Slot(Control.SQUARE, 0.83f, 0.66f),
            Slot(Control.CIRCLE, 0.80f, 0.84f),
            Slot(Control.CROSS, 0.89f, 0.82f),
            Slot(Control.START, 0.50f, 0.08f, alpha = 0.6f),
        )
        if (profile == Profile.CORTEX) {
            list.add(Slot(Control.L1, 0.05f, 0.62f))
            list.add(Slot(Control.R1, 0.93f, 0.62f))
        } else {
            list.add(Slot(Control.R1, 0.05f, 0.62f))
        }
        return list
    }
}
