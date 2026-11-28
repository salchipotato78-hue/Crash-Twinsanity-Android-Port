// The Mods panel's bridge to the mod importer (engine/app/host/mod_import, the same library the PC launcher uses).
// Strings cross the boundary as text: the importer's sentences for the player, and its facts one per line.
#include <jni.h>

#include <exception>
#include <string>
#include <vector>

#include "app/host/mod_import/mod_import.h"

namespace {

std::string Text(JNIEnv* env, jstring s) {
    if (!s) return std::string();
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

jstring Out(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

// The separator for a list inside one text: a control character no sentence of the importer contains.
const char kSep = '\x1f';

std::string Join(const std::vector<std::string>& items) {
    std::string out;
    for (size_t i = 0; i < items.size(); ++i) {
        if (i) out += kSep;
        out += items[i];
    }
    return out;
}

te::modimport::Progress Quiet() {
    te::modimport::Progress p;
    p.report = [](const std::string&, double) {};
    p.cancelled = [] { return false; };
    return p;
}

}  // namespace

extern "C" {

// "" when the artefact is not a mod; else "id\nname".
JNIEXPORT jstring JNICALL
Java_com_twinsanityevolved_app_ModImporter_nativeModId(JNIEnv* env, jclass, jstring artefact) {
    try {
        std::string name;
        const std::string id = te::modimport::ModIdFor(Text(env, artefact), &name);
        if (id.empty()) return Out(env, "");
        return Out(env, id + "\n" + name);
    } catch (const std::exception&) {
        return Out(env, "");
    }
}

// "kind label\nwhy" -- what the artefact is, by its content (what the panel says before it imports).
JNIEXPORT jstring JNICALL
Java_com_twinsanityevolved_app_ModImporter_nativeDetect(JNIEnv* env, jclass, jstring artefact) {
    try {
        std::string why;
        const te::modimport::Kind kind = te::modimport::Detect(Text(env, artefact), &why);
        return Out(env, std::string(te::modimport::KindLabel(kind)) + "\n" + why);
    } catch (const std::exception& e) {
        return Out(env, std::string("not a mod\n") + e.what());
    }
}

// "OK" + lines: id, name, replaced (0/1), files changed, then the warnings joined by kSep.
// "ERROR" + the sentence for the player (the mods folder is left as it was).
JNIEXPORT jstring JNICALL
Java_com_twinsanityevolved_app_ModImporter_nativeImport(JNIEnv* env, jclass, jstring artefact, jstring base_iso,
                                                        jstring mods_dir) {
    try {
        te::modimport::Report report;
        std::string error;
        const bool ok = te::modimport::Import(Text(env, artefact), Text(env, base_iso), Text(env, mods_dir), Quiet(),
                                              &report, &error);
        if (!ok) return Out(env, "ERROR\n" + (error.empty() ? std::string("The import failed.") : error));
        std::string warnings = Join(report.warnings);
        return Out(env, "OK\n" + report.id + "\n" + report.name + "\n" + (report.replaced ? "1" : "0") + "\n" +
                            std::to_string(report.files_changed.size() + report.files_added.size()) + "\n" + warnings);
    } catch (const std::exception& e) {
        return Out(env, std::string("ERROR\nThe import failed: ") + e.what());
    }
}

}  // extern "C"
