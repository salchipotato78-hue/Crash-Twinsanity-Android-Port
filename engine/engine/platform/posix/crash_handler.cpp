// platform/posix/crash_handler.cpp -- execinfo backtrace + sigaction; identical on Darwin and glibc.
#include "engine/platform/posix/crash_handler.h"

#include <signal.h>
#include <unistd.h>

#include <cstdio>
#include <cstring>

#include "engine/core/log/ring_log.h"

#if defined(__ANDROID__)
// Bionic has no execinfo.h before API 33 (the build targets 26): the frames come from the unwinder (<unwind.h>,
// every API level) and are named with dladdr, as the glibc path names them with backtrace_symbols_fd.
#include <dlfcn.h>
#include <unwind.h>
#else
#include <execinfo.h>
#endif

namespace ps2r::platform {

namespace {
bool g_installed = false;
char g_ring_path[512] = {0};
const int kSignals[] = {SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGABRT};

void WriteStr(int fd, const char* s) { (void)!write(fd, s, std::strlen(s)); }
}  // namespace

#if defined(__ANDROID__)
namespace {
struct UnwindCollect {
    void** frames;
    int max;
    int n;
};

_Unwind_Reason_Code CollectFrame(struct _Unwind_Context* ctx, void* arg) {
    auto* c = static_cast<UnwindCollect*>(arg);
    if (c->n >= c->max) return _URC_END_OF_STACK;
    const uintptr_t ip = _Unwind_GetIP(ctx);
    if (ip) c->frames[c->n++] = reinterpret_cast<void*>(ip);
    return _URC_NO_REASON;
}

int CaptureFrames(void** frames, int max) {
    UnwindCollect c{frames, max, 0};
    _Unwind_Backtrace(&CollectFrame, &c);
    return c.n;
}
}  // namespace

void CrashHandler::WriteBacktrace(int fd, int skip_frames) {
    void* frames[64];
    const int n = CaptureFrames(frames, 64);
    for (int i = skip_frames; i < n; ++i) {
        Dl_info info{};
        const bool named = dladdr(frames[i], &info) != 0;
        char line[512];
        std::snprintf(line, sizeof(line), "#%02d %p %s (%s)\n", i, frames[i],
                      named && info.dli_fname ? info.dli_fname : "?",
                      named && info.dli_sname ? info.dli_sname : "?");
        WriteStr(fd, line);
    }
}
#else
void CrashHandler::WriteBacktrace(int fd, int skip_frames) {
    void* frames[64];
    const int n = backtrace(frames, 64);
    if (n > skip_frames) backtrace_symbols_fd(frames + skip_frames, n - skip_frames, fd);
}
#endif

void CrashHandler::OnSignal(int sig) {
    WriteStr(2, "\n=== fatal signal ");
    WriteStr(2, sig == SIGSEGV ? "SIGSEGV" : sig == SIGBUS ? "SIGBUS" : sig == SIGILL ? "SIGILL"
             : sig == SIGFPE ? "SIGFPE" : sig == SIGABRT ? "SIGABRT" : "?");
    WriteStr(2, " ===\n");
    WriteBacktrace(2, 2);
    if (g_ring_path[0]) ringlog::RingLog::DumpAllToPath(g_ring_path, ringlog::DUMP_CRASH);
    signal(sig, SIG_DFL);
    raise(sig);
}

void CrashHandler::Install(const char* ring_dump_path) {
    if (ring_dump_path) {
        std::strncpy(g_ring_path, ring_dump_path, sizeof(g_ring_path) - 1);
        g_ring_path[sizeof(g_ring_path) - 1] = 0;
    }
    if (g_installed) return;
    g_installed = true;
    struct sigaction sa;
    std::memset(&sa, 0, sizeof(sa));
    sa.sa_handler = &CrashHandler::OnSignal;
    sa.sa_flags = SA_RESETHAND | SA_NODEFER;
    sigemptyset(&sa.sa_mask);
    for (int s : kSignals) sigaction(s, &sa, nullptr);
}

bool CrashHandler::Installed() { return g_installed; }

}  // namespace ps2r::platform
