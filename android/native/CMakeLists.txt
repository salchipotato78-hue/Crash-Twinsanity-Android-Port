# libtwinsanityevolved.so for the Android app, and SDL2 built from source beside it (libSDL2.so).
# Built by .github/workflows/android.yml with the NDK's CMake toolchain and an SDL 2.32 source tree:
#   cmake -S android/native -B build/native-android -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
#         -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DCRASHTWIN_SDL_SRC=<sdl source> -DCRASHTWIN_OUT=<jniLibs/arm64-v8a>
cmake_minimum_required(VERSION 3.22)
project(twinsanityevolved_android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT CRASHTWIN_SDL_SRC)
    message(FATAL_ERROR "Set -DCRASHTWIN_SDL_SRC to an SDL 2.32 source tree (the CI fetches release-2.32.10)")
endif()
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
set(SDL_TEST OFF CACHE BOOL "" FORCE)
add_subdirectory(${CRASHTWIN_SDL_SRC} ${CMAKE_BINARY_DIR}/sdl2)

add_library(twinsanityevolved SHARED src/twinsanityevolved_jni.cpp src/iso9660.cpp)
target_link_libraries(twinsanityevolved PRIVATE SDL2 log)

if(CRASHTWIN_OUT)
    set_target_properties(twinsanityevolved PROPERTIES LIBRARY_OUTPUT_DIRECTORY "${CRASHTWIN_OUT}")
    add_custom_command(TARGET twinsanityevolved POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E make_directory "${CRASHTWIN_OUT}"
        COMMAND ${CMAKE_COMMAND} -E copy_if_different $<TARGET_FILE:SDL2> "${CRASHTWIN_OUT}")
endif()
