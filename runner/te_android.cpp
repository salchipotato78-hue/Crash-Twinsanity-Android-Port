// runner/te_android.cpp -- see te_android.h.
#include "te_android.h"
#include "te_android_diag.h"

#include <SDL.h>
#include <android/log.h>

#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

#define TE_ALOG(...) __android_log_print(ANDROID_LOG_INFO, "twinsanityevolved", __VA_ARGS__)

namespace te::android {
namespace {
std::string g_iso_path;
std::string g_files_dir;
std::vector<std::string> g_args;
std::vector<char*> g_argv;
}  // namespace

void SetLaunch(const char* iso_path, const char* files_dir) {
    g_iso_path = iso_path ? iso_path : "";
    g_files_dir = files_dir ? files_dir : "";
}

void Startup(int* argc, char*** argv) {
    te_diag_start();
    TE_DIAG_MARK("startup: SDL events init");
    // SDL's first environment read asks the Java side for the manifest's variables (Android_JNI_GetManifestEnvironmentVariables).
    // That JNI call must run here, on SDL's own thread: from the game's fiber (a malloc'd stack that ART does not know)
    // it raises a StackOverflowError and aborts. SDL_InitSubSystem(SDL_INIT_EVENTS) runs the same first read before the fiber.
    (void)SDL_getenv("SDL_VIDEO_ALLOW_SCREENSAVER");
    SDL_InitSubSystem(SDL_INIT_EVENTS);
    if (!g_files_dir.empty()) setenv("HOME", g_files_dir.c_str(), 1);
    g_args.clear();
    g_args.push_back("twinsanityevolved");
    if (!g_iso_path.empty()) {
        g_args.push_back("--iso");
        g_args.push_back(g_iso_path);
    } else {
        TE_ALOG("launch: no disc path; main() will ask for one");
    }
    g_argv.clear();
    for (std::string& a : g_args) g_argv.push_back(a.data());
    g_argv.push_back(nullptr);
    *argc = static_cast<int>(g_argv.size()) - 1;
    *argv = g_argv.data();
    TE_DIAG_MARK("startup: argv ready, handing over to main()");
    TE_ALOG("launch: argc=%d HOME=%s", *argc, std::getenv("HOME") ? std::getenv("HOME") : "(unset)");
    for (int i = 0; i < *argc; ++i) TE_ALOG("launch: argv[%d]=%s", i, (*argv)[i]);
}

}  // namespace te::android
