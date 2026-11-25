// JNI entry for libcrashtwin_engine.so (milestone 1). It runs the engine's main (entry.cpp, renamed twin_engine_main by
// the build) with the disc given as a path. From the app the path is /proc/self/fd/<n> of the descriptor the file picker
// gave: Native::Disc::Open opens it with open(), so the disc is read in place, nothing is copied.
// Not wired into the app yet: SDL needs its Android glue (SDLActivity) before a window can be made. See README.md.
#include <jni.h>

#include <string>
#include <vector>

extern "C" int twin_engine_main(int argc, char** argv);

extern "C" JNIEXPORT jint JNICALL
Java_com_crashtwinsanity_app_NativeBridge_nativeRunEngine(JNIEnv* env, jclass, jstring disc)
{
    const char* path = env->GetStringUTFChars(disc, nullptr);
    std::string iso = std::string("--iso=") + path;
    env->ReleaseStringUTFChars(disc, path);

    std::vector<char*> args{const_cast<char*>("crashtwin"), iso.data()};
    return twin_engine_main(static_cast<int>(args.size()), args.data());
}
