// platform/sdl/input/android_pad_backend.cpp -- see android_pad_backend.h.
#include "engine/platform/sdl/input/android_pad_backend.h"

#include <cstdint>

#if defined(__ANDROID__)
// Defined in android/native/src/twinsanityevolved_jni.cpp (the same library): the last pad state the touch overlay sent.
extern "C" void te_android_pad_read(uint32_t* bits, float* lx, float* ly);
#endif

namespace ps2r::platform::sdl {

namespace {
// The Java Pad bits (android/app Layouts.kt): CROSS 1, CIRCLE 2, SQUARE 4, TRIANGLE 8, L1 16, R1 32, START 64.
constexpr uint32_t kJavaCross = 1, kJavaCircle = 2, kJavaSquare = 4, kJavaTriangle = 8,
                   kJavaL1 = 16, kJavaR1 = 32, kJavaStart = 64;
// A push past half-way on the stick is also a d-pad press: the PS2 menus read the d-pad.
constexpr float kDpadThreshold = 0.5f;
}  // namespace

bool AndroidPadBackend::IsActive() const {
#if defined(__ANDROID__)
    return true;
#else
    return false;
#endif
}

void AndroidPadBackend::Poll(NormalizedInput& out) {
#if defined(__ANDROID__)
    uint32_t bits = 0;
    float lx = 0.f, ly = 0.f;   // ly: up positive, as the Java side sends it
    te_android_pad_read(&bits, &lx, &ly);

    uint32_t b = 0;
    if (bits & kJavaCross)    b |= kBtnCross;
    if (bits & kJavaCircle)   b |= kBtnCircle;
    if (bits & kJavaSquare)   b |= kBtnSquare;
    if (bits & kJavaTriangle) b |= kBtnTriangle;
    if (bits & kJavaL1)       b |= kBtnL1;
    if (bits & kJavaR1)       b |= kBtnR1;
    if (bits & kJavaStart)    b |= kBtnStart;
    if (lx >  kDpadThreshold) b |= kBtnRight;
    if (lx < -kDpadThreshold) b |= kBtnLeft;
    if (ly >  kDpadThreshold) b |= kBtnUp;
    if (ly < -kDpadThreshold) b |= kBtnDown;
    out.buttons |= b;

    // NormalizedInput's sticks run y DOWN positive: the Java y-up value is negated here.
    out.lx = lx;
    out.ly = -ly;
#else
    (void)out;
#endif
}

}  // namespace ps2r::platform::sdl
