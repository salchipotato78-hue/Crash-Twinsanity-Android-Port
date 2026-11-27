// runner/te_android_diag.c -- see te_android_diag.h. Atomics only on the hot path: a mark is a few stores and one log line.
#include "te_android_diag.h"

#include <android/log.h>
#include <pthread.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>

#define TE_DIAG_TAG "TE_DBG"

static const char* g_step = "(none yet)";
static int64_t g_step_ns = 0;
static int g_step_tid = 0;
static uint64_t g_boundary = 0;
static int g_started = 0;
static int64_t g_start_ns = 0;

static int64_t NowNs(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + (int64_t)ts.tv_nsec;
}

void te_diag_mark(const char* step) {
    const int tid = (int)syscall(SYS_gettid);
    __atomic_store_n(&g_step_tid, tid, __ATOMIC_RELAXED);
    __atomic_store_n(&g_step_ns, NowNs(), __ATOMIC_RELEASE);
    __atomic_store_n(&g_step, step, __ATOMIC_RELEASE);
    __android_log_print(ANDROID_LOG_INFO, TE_DIAG_TAG, "step: %s (tid %d)", step, tid);
}

void te_diag_boundary(uint64_t boundary) {
    __atomic_store_n(&g_boundary, boundary, __ATOMIC_RELAXED);
}

static void* Watchdog(void* arg) {
    (void)arg;
    int64_t warned_at = -1;
    for (;;) {
        sleep(2);
        const char* step = __atomic_load_n(&g_step, __ATOMIC_ACQUIRE);
        const int64_t at = __atomic_load_n(&g_step_ns, __ATOMIC_ACQUIRE);
        const int tid = __atomic_load_n(&g_step_tid, __ATOMIC_RELAXED);
        const uint64_t boundary = __atomic_load_n(&g_boundary, __ATOMIC_RELAXED);
        const int64_t now = NowNs();
        const double since = (double)(now - at) / 1e9;
        const double up = (double)(now - g_start_ns) / 1e9;
        __android_log_print(ANDROID_LOG_INFO, TE_DIAG_TAG,
                            "heartbeat up=%.0fs step='%s' (tid %d) since_step=%.1fs boundaries=%llu",
                            up, step, tid, since, (unsigned long long)boundary);
        if (since > 5.0 && at != warned_at) {
            warned_at = at;
            __android_log_print(ANDROID_LOG_WARN, TE_DIAG_TAG,
                                "STALL: no progress after '%s' (tid %d) for %.1fs", step, tid, since);
        }
    }
    return NULL;
}

void te_diag_start(void) {
    if (__atomic_exchange_n(&g_started, 1, __ATOMIC_ACQ_REL)) return;
    g_start_ns = NowNs();
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    pthread_t t;
    if (pthread_create(&t, &attr, Watchdog, NULL) != 0)
        __android_log_print(ANDROID_LOG_WARN, TE_DIAG_TAG, "watchdog: cannot start");
    pthread_attr_destroy(&attr);
}
