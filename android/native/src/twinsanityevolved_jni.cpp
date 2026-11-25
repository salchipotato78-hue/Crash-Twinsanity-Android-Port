// The native side of the Android app (libtwinsanityevolved.so). The Kotlin side is NativeBridge.kt; the engine
// itself is not linked yet. For now the library does the two things the first milestone needs:
//  - reads the disc image (the file descriptor the file picker gives) and finds the game's executable;
//  - draws the surface with OpenGL ES 3: green when the executable was found, red when not.
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <unistd.h>

#include <atomic>
#include <cstdio>
#include <mutex>
#include <string>

#include "iso9660.h"

#define LOG_TAG "twinsanityevolved"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex g_lock;
int g_fd = -1;
bool g_discOk = false;
std::string g_status = "motor no iniciado";

ANativeWindow* g_window = nullptr;
EGLDisplay g_display = EGL_NO_DISPLAY;
EGLContext g_context = EGL_NO_CONTEXT;
EGLSurface g_surface = EGL_NO_SURFACE;

std::atomic<int> g_pad{0};
std::atomic<float> g_stickX{0.0f};
std::atomic<float> g_stickY{0.0f};

// pread on the descriptor the app gave us: the disc is read in place, nothing is copied.
bool ReadDisc(uint64_t offset, void* buffer, size_t length) {
    if (g_fd < 0) return false;
    auto* out = static_cast<uint8_t*>(buffer);
    size_t done = 0;
    while (done < length) {
        const ssize_t n = pread(g_fd, out + done, length - done, static_cast<off_t>(offset + done));
        if (n <= 0) return false;
        done += static_cast<size_t>(n);
    }
    return true;
}

// Looks for the PAL executable and checks it starts with an ELF header.
void CheckDisc() {
    IsoEntry entry;
    std::string error;
    if (!IsoFindRootFile(ReadDisc, "SLES_525.68", &entry, &error)) {
        g_discOk = false;
        g_status = "No se encontró el ejecutable del juego: " + error;
        return;
    }
    uint8_t magic[4] = {0, 0, 0, 0};
    if (!ReadDisc(static_cast<uint64_t>(entry.lba) * 2048, magic, 4) || magic[0] != 0x7f || magic[1] != 'E') {
        g_discOk = false;
        g_status = "El ejecutable del juego no es un programa válido.";
        return;
    }
    g_discOk = true;
    char text[160];
    std::snprintf(text, sizeof(text), "Disco leído: SLES_525.68, %u bytes. El motor aún no está enlazado.", entry.size);
    g_status = text;
    LOGI("%s", text);
}

bool InitEgl(ANativeWindow* window) {
    g_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (g_display == EGL_NO_DISPLAY || !eglInitialize(g_display, nullptr, nullptr)) return false;

    const EGLint configAttributes[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_NONE,
    };
    EGLConfig config = nullptr;
    EGLint count = 0;
    if (!eglChooseConfig(g_display, configAttributes, &config, 1, &count) || count < 1) return false;

    const EGLint contextAttributes[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    g_context = eglCreateContext(g_display, config, EGL_NO_CONTEXT, contextAttributes);
    g_surface = eglCreateWindowSurface(g_display, config, window, nullptr);
    return g_context != EGL_NO_CONTEXT && g_surface != EGL_NO_SURFACE &&
           eglMakeCurrent(g_display, g_surface, g_surface, g_context);
}

void ReleaseEgl() {
    if (g_display != EGL_NO_DISPLAY) {
        eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (g_surface != EGL_NO_SURFACE) eglDestroySurface(g_display, g_surface);
        if (g_context != EGL_NO_CONTEXT) eglDestroyContext(g_display, g_context);
        eglTerminate(g_display);
    }
    g_display = EGL_NO_DISPLAY;
    g_context = EGL_NO_CONTEXT;
    g_surface = EGL_NO_SURFACE;
}

void Draw(int width, int height) {
    if (g_surface == EGL_NO_SURFACE || !eglMakeCurrent(g_display, g_surface, g_surface, g_context)) return;
    glViewport(0, 0, width, height);
    if (g_discOk) {
        glClearColor(0.05f, 0.45f, 0.12f, 1.0f);
    } else {
        glClearColor(0.45f, 0.05f, 0.05f, 1.0f);
    }
    glClear(GL_COLOR_BUFFER_BIT);
    eglSwapBuffers(g_display, g_surface);
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_twinsanityevolved_app_NativeBridge_nativeStart(JNIEnv*, jclass, jint fd) {
    std::lock_guard<std::mutex> lock(g_lock);
    if (g_fd >= 0) close(g_fd);
    g_fd = fd;
    CheckDisc();
    Draw(1, 1);  // a surface may already be there; the next nativeSurface call draws it at full size
    return g_discOk ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_twinsanityevolved_app_NativeBridge_nativeSurface(JNIEnv* env, jclass, jobject surface, jint width,
                                                        jint height, jfloat /*aspect*/) {
    std::lock_guard<std::mutex> lock(g_lock);
    if (surface == nullptr) {
        ReleaseEgl();
        if (g_window != nullptr) ANativeWindow_release(g_window);
        g_window = nullptr;
        return;
    }
    if (g_window == nullptr) {
        g_window = ANativeWindow_fromSurface(env, surface);
        if (g_window == nullptr || !InitEgl(g_window)) {
            LOGI("EGL: cannot start on this surface");
            return;
        }
    }
    Draw(width, height);
}

JNIEXPORT void JNICALL
Java_com_twinsanityevolved_app_NativeBridge_nativeSetPad(JNIEnv*, jclass, jint bits, jfloat lx, jfloat ly) {
    g_pad.store(bits);
    g_stickX.store(lx);
    g_stickY.store(ly);
}

JNIEXPORT void JNICALL
Java_com_twinsanityevolved_app_NativeBridge_nativeStop(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_lock);
    if (g_fd >= 0) close(g_fd);
    g_fd = -1;
    g_discOk = false;
    g_status = "motor detenido";
}

JNIEXPORT jstring JNICALL
Java_com_twinsanityevolved_app_NativeBridge_nativeStatus(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(g_lock);
    return env->NewStringUTF(g_status.c_str());
}

}  // extern "C"
