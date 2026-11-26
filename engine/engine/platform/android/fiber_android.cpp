// platform/android/fiber_android.cpp -- AAPCS64 fibers for Android arm64. Callee-saved state is x19-x28, x29 (fp),
// x30 (lr), sp and d8-d15; the context layout is the switch's: sp at 0, x19..x30 at 8..96 (12 words), d8..d15 at 104.
// The same layout as runner/te_fiber_switch.S (the desktop arm64 runner), so the two stay interchangeable.
#include "engine/platform/android/fiber_android.h"

#include <cstdint>
#include <cstdio>
#include <cstdlib>

#include "engine/platform/host/platform.h"

#if !defined(__aarch64__)
#error "engine/platform/android/fiber_android.cpp is the arm64 fiber switch; the 32-bit ABIs are not built for Android"
#endif

namespace ps2r::platform {
namespace {

struct FiberContext {
    uint64_t sp;
    uint64_t r[12];   // x19..x28, x29 (fp), x30 (lr)
    uint64_t d[8];    // d8..d15
};
static_assert(offsetof(FiberContext, r) == 8 && offsetof(FiberContext, d) == 104, "FiberContext: the switch's offsets");

struct FiberRecord {
    FiberContext ctx{};
    uint8_t*     stack = nullptr;   // null for a host thread's own record
    void       (*entry)(void*) = nullptr;
    void*        arg = nullptr;
};

thread_local FiberRecord* t_current = nullptr;

FiberRecord* HostRecord() {
    if (!t_current) t_current = new FiberRecord();
    return t_current;
}

}  // namespace
}  // namespace ps2r::platform

extern "C" void ps2r_android_fiber_switch(ps2r::platform::FiberContext* save,
                                          const ps2r::platform::FiberContext* load);
extern "C" void ps2r_android_fiber_trampoline();

// save into *x0, load *x1, return into the loaded x30
asm(R"(
    .text
    .balign 4
    .globl ps2r_android_fiber_switch
    .type ps2r_android_fiber_switch, %function
ps2r_android_fiber_switch:
    mov     x2, sp
    str     x2, [x0, #0]
    stp     x19, x20, [x0, #8]
    stp     x21, x22, [x0, #24]
    stp     x23, x24, [x0, #40]
    stp     x25, x26, [x0, #56]
    stp     x27, x28, [x0, #72]
    stp     x29, x30, [x0, #88]
    stp     d8,  d9,  [x0, #104]
    stp     d10, d11, [x0, #120]
    stp     d12, d13, [x0, #136]
    stp     d14, d15, [x0, #152]
    ldr     x2, [x1, #0]
    mov     sp, x2
    ldp     x19, x20, [x1, #8]
    ldp     x21, x22, [x1, #24]
    ldp     x23, x24, [x1, #40]
    ldp     x25, x26, [x1, #56]
    ldp     x27, x28, [x1, #72]
    ldp     x29, x30, [x1, #88]
    ldp     d8,  d9,  [x1, #104]
    ldp     d10, d11, [x1, #120]
    ldp     d12, d13, [x1, #136]
    ldp     d14, d15, [x1, #152]
    ret
    .size ps2r_android_fiber_switch, .-ps2r_android_fiber_switch

    .globl ps2r_android_fiber_trampoline
    .type ps2r_android_fiber_trampoline, %function
ps2r_android_fiber_trampoline:
    mov     x0, x20
    blr     x19
    brk     #0
    .size ps2r_android_fiber_trampoline, .-ps2r_android_fiber_trampoline
)");

namespace ps2r::platform {
namespace {

// First entry of a fiber: the trampoline calls Entry(record) with x19 = Entry, x20 = the record.
void Entry(void* p) {
    auto* r = static_cast<FiberRecord*>(p);
    r->entry(r->arg);
    std::fprintf(stderr, "platform: fiber entry returned; a fiber body must park forever\n");
    std::abort();
}

}  // namespace

Fiber FiberAndroid::Create(void (*fn)(void*), void* arg, size_t stack) {
    Fiber f;
    if (!fn) return f;
    if (stack < 64 * 1024) stack = 64 * 1024;
    stack = (stack + 15) & ~static_cast<size_t>(15);
    auto* r = new FiberRecord();
    r->stack = static_cast<uint8_t*>(std::malloc(stack));
    if (!r->stack) {
        delete r;
        return f;
    }
    r->entry = fn;
    r->arg = arg;
    const uintptr_t top = (reinterpret_cast<uintptr_t>(r->stack) + stack) & ~static_cast<uintptr_t>(15);
    r->ctx.sp = static_cast<uint64_t>(top);
    r->ctx.r[0] = static_cast<uint64_t>(reinterpret_cast<uintptr_t>(&Entry));                       // x19
    r->ctx.r[1] = static_cast<uint64_t>(reinterpret_cast<uintptr_t>(r));                            // x20
    r->ctx.r[10] = 0;                                                                               // x29: the frame chain ends here
    r->ctx.r[11] = static_cast<uint64_t>(reinterpret_cast<uintptr_t>(&ps2r_android_fiber_trampoline));  // x30
    f.handle = r;
    return f;
}

void FiberAndroid::Switch(Fiber to) {
    auto* target = static_cast<FiberRecord*>(to.handle);
    if (!target) return;
    FiberRecord* from = HostRecord();
    if (from == target) return;
    t_current = target;
    ps2r_android_fiber_switch(&from->ctx, &target->ctx);
    // Resumed: whoever switched back to us set t_current before switching.
    t_current = from;
}

Fiber FiberAndroid::Current() {
    Fiber f;
    f.handle = HostRecord();
    return f;
}

void FiberAndroid::Destroy(Fiber f) {
    auto* r = static_cast<FiberRecord*>(f.handle);
    if (!r || !r->stack) return;   // host records are never destroyed
    std::free(r->stack);
    delete r;
}

}  // namespace ps2r::platform
