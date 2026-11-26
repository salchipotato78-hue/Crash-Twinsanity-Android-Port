// platform/android/fiber_android.h -- the Android fiber backend behind the Platform fiber entries and the host
// coroutines: Bionic has no <ucontext.h>, so the context switch is an AAPCS64 register save/load (x19-x30, sp, d8-d15)
// over a heap stack, as the Vita lane does in AAPCS32. Same contract as FiberPosix.
#pragma once

#include <cstddef>

#include "engine/platform/platform.h"

namespace ps2r::platform {

class FiberAndroid {
public:
    // A heap stack of `stack` bytes (no guard page: an overflow corrupts the heap instead of faulting) and a context
    // that runs fn(arg) on the first switch. fn must never return.
    static Fiber Create(void (*fn)(void*), void* arg, size_t stack);
    static void Switch(Fiber to);
    static Fiber Current();
    static void Destroy(Fiber f);
};

}  // namespace ps2r::platform
