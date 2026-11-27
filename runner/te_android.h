// runner/te_android.h -- the Android launch. The app's disc arrives as a descriptor (the JNI's nativeStart) and the
// folder the game keeps its files in (HOME, as on the desktop: the game folder is $HOME/Games/Crash Twinsanity).
// Startup() turns them into the argv main() expects: --iso /proc/self/fd/<n>. SDL calls main() with no arguments here.
#pragma once

namespace te::android {

void SetLaunch(int iso_fd, const char* files_dir);
void Startup(int* argc, char*** argv);

}  // namespace te::android
