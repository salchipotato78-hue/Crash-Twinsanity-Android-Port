# libcrashtwin_engine.so: the game's C++ for arm64 Android (milestone 1: compile and link, no window yet).
#
# Built by .github/workflows/android-engine.yml, which first generates build/native/data.cpp from the split (the same
# converter the desktop build uses) and then runs:
#   cmake -S android/engine -B build/engine-android \
#     -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
#     -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=RelWithDebInfo \
#     -DCRASHTWIN_ROOT=$GITHUB_WORKSPACE -DCRASHTWIN_PYTHON=python3 -DCRASHTWIN_HOST_CLANG=clang++-20
#
# Plan (see android/engine/README.md): the engine is the same as the desktop one. Platform code that only exists on
# Windows (or needs the update check, FFmpeg movies, glslang) is left out here; what's missing comes back as the
# desktop build's weak stand-ins (native/tools/make_stubs.py), so the link tells us exactly what is still not ported.
cmake_minimum_required(VERSION 3.24)
project(crashtwin_engine VERSION 1.4.0 LANGUAGES C CXX)

if(NOT CRASHTWIN_ROOT)
    message(FATAL_ERROR "Set -DCRASHTWIN_ROOT to the repository root")
endif()
set(ROOT ${CRASHTWIN_ROOT})
if(NOT CRASHTWIN_PYTHON)
    set(CRASHTWIN_PYTHON python3)
endif()
if(NOT CRASHTWIN_HOST_CLANG)
    set(CRASHTWIN_HOST_CLANG clang++-20)
endif()

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_EXTENSIONS ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
set(CMAKE_CXX_FLAGS_RELWITHDEBINFO "-O2 -gline-tables-only -DNDEBUG")

# SDL3 from source (the Android build needs SDL's own Java glue later; milestone 1 only links the library).
set(CRASHTWIN_SDL_TAG "release-3.2.0" CACHE STRING "SDL3 tag to fetch")
include(FetchContent)
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
set(SDL_SHARED ON CACHE BOOL "" FORCE)
set(SDL_TEST_LIBRARY OFF CACHE BOOL "" FORCE)
FetchContent_Declare(SDL3
    GIT_REPOSITORY https://github.com/libsdl-org/SDL.git
    GIT_TAG ${CRASHTWIN_SDL_TAG}
    GIT_SHALLOW TRUE)
FetchContent_MakeAvailable(SDL3)

# The game's sources, as the desktop build lists them (native/CMakeLists.txt)
file(GLOB GAME_SOURCES CONFIGURE_DEPENDS ${ROOT}/src/game/*.cpp)
set(CORE_SOURCES ${ROOT}/src/debug.cpp ${ROOT}/src/gcc2.cpp ${ROOT}/src/main.cpp)
file(STRINGS ${ROOT}/native/reused_ps2.txt REUSED_PS2 REGEX "^src/")
list(TRANSFORM REUSED_PS2 PREPEND ${ROOT}/)

file(GLOB_RECURSE PLATFORM_SOURCES CONFIGURE_DEPENDS ${ROOT}/src/platform/native/*.cpp)
# Left out on Android for now: Windows-only code, the update check (WinHTTP), FFmpeg's movies, the profiler's Windows side.
list(FILTER PLATFORM_SOURCES EXCLUDE REGEX "(_win32|/updatecheck|/steamdeck|/profiler_win32|/movie/|/abi_win32)")

set(DATA_CPP ${ROOT}/build/native/data.cpp)
if(NOT EXISTS ${DATA_CPP})
    message(FATAL_ERROR "${DATA_CPP} is missing: generate it from the split first (data_types.py, convert_data.py)")
endif()

# The stand-ins: every function nothing defines yet, made from the objects
set(STUBS_CPP ${CMAKE_CURRENT_BINARY_DIR}/stubs.cpp)
add_library(game OBJECT ${GAME_SOURCES} ${CORE_SOURCES} ${REUSED_PS2} ${PLATFORM_SOURCES} ${DATA_CPP})
target_include_directories(game PRIVATE ${ROOT}/native/include ${ROOT}/include ${ROOT}/src/platform/native)
# SDL's headers for the platform layer (audio.cpp includes SDL3/SDL.h): the objects need them, not only the library
target_link_libraries(game PRIVATE SDL3::SDL3)
target_compile_definitions(game PRIVATE TWIN_NATIVE TWIN_ANDROID=1)
target_compile_options(game PRIVATE
    -fno-exceptions -fno-rtti -fno-threadsafe-statics -fno-strict-aliasing -fno-delete-null-pointer-checks -fwrapv
    -ffp-contract=off -fno-strict-float-cast-overflow -Wno-invalid-offsetof -Wno-unused-variable -Wno-unused-function
    -Wno-unused-but-set-variable -Wno-unknown-pragmas -Wno-missing-braces -Wno-mismatched-tags)

# The entry point is main in entry.cpp; the JNI shim calls it as twin_engine_main
set_source_files_properties(${ROOT}/src/platform/native/entry.cpp PROPERTIES COMPILE_DEFINITIONS "main=twin_engine_main")

file(GENERATE OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/stub_objects.txt CONTENT "$<JOIN:$<TARGET_OBJECTS:game>,\n>\n")
add_custom_command(
    OUTPUT ${STUBS_CPP}
    COMMAND ${CMAKE_COMMAND} -E env "TWIN_CLANG=${CRASHTWIN_HOST_CLANG}"
            ${CRASHTWIN_PYTHON} ${ROOT}/native/tools/make_stubs.py ${STUBS_CPP} @${CMAKE_CURRENT_BINARY_DIR}/stub_objects.txt
    DEPENDS game $<TARGET_OBJECTS:game> ${ROOT}/native/tools/make_stubs.py
    COMMAND_EXPAND_LISTS
    VERBATIM
    COMMENT "Making the stand-ins for what the Android port doesn't do yet")

add_library(crashtwin_engine SHARED $<TARGET_OBJECTS:game> ${STUBS_CPP} ${CMAKE_CURRENT_SOURCE_DIR}/src/engine_jni.cpp)
target_include_directories(crashtwin_engine PRIVATE ${ROOT}/include ${ROOT}/src/platform/native)
target_compile_definitions(crashtwin_engine PRIVATE TWIN_NATIVE TWIN_ANDROID=1)
target_link_libraries(crashtwin_engine PRIVATE SDL3::SDL3 android log EGL GLESv3 dl)
if(CRASHTWIN_OUT)
    set_target_properties(crashtwin_engine PROPERTIES LIBRARY_OUTPUT_DIRECTORY "${CRASHTWIN_OUT}")
endif()
