package com.twinsanityevolved.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * The engine's mod importer (engine/app/host/mod_import, through android/native/src/mod_import_jni.cpp). It takes a
 * Mod Crate (.zip), a patched disc image, an xdelta / PPF / BPS patch or one file of the game's archive, recognises it
 * by its content and writes one folder into the mods folder. The file the player picks is copied first: the importer
 * reads a path, not a content descriptor.
 */
object ModImporter {

    /** The ids the game already has: a mod with one of them would share its switch (mods.<id>.enabled). */
    val RESERVED_IDS = setOf("arabic", "infinite_lives", "level_select", "speedrun")

    init {
        // NativeBridge loads the library (SDL2 first); the functions below live in it.
        NativeBridge.available
    }

    @JvmStatic private external fun nativeModId(artefact: String): String
    @JvmStatic private external fun nativeDetect(artefact: String): String
    @JvmStatic private external fun nativeImport(artefact: String, baseIso: String, modsDir: String): String

    /** Where the game keeps its mods: the settings folder's mods (HOME is the app's files folder, see te_android.cpp). */
    fun modsDir(context: Context): File = File(context.filesDir, ".twinsanityevolved/mods")

    /** The disc the mods are made over: the copy the game reads (DiscImport). Null when there is no disc yet. */
    fun baseDisc(context: Context): File? = File(context.filesDir, "disc.iso").takeIf { it.isFile }

    class Picked(val file: File, val displayName: String)

    /**
     * Copies the picked file into the app's cache under its own name: the importer names a mod after it (a crate's
     * name, else the file's name), so the name is kept.
     */
    fun copyPicked(context: Context, uri: Uri): Picked? {
        val name = displayName(context, uri) ?: return null
        val dir = File(context.cacheDir, "imports").apply { mkdirs() }
        val file = File(dir, name.replace('/', '_'))
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { out -> input.copyTo(out, 1 shl 20) }
        } ?: return null
        return Picked(file, name)
    }

    /** "" when the file is not a mod; else the id it would get and its name. */
    fun identify(file: File): Pair<String, String>? {
        val out = nativeModId(file.absolutePath)
        if (out.isEmpty()) return null
        val parts = out.split("\n", limit = 2)
        return parts[0] to parts.getOrElse(1) { "" }
    }

    /** What the file is, as the importer's words for the player: a kind and why. */
    fun detect(file: File): Pair<String, String> {
        val parts = nativeDetect(file.absolutePath).split("\n", limit = 2)
        return parts[0] to parts.getOrElse(1) { "" }
    }

    /**
     * Imports the copied file over the game's disc. Runs the importer (it can take a while: a large crate, or a
     * patch applied to a temporary copy of the disc), so call it off the UI thread.
     */
    fun importFile(context: Context, file: File): Result {
        val disc = baseDisc(context) ?: return Result.Failed("Primero elige el disco del juego.")
        val mods = modsDir(context).apply { mkdirs() }
        val out = nativeImport(file.absolutePath, disc.absolutePath, mods.absolutePath)
        val lines = out.split("\n", limit = 6)
        if (lines.isEmpty() || lines[0] != "OK") {
            return Result.Failed(out.removePrefix("ERROR\n").ifEmpty { "No se pudo importar el archivo." })
        }
        val warnings = lines.getOrElse(5) { "" }.split('\u001f').filter { it.isNotBlank() }
        return Result.Added(
            id = lines.getOrElse(1) { "" },
            name = lines.getOrElse(2) { "" },
            replaced = lines.getOrElse(3) { "0" } == "1",
            filesChanged = lines.getOrElse(4) { "0" }.toIntOrNull() ?: 0,
            warnings = warnings,
        )
    }

    sealed class Result {
        class Added(val id: String, val name: String, val replaced: Boolean, val filesChanged: Int, val warnings: List<String>) : Result()
        class Failed(val message: String) : Result()
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
