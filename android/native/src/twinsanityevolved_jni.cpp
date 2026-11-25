// The native side of the Android app (libtwinsanityevolved.so). SDL owns the window: SDLActivity (Java, from SDL's
// android-project) loads libSDL2.so, then this library, and calls SDL_main below on its own thread. The engine is not
// linked yet: SDL_main waits until nativeStop. The disc check (the file descriptor the picker gives) is the status
// GameActivity shows.
#include <SDL.h>
#include <android/log.h>
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
std::atomic<bool> g_stop{false};
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

}  // namespace

extern "C" {

// SDL calls this on its own thread (SDLActivity). The engine's main goes here once it is linked; for now it keeps
// the activity alive until the player leaves.
int SDL_main(int, char**) {
    LOGI("SDL_main: engine not linked yet; waiting");
    while (!g_stop.load()) {
        SDL_Delay(100);
    }
    LOGI("SDL_main: done");
    return 0;
}

JNIEXPORT jboolean JNICALL
Java_com_twinsanityevolved_app_NativeBridge_nativeStart(JNIEnv*, jclass, jint fd) {
    std::lock_guard<std::mutex> lock(g_lock);
    if (g_fd >= 0) close(g_fd);
    g_fd = fd;
    g_stop.store(false);
    CheckDisc();
    return g_discOk ? JNI_TRUE : JNI_FALSE;
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
    g_stop.store(true);
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
