# libcrashtwin.so for the Android app. Built by the workflow with the NDK's CMake toolchain:
#   cmake -S android/native -B build/native-android -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
#         -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DCRASHTWIN_OUT=<jniLibs/arm64-v8a>
cmake_minimum_required(VERSION 3.22)
project(crashtwin LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(crashtwin SHARED src/crashtwin_jni.cpp src/iso9660.cpp)
target_link_libraries(crashtwin android log EGL GLESv3)

if(CRASHTWIN_OUT)
    set_target_properties(crashtwin PROPERTIES LIBRARY_OUTPUT_DIRECTORY "${CRASHTWIN_OUT}")
endif()
