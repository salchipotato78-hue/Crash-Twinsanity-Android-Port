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

/**
 * Every touch control: its drawable, its pad bit and its width as a fraction of the screen width at scale 1.
 * The widths are measured on the reference pictures (layout_de_cortex, layout_de_crash_y_nina: 1386 px wide).
 */
enum class Control(val drawable: String, val padBit: Int, val widthFraction: Float, val isStick: Boolean = false) {
    CROSS("btn_cross", Pad.CROSS, 0.1089f),
    CIRCLE("btn_circle", Pad.CIRCLE, 0.0678f),
    SQUARE("btn_square", Pad.SQUARE, 0.0707f),
    TRIANGLE("btn_triangle", Pad.TRIANGLE, 0.0505f),
    L1("btn_l1", Pad.L1, 0.0707f),
    R1("btn_r1", Pad.R1, 0.0815f),
    START("btn_start", Pad.START, 0.047f),
    STICK("joy_base", 0, 0.1270f, isStick = true),
}

/**
 * One control's place. x and y are its centre as fractions of the screen (0..1), measured on the reference
 * pictures. scale multiplies the measured size (edit mode: 0.5..2), alpha is the opacity (0.1..1).
 */
data class Slot(
    val control: Control,
    var x: Float,
    var y: Float,
    var scale: Float = 1f,
    var alpha: Float = 1f,
)

/**
 * The layout from the reference pictures. There is no profile to choose: the pad is always this one.
 * (crashNina() is measured from layout_de_crash_y_nina; the app uses it once the engine reports that Crash is
 * the character being played.)
 */
object Layouts {
    fun cortex(): MutableList<Slot> = mutableListOf(
        Slot(Control.L1, 0.0382f, 0.6273f),
        Slot(Control.TRIANGLE, 0.1587f, 0.6242f),
        Slot(Control.STICK, 0.1017f, 0.7867f),
        Slot(Control.SQUARE, 0.8297f, 0.6883f),
        Slot(Control.R1, 0.9354f, 0.6617f),
        Slot(Control.CIRCLE, 0.8038f, 0.8375f),
        Slot(Control.CROSS, 0.8914f, 0.8242f),
        Slot(Control.START, 0.5000f, 0.0780f),
    )

    fun crashNina(): MutableList<Slot> = mutableListOf(
        Slot(Control.R1, 0.0379f, 0.6289f),
        Slot(Control.TRIANGLE, 0.1587f, 0.6242f),
        Slot(Control.STICK, 0.1017f, 0.7867f),
        Slot(Control.SQUARE, 0.8297f, 0.6883f),
        Slot(Control.CIRCLE, 0.9520f, 0.6875f),
        Slot(Control.CROSS, 0.8943f, 0.8242f),
        Slot(Control.START, 0.5000f, 0.0780f),
    )
}
