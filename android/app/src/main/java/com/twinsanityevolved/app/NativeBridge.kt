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

    @JvmStatic private external fun nativeStart(isoFd: Int): Boolean
    @JvmStatic private external fun nativeSetPad(bits: Int, lx: Float, ly: Float)
    @JvmStatic private external fun nativeStop()
    @JvmStatic private external fun nativeStatus(): String

    /** Opens the disc (the file descriptor is handed over) and checks it. */
    fun start(isoFd: Int): Boolean = available && nativeStart(isoFd)

    /** Pad state: buttons as Pad bits, the left stick from -1..1 (y up positive). */
    fun setPad(bits: Int, lx: Float, ly: Float) {
        if (available) nativeSetPad(bits, lx, ly)
    }

    fun stop() {
        if (available) nativeStop()
    }

    /** What the native side last said: the disc check, or that the engine is stopped. */
    fun status(): String = if (available) nativeStatus() else ""
}
