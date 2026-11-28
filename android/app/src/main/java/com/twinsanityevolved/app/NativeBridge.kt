package com.twinsanityevolved.app

/**
 * The engine's entry points, in the JNI library "twinsanityevolved". SDL draws the picture (SDL_main runs the engine);
 * these calls hand over the disc and the pad. Until the library is linked the app still runs: every call goes through
 * the wrappers below, which do nothing when the library is missing.
 */
object NativeBridge {
    val available: Boolean = try {
        System.loadLibrary("SDL2")
        System.loadLibrary("twinsanityevolved")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    @JvmStatic private external fun nativeStart(isoPath: String, filesDir: String): Boolean
    @JvmStatic private external fun nativeSetPad(bits: Int, lx: Float, ly: Float)
    @JvmStatic private external fun nativeStop()
    @JvmStatic private external fun nativeStatus(): String
    @JvmStatic private external fun nativeMenuOpen(): Boolean
    @JvmStatic private external fun nativeSetScreen(width: Int, height: Int)
    @JvmStatic private external fun nativeLook(dx: Float, dy: Float)
    @JvmStatic private external fun nativeSetPadMap(spec: String)

    /** Opens the disc (the file descriptor is handed over) and checks it. */
    fun start(isoPath: String, filesDir: String): Boolean = available && nativeStart(isoPath, filesDir)

    /** Pad state: buttons as Pad bits, the left stick from -1..1 (y up positive). */
    fun setPad(bits: Int, lx: Float, ly: Float) {
        if (available) nativeSetPad(bits, lx, ly)
    }

    /** The gamepad's button map for the PS2 controls: the "key=value" lines of PadMapping.serialize. */
    fun setPadMap(spec: String) {
        if (available) nativeSetPadMap(spec)
    }

    fun stop() {
        if (available) nativeStop()
    }

    /** A finger's drag in the game, in dp: the camera turns by it (taken by the engine once a frame). */
    fun look(dx: Float, dy: Float) {
        if (available) nativeLook(dx, dy)
    }

    /** The screen's pixels: auto and the wide choices draw at this shape, so the picture fills the screen. */
    fun setScreen(width: Int, height: Int) {
        if (available) nativeSetScreen(width, height)
    }

    /** True while the game shows a menu (title, main menu, new game, pause, gallery). */
    fun menuOpen(): Boolean = available && nativeMenuOpen()

    /** What the native side last said: the disc check, or that the engine is stopped. */
    fun status(): String = if (available) nativeStatus() else ""
}
