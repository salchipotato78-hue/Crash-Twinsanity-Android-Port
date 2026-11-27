// runner/te_fiber_android.c -- the game coroutine on Android (see te_fiber.h for the contract).
//
// The desktop switch runs the game on a mapped stack that is not the thread's own. ART knows only the thread's stack,
// so any JNI call the game makes from there (SDL's audio, windows, input) throws StackOverflowError and aborts. Here
// each fiber is a pthread with the requested stack size, and it runs only while it holds the baton: one fiber runs at
// a time, as with the switch, and every JNI call comes from a stack ART knows. map_stack hands out a token: no memory is
// mapped (the threads have their own stacks), and unmap is a no-op.
#include "te_fiber.h"
#include "te_android_diag.h"

#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>

typedef struct TeThreadFiber {
    pthread_cond_t cv;
    int go;                 /* this fiber may run now (under g_mu) */
    void (*fn)(void*);
    void* arg;
    size_t stack_size;
    int has_thread;
} TeThreadFiber;

static pthread_mutex_t g_mu = PTHREAD_MUTEX_INITIALIZER;
static char g_stack_token[1];

static TeThreadFiber* RecordOf(te_fiber_ctx* ctx) {   /* under g_mu */
    if (!ctx->thread_record) {
        TeThreadFiber* r = (TeThreadFiber*)calloc(1, sizeof *r);
        if (!r) {
            fprintf(stderr, "te_fiber: out of memory for a fiber record\n");
            abort();
        }
        pthread_cond_init(&r->cv, NULL);
        ctx->thread_record = r;
    }
    return (TeThreadFiber*)ctx->thread_record;
}

static void* ThreadMain(void* p) {
    TeThreadFiber* r = (TeThreadFiber*)p;
    TE_DIAG_MARK("fiber: game thread running, waiting for the baton");
    pthread_mutex_lock(&g_mu);
    while (!r->go) pthread_cond_wait(&r->cv, &g_mu);
    r->go = 0;
    pthread_mutex_unlock(&g_mu);
    r->fn(r->arg);
    fprintf(stderr, "te_fiber: a game fiber returned; it must switch away for good\n");
    abort();
    return NULL;
}

void te_fiber_switch(te_fiber_ctx* save, const te_fiber_ctx* load) {
    pthread_mutex_lock(&g_mu);
    TeThreadFiber* from = RecordOf(save);
    TeThreadFiber* to = (TeThreadFiber*)load->thread_record;
    if (!to) {
        fprintf(stderr, "te_fiber: switch to a context that was never prepared\n");
        abort();
    }
    if (from != to) {
        to->go = 1;
        pthread_cond_signal(&to->cv);
        while (!from->go) pthread_cond_wait(&from->cv, &g_mu);   /* parked until someone switches back */
        from->go = 0;
    }
    pthread_mutex_unlock(&g_mu);
}

void te_fiber_prepare(te_fiber_ctx* ctx, void* stack, size_t size, void (*fn)(void*), void* arg) {
    (void)stack;
    pthread_mutex_lock(&g_mu);
    TeThreadFiber* r = RecordOf(ctx);
    if (r->has_thread) {
        /* A fiber is prepared once: its thread is already parked in the middle of its body. */
        pthread_mutex_unlock(&g_mu);
        fprintf(stderr, "te_fiber: re-preparing a running fiber is not supported on Android\n");
        abort();
    }
    r->fn = fn;
    r->arg = arg;
    r->stack_size = size >= ((size_t)256 << 10) ? size : ((size_t)256 << 10);
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, r->stack_size);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    pthread_t t;
    const int rc = pthread_create(&t, &attr, ThreadMain, r);
    pthread_attr_destroy(&attr);
    if (rc != 0) {
        pthread_mutex_unlock(&g_mu);
        fprintf(stderr, "te_fiber: cannot create the game thread (%d)\n", rc);
        abort();
    }
    r->has_thread = 1;
    pthread_mutex_unlock(&g_mu);
}

void* te_fiber_map_stack(uint64_t hint, size_t size, int* at_hint) {
    (void)hint;
    (void)size;
    if (at_hint) *at_hint = 0;   /* the fiber's stack is its thread's: never at the hint */
    return g_stack_token;
}

void te_fiber_unmap_stack(void* stack, size_t size) {
    (void)stack;
    (void)size;
}

uint64_t te_fiber_frame_pointer(const te_fiber_ctx* ctx) {
    (void)ctx;
    return 0;   /* the save-state stack walk is not built on Android (te_savestate_null.cpp) */
}
