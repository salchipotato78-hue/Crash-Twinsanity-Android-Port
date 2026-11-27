// platform/android/fiber_android.cpp -- Android fibers as threads that hand control to each other.
//
// A fiber's stack must be one ART knows: a thread's own stack. A heap stack switched in assembly (the desktop-style
// switch) turns every JNI call made from it into a StackOverflowError, and SDL's Android backend calls JNI for its
// first environment read, the audio device, the windows and the input. So each fiber is a pthread that runs only while
// it holds the baton: exactly one fiber runs at a time, as with fibers, and every JNI call comes from a real thread stack.
#include "engine/platform/android/fiber_android.h"

#include <pthread.h>

#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <mutex>

namespace ps2r::platform {
namespace {

struct FiberRecord {
    std::condition_variable cv;
    bool go = false;                  // this fiber may run now
    void (*entry)(void*) = nullptr;
    void* arg = nullptr;
    pthread_t thread{};
};

std::mutex g_mu;
FiberRecord* g_current = nullptr;     // the fiber that runs; only one does

// The first fiber to run is the calling thread's own (the engine's main or SDL's thread): it gets a record too.
FiberRecord* CurrentLocked() {
    if (!g_current) g_current = new FiberRecord();
    return g_current;
}

void* ThreadMain(void* p) {
    auto* r = static_cast<FiberRecord*>(p);
    {
        std::unique_lock<std::mutex> lock(g_mu);
        r->cv.wait(lock, [r] { return r->go; });
        r->go = false;
    }
    r->entry(r->arg);
    std::fprintf(stderr, "platform: fiber entry returned; a fiber body must park forever\n");
    std::abort();
}

}  // namespace

Fiber FiberAndroid::Create(void (*fn)(void*), void* arg, size_t stack) {
    Fiber f;
    if (!fn) return f;
    if (stack < 256 * 1024) stack = 256 * 1024;
    auto* r = new FiberRecord();
    r->entry = fn;
    r->arg = arg;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, stack);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    const int rc = pthread_create(&r->thread, &attr, ThreadMain, r);
    pthread_attr_destroy(&attr);
    if (rc != 0) {
        delete r;
        return f;
    }
    f.handle = r;
    return f;
}

void FiberAndroid::Switch(Fiber to) {
    auto* target = static_cast<FiberRecord*>(to.handle);
    std::unique_lock<std::mutex> lock(g_mu);
    FiberRecord* from = CurrentLocked();
    if (!target || target == from) return;
    g_current = target;
    target->go = true;
    target->cv.notify_one();
    from->cv.wait(lock, [from] { return from->go; });   // parked until another fiber switches back
    from->go = false;
    g_current = from;
}

Fiber FiberAndroid::Current() {
    std::lock_guard<std::mutex> lock(g_mu);
    Fiber f;
    f.handle = CurrentLocked();
    return f;
}

void FiberAndroid::Destroy(Fiber f) {
    // A parked fiber's thread waits on its record for good; freeing either would be a use-after-free. Its record and
    // thread stay (a detached thread costs its stack, not the CPU). Destroying the running fiber is not a thing the
    // engine does.
    (void)f;
}

}  // namespace ps2r::platform
