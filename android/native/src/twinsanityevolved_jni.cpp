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
#include "te_android.h"

#if defined(TE_HAVE_FFMPEG)
// FFmpeg's headers have no extern "C" guards of their own: without this wrapper the calls below are C++-mangled and
// the linker cannot find the C symbols in libavcodec.a.
extern "C" {
#include <libavcodec/avcodec.h>
}
#endif

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

// Checks that FFmpeg is linked and that its MPEG-2 video decoder (the movies' decoder) is in it: finds the decoder,
// opens a context on it and frees the context again. Returns a line for the status and for logcat.
std::string FfmpegCheck() {
#if defined(TE_HAVE_FFMPEG)
    const unsigned v = avcodec_version();
    char version[48];
    std::snprintf(version, sizeof(version), "FFmpeg %u.%u.%u", AV_VERSION_MAJOR(v), AV_VERSION_MINOR(v),
                  AV_VERSION_MICRO(v));
    const AVCodec* codec = avcodec_find_decoder(AV_CODEC_ID_MPEG2VIDEO);
    if (!codec) return std::string(version) + ": sin decodificador MPEG-2";
    AVCodecContext* ctx = avcodec_alloc_context3(codec);
    if (!ctx) return std::string(version) + ": sin memoria para el decodificador";
    const int ret = avcodec_open2(ctx, codec, nullptr);
    avcodec_free_context(&ctx);
    if (ret < 0) return std::string(version) + ": el decodificador MPEG-2 no abre";
    return std::string(version) + ": decodificador MPEG-2 listo";
#else
    return "FFmpeg no enlazado";
#endif
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

// runner/main.cpp, compiled with main renamed to te_main: C++ linkage, so declared outside the C block
int te_main(int argc, char** argv);

extern "C" {

// SDL calls this on its own thread (SDLActivity). The engine's main goes here once it is linked; for now it keeps
// the activity alive until the player leaves.

// SDL calls this on its own thread (SDLActivity): the desktop runner's main(), which takes the disc from the
// descriptor nativeStart was given (te_android.cpp).
int SDL_main(int argc, char** argv) { return te_main(argc, argv); }

JNIEXPORT jboolean JNICALL
Java_com_twinsanityevolved_app_NativeBridge_nativeStart(JNIEnv* env, jclass, jint fd, jstring filesDir) {
    std::lock_guard<std::mutex> lock(g_lock);
    if (g_fd >= 0) close(g_fd);
    g_fd = fd;
    g_stop.store(false);
    const char* dir = env->GetStringUTFChars(filesDir, nullptr);
    te::android::SetLaunch(fd, dir);
    env->ReleaseStringUTFChars(filesDir, dir);
    CheckDisc();
    const std::string ffmpeg = FfmpegCheck();
    LOGI("%s", ffmpeg.c_str());
    g_status += " " + ffmpeg + ".";
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
