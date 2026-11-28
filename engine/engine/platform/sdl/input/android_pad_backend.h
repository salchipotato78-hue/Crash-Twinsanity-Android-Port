// platform/sdl/input/android_pad_backend.h -- the Android touch controls as an input backend.
// The Java side (android/app TouchOverlay) publishes the pad through NativeBridge.nativeSetPad: the Pad bits and the left
// stick (y up). The JNI library keeps that state and exposes it through te_android_pad_read; this backend turns it into
// the NormalizedInput the mapper reads. On other platforms it is idle.
//
// The same file carries the Android gamepad button map: the app's "Controller" menu sets it as text ("cross=a" lines, the
// bindings.ini [pad] names) through te_android_set_pad_map, and the input system takes each new map once per change on
// the game thread (AndroidPadMapSince).
#pragma once

#include <cstdint>

#include "engine/platform/sdl/input/input_backend.h"
#include "engine/platform/sdl/input/pad_button_map.h"

namespace ps2r::platform::sdl {

class AndroidPadBackend : public InputBackend {
public:
    void Poll(NormalizedInput& out) override;
    bool IsActive() const override;
    const char* Name() const override { return "android-touch"; }
};

// A gamepad button map set by the Android app since `seen` (a generation counter the caller keeps, 0 at first). Returns
// true and fills `out` when there is a newer one; false otherwise, and always false off Android.
bool AndroidPadMapSince(uint64_t& seen, PadButtonMap& out);

}  // namespace ps2r::platform::sdl
