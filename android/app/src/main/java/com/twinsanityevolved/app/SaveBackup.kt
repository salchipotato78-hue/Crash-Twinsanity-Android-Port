package com.twinsanityevolved.app

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The player's progress as one zip: the memory cards (the game's own saves, and the ones used while a mod is on) and the
 * save states. The settings (ps2r.ini) are not in it. The game keeps these folders under its data folder, which is
 * .twinsanityevolved in the app's private storage.
 */
object SaveBackup {

    private val folders = listOf("memcards", "memcards-mods", "savestates")

    private fun dataRoot(context: Context): File = File(context.filesDir, ".twinsanityevolved")

    /** Writes the saves to [out]. Returns how many files went in (0 when there are no saves yet). */
    fun export(context: Context, out: OutputStream): Int {
        val root = dataRoot(context)
        var count = 0
        ZipOutputStream(out).use { zip ->
            for (folder in folders) {
                val dir = File(root, folder)
                if (!dir.isDirectory) continue
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
            }
        }
        return count
    }

    /**
     * Replaces the saves with the backup in [input]. The backup is read into a staging folder first and checked: an entry
     * outside the three folders (or one that climbs out of them) refuses the whole backup, and the current saves are only
     * replaced once every entry has been read. Returns how many files were restored.
     */
    fun restore(context: Context, input: InputStream): Int {
        val root = dataRoot(context)
        val staging = File(root, "saves-import")
        staging.deleteRecursively()
        var count = 0
        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
                        require(!name.startsWith("/") && !name.contains("..")) { "unsafe path in the backup: $name" }
                        require(folders.any { name.startsWith("$it/") }) { "not a Crash Twinsanity save backup: $name" }
                        val target = File(staging, name)
                        require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                            "unsafe path in the backup: $name"
                        }
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                        count++
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            require(count > 0) { "the backup holds no saves" }
            for (folder in folders) File(root, folder).deleteRecursively()
            for (folder in folders) {
                val src = File(staging, folder)
                if (src.isDirectory) src.renameTo(File(root, folder))
            }
        } finally {
            staging.deleteRecursively()
        }
        return count
    }
}
