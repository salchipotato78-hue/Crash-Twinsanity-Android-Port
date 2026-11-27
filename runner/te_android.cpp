// runner/te_android.cpp -- see te_android.h.
#include "te_android.h"

#include <android/log.h>

#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#define TE_ALOG(...) __android_log_print(ANDROID_LOG_INFO, "twinsanityevolved", __VA_ARGS__)

namespace te::android {
namespace {
int g_iso_fd = -1;
std::string g_files_dir;
std::vector<std::string> g_args;
std::vector<char*> g_argv;
}  // namespace

void SetLaunch(int iso_fd, const char* files_dir) {
    g_iso_fd = iso_fd;
    g_files_dir = files_dir ? files_dir : "";
}

void Startup(int* argc, char*** argv) {
    if (!g_files_dir.empty()) setenv("HOME", g_files_dir.c_str(), 1);
    g_args.clear();
    g_args.push_back("twinsanityevolved");
    if (g_iso_fd >= 0) {
        g_args.push_back("--iso");
        g_args.push_back("/proc/self/fd/" + std::to_string(g_iso_fd));
    } else {
        std::fprintf(stderr, "[twinsanityevolved] android: no disc descriptor; the game cannot start\n");
    }
    g_argv.clear();
    for (std::string& a : g_args) g_argv.push_back(a.data());
    g_argv.push_back(nullptr);
    *argc = static_cast<int>(g_argv.size()) - 1;
    *argv = g_argv.data();
    // the launch as main() gets it: whether the descriptor's path opens here, and the folder HOME names
    TE_ALOG("launch: argc=%d HOME=%s", *argc, std::getenv("HOME") ? std::getenv("HOME") : "(unset)");
    for (int i = 0; i < *argc; ++i) TE_ALOG("launch: argv[%d]=%s", i, (*argv)[i]);
    if (g_iso_fd >= 0) {
        const std::string path = "/proc/self/fd/" + std::to_string(g_iso_fd);
        FILE* probe = std::fopen(path.c_str(), "rb");
        TE_ALOG("launch: %s %s (errno %d %s)", path.c_str(), probe ? "opens" : "does NOT open", errno,
                probe ? "" : std::strerror(errno));
        if (probe) std::fclose(probe);
    }
}

}  // namespace te::android
