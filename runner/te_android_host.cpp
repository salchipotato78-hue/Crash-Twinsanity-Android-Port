// runner/te_android_host.cpp -- the guest-host functions main.cpp owns on the desktop (GuestMemory, GuestMemorySize,
// GuestString, RequestExit), for the Android library, which has no main(). They read the environment the Android entry
// binds with te_android_bind (main() binds g_env on the desktop). Until it is bound, guest memory reads as empty.
#include <algorithm>
#include <cstdlib>
#include <cstring>

#include "te_env.h"
#include "te_game.h"
#include "te_host.h"

namespace {
w2c_env* g_android_env = nullptr;
}  // namespace

namespace te {

// The Android entry calls this once the guest environment exists (the same role as main()'s g_env binding).
void AndroidBindEnv(w2c_env* env) { g_android_env = env; }

uint8_t* GuestMemory() { return g_android_env ? g_android_env->memory.data : nullptr; }
std::size_t GuestMemorySize() { return g_android_env ? g_android_env->memory.size : 0; }

const char* GuestString(uint32_t address, std::size_t max) {
    const uint8_t* p = GuestPointer(address, 1);
    if (!p) return nullptr;
    const std::size_t room = std::min<std::size_t>(max, GuestMemorySize() - address);
    return std::memchr(p, 0, room) ? reinterpret_cast<const char*>(p) : nullptr;
}

[[noreturn]] void RequestExit(int status) {
    te::game::ExitFromGame(status);   // returns only when not on the game fiber
    std::exit(status);
}

}  // namespace te
