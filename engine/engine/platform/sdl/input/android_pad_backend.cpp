// platform/sdl/input/android_pad_backend.cpp -- see android_pad_backend.h.
#include "engine/platform/sdl/input/android_pad_backend.h"

#include <cstdint>

#if defined(__ANDROID__)
// Defined in android/native/src/twinsanityevolved_jni.cpp (the same library): the last pad state the touch overlay sent.
extern "C" void te_android_pad_read(uint32_t* bits, float* lx, float* ly);
// The finger's drag since the last take, in dp (android/native): the camera's mouse look.
extern "C" void te_android_look_take(float* dx, float* dy);
extern "C" bool te_android_menu_open();
#endif

namespace ps2r::platform::sdl {

namespace {
// The Java Pad bits (android/app Layouts.kt): CROSS 1, CIRCLE 2, SQUARE 4, TRIANGLE 8, L1 16, R1 32, START 64.
constexpr uint32_t kJavaCross = 1, kJavaCircle = 2, kJavaSquare = 4, kJavaTriangle = 8,
                   kJavaL1 = 16, kJavaR1 = 32, kJavaStart = 64;
// The touch overlay's d-pad bits (Pad.UP..RIGHT in Layouts.kt): a swipe across the screen moves the menus.
constexpr uint32_t kJavaUp = 128, kJavaDown = 256, kJavaLeft = 512, kJavaRight = 1024;
// A push past half-way on the stick is also a d-pad press: the PS2 menus read the d-pad.
constexpr float kDpadThreshold = 0.5f;
// The mapper reads mouse deltas as pixels (0.35 stick units per 100 px): one dp of drag is a mouse pixel.
constexpr float kLookPixelsPerDp = 1.0f;
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
    if (bits & kJavaUp)       b |= kBtnUp;
    if (bits & kJavaDown)     b |= kBtnDown;
    if (bits & kJavaLeft)     b |= kBtnLeft;
    if (bits & kJavaRight)    b |= kBtnRight;
    // The stick also reads as the d-pad, but only in a menu: in the game a push past half-way on one axis would
    // press a d-pad direction too, which stops Crash on that side.
    if (te_android_menu_open()) {
        if (lx >  kDpadThreshold) b |= kBtnRight;
        if (lx < -kDpadThreshold) b |= kBtnLeft;
        if (ly >  kDpadThreshold) b |= kBtnUp;
        if (ly < -kDpadThreshold) b |= kBtnDown;
    }
    out.buttons |= b;

    // The drag turns the camera: a mouse delta, the mapper's look (mouse = right stick) with its own decay.
    float look_dx = 0.f, look_dy = 0.f;
    te_android_look_take(&look_dx, &look_dy);
    out.mouse_dx += look_dx * kLookPixelsPerDp;
    out.mouse_dy += look_dy * kLookPixelsPerDp;

    // NormalizedInput's sticks run y DOWN positive: the Java y-up value is negated here.
    out.lx = lx;
    out.ly = -ly;
#else
    (void)out;
#endif
}

}  // namespace ps2r::platform::sdl
