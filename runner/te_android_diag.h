// runner/te_android_diag.h -- Android-only progress markers and a watchdog thread (te_android_diag.c).
// The markers say which step of the launch the game reached; the watchdog logs them every 2 s under the tag TE_DBG
// and warns when no step has advanced for 5 s. On other platforms every macro is a no-op.
#pragma once
#include <stdint.h>

#if defined(__ANDROID__)
#ifdef __cplusplus
extern "C" {
#endif
void te_diag_start(void);                     /* starts the watchdog (once) */
void te_diag_mark(const char* step);          /* records and logs a step; any thread */
void te_diag_boundary(uint64_t boundary);     /* the game's frame boundary counter */
void te_diag_capture_stdio(void);           /* stdout/stderr -> logcat (tag TE_STDIO) */
#ifdef __cplusplus
}
#endif
#define TE_DIAG_MARK(step) te_diag_mark(step)
#define TE_DIAG_BOUNDARY(b) te_diag_boundary(b)
#else
#define TE_DIAG_MARK(step) ((void)0)
#define TE_DIAG_BOUNDARY(b) ((void)0)
#endif
