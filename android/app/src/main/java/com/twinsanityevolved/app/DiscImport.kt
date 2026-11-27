package com.twinsanityevolved.app

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * The disc copied into the app's own folder. A content descriptor cannot be reopened by path (the engine's main()
 * reads the disc by path), so the engine reads a copy. The copy is kept: it is made again only when its size differs
 * from the picked disc. This runs before GameActivity opens, because SDL starts the engine's main() as soon as the
 * game's surface is ready, and that main() needs the disc already in place.
 */
object DiscImport {
    const val EXTRA_DISC_PATH = "disc_path"

    fun importDisc(context: Context, uri: Uri): String? {
        val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        if (size <= 0L) return null
        val target = File(context.filesDir, "disc.iso")
        if (target.exists() && target.length() == size) return target.absolutePath
        val part = File(context.filesDir, "disc.iso.part")
        context.contentResolver.openInputStream(uri)?.use { input ->
            part.outputStream().use { out -> input.copyTo(out, 1 shl 20) }
        } ?: return null
        if (part.length() != size) {
            part.delete()
            return null
        }
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) return null
        return target.absolutePath
    }
}
