// platform/sdl/input/android_pad_backend.cpp -- see android_pad_backend.h.
#include "engine/platform/sdl/input/android_pad_backend.h"

#include <cstdint>
#include <cstdlib>
#include <mutex>
#include <string>

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

#if defined(__ANDROID__)
namespace {
// The LogicalButton bit of each [pad] name the Android app writes (the bindings.ini [pad] section's keys).
struct PadName {
    const char* name;
    uint32_t bit;
};
constexpr PadName kPadNames[] = {
    {"cross", kBtnCross},       {"circle", kBtnCircle},   {"square", kBtnSquare}, {"triangle", kBtnTriangle},
    {"l1", kBtnL1},             {"r1", kBtnR1},           {"l2", kBtnL2},         {"r2", kBtnR2},
    {"l3", kBtnL3},             {"r3", kBtnR3},           {"start", kBtnStart},   {"select", kBtnSelect},
    {"up", kBtnUp},             {"down", kBtnDown},       {"left", kBtnLeft},     {"right", kBtnRight},
};

std::string Trim(const std::string& s) {
    const size_t b = s.find_first_not_of(" \t\r");
    const size_t e = s.find_last_not_of(" \t\r");
    return b == std::string::npos ? std::string() : s.substr(b, e - b + 1);
}

// "cross=a" lines, one per control, over SDL's standard layout. A line it cannot read keeps the control's default.
PadButtonMap ParsePadMapText(const std::string& text) {
    PadButtonMap map;
    size_t pos = 0;
    while (pos <= text.size()) {
        size_t end = text.find('\n', pos);
        if (end == std::string::npos) end = text.size();
        const std::string line = text.substr(pos, end - pos);
        pos = end + 1;
        const size_t eq = line.find('=');
        if (eq == std::string::npos) continue;
        const std::string key = Trim(line.substr(0, eq));
        const std::string value = Trim(line.substr(eq + 1));
        for (const PadName& n : kPadNames) {
            if (key != n.name) continue;
            PadControl control;
            if (!PadButtonMap::ControlFromName(value, control)) break;
            int index = 0;
            while (index < 31 && (1u << index) != n.bit) index++;
            map.Set(index, control);
            break;
        }
    }
    return map;
}

std::mutex g_map_lock;
std::string g_map_text;          // the last text the app set
uint64_t g_map_generation = 0;   // counts the sets: 0 = never set
}  // namespace

// NativeBridge.setPadMap (android/native/src/twinsanityevolved_jni.cpp): the app's gamepad button map as text.
extern "C" void te_android_set_pad_map(const char* text) {
    std::lock_guard<std::mutex> lock(g_map_lock);
    g_map_text = text ? text : "";
    ++g_map_generation;
}
#endif

bool AndroidPadMapSince(uint64_t& seen, PadButtonMap& out) {
#if defined(__ANDROID__)
    std::lock_guard<std::mutex> lock(g_map_lock);
    if (g_map_generation == 0 || g_map_generation == seen) return false;
    seen = g_map_generation;
    out = ParsePadMapText(g_map_text);
    return true;
#else
    (void)seen;
    (void)out;
    return false;
#endif
}

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
