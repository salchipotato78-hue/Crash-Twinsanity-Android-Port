// runner/te_android.cpp -- see te_android.h.
#include "te_android.h"

#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

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
}

}  // namespace te::android
