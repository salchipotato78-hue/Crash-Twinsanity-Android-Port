// runner/te_android.h -- the Android launch. The app copies the disc into its own folder (the content provider's
// descriptor cannot be reopened by path: /proc/self/fd fails with EACCES), then the JNI entry hands the path and the
// folder the game keeps its files in (HOME, as on the desktop: $HOME/Games/Crash Twinsanity) to Startup(), which
// turns them into the argv main() expects: --iso <path>.
#pragma once

namespace te::android {

void SetLaunch(const char* iso_path, const char* files_dir);
void Startup(int* argc, char*** argv);

}  // namespace te::android
