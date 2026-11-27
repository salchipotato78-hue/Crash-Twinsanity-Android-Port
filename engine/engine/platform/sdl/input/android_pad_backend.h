// platform/sdl/input/android_pad_backend.h -- the Android touch controls as an input backend.
// The Java side (android/app TouchOverlay) publishes the pad through NativeBridge.nativeSetPad: the Pad bits and the left
// stick (y up). The JNI library keeps that state and exposes it through te_android_pad_read; this backend turns it into
// the NormalizedInput the mapper reads. On other platforms it is idle.
#pragma once

#include "engine/platform/sdl/input/input_backend.h"

namespace ps2r::platform::sdl {

class AndroidPadBackend : public InputBackend {
public:
    void Poll(NormalizedInput& out) override;
    bool IsActive() const override;
    const char* Name() const override { return "android-touch"; }
};

}  // namespace ps2r::platform::sdl
