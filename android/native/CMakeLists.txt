# libtwinsanityevolved.so for the Android app. Built by .github/workflows/android.yml with the NDK's CMake toolchain:
#   cmake -S android/native -B build/native-android -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
#         -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DCRASHTWIN_OUT=<jniLibs/arm64-v8a>
# Today it only checks the disc and draws. The engine (TwinsanityEvolved's wasm2c build on NativeEE + SDL2) is linked in
# the next milestone: android/README.md has the plan.
cmake_minimum_required(VERSION 3.22)
project(twinsanityevolved_android LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(twinsanityevolved SHARED src/twinsanityevolved_jni.cpp src/iso9660.cpp)
target_link_libraries(twinsanityevolved android log EGL GLESv3)

if(CRASHTWIN_OUT)
    set_target_properties(twinsanityevolved PROPERTIES LIBRARY_OUTPUT_DIRECTORY "${CRASHTWIN_OUT}")
endif()
