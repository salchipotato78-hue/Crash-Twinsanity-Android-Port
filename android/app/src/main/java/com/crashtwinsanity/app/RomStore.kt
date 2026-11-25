package com.crashtwinsanity.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/** The disc the player picked. Kept as a content URI with a persistable permission (Storage Access Framework). */
object RomStore {
    private const val PREFS = "rom"
    private const val KEY_URI = "uri"
    private val DISC_EXTENSIONS = listOf(".iso", ".bin", ".img")

    fun get(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URI, null)
            ?.let(Uri::parse)

    fun pickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** Keeps the picked file if it is a disc image. False when it isn't. */
    fun accept(context: Context, uri: Uri): Boolean {
        val name = displayName(context, uri)?.lowercase() ?: return false
        if (DISC_EXTENSIONS.none { name.endsWith(it) }) return false
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // The provider doesn't keep permissions; the picked file still works until the app closes.
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_URI, uri.toString()).apply()
        return true
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
