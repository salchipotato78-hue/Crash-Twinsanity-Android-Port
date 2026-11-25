package com.crashtwinsanity.app

import android.view.Surface

/**
 * The engine's entry points, in the JNI library "crashtwin". Until that library is linked the app still runs:
 * every call goes through the wrappers below, which do nothing when the library is missing.
 */
object NativeBridge {
    val available: Boolean = try {
        System.loadLibrary("crashtwin")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    @JvmStatic private external fun nativeStart(isoFd: Int): Boolean
    @JvmStatic private external fun nativeSurface(surface: Surface?, width: Int, height: Int, aspect: Float)
    @JvmStatic private external fun nativeSetPad(bits: Int, lx: Float, ly: Float)
    @JvmStatic private external fun nativeStop()

    /** Opens the disc (the file descriptor is handed over) and starts the engine. */
    fun start(isoFd: Int): Boolean = available && nativeStart(isoFd)

    /** Attaches the drawing surface (null detaches). aspect is the picture's real width/height: nothing is stretched. */
    fun surface(surface: Surface?, width: Int, height: Int, aspect: Float) {
        if (available) nativeSurface(surface, width, height, aspect)
    }

    /** Pad state: buttons as Pad bits, the left stick from -1..1 (y up positive). */
    fun setPad(bits: Int, lx: Float, ly: Float) {
        if (available) nativeSetPad(bits, lx, ly)
    }

    fun stop() {
        if (available) nativeStop()
    }
}
