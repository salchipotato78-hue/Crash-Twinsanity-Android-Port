package com.twinsanityevolved.app

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * A mod of the mods folder, as its mod.json describes it.
 */
data class Mod(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val folder: File,
    val enabled: Boolean,
)

/**
 * The switches and the order of the mods, kept in ps2r.ini the way the game reads them: `mods.<id>.enabled = on|off`
 * and `mods.order = id,id,...` (platform/nativeee/host/mods.cpp). Other lines of the file are left as they are.
 */
object ModSettings {

    private fun iniFile(context: Context): File = File(context.filesDir, ".twinsanityevolved/ps2r.ini")

    /** The mods of the mods folder, in the game's order (mods.order first, the rest by id). */
    fun list(context: Context): List<Mod> {
        val dir = ModImporter.modsDir(context)
        val lines = readLines(context)
        val order = orderOf(lines)
        val mods = dir.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") && File(it, "mod.json").isFile }
            .mapNotNull { folder -> parse(folder, enabledOf(lines, folder.name)) }
        return mods.sortedWith(compareBy({ order.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.id }))
    }

    fun setEnabled(context: Context, id: String, on: Boolean) {
        val lines = readLines(context).toMutableList()
        setKey(lines, "mods.$id.enabled", if (on) "on" else "off")
        writeLines(context, lines)
    }

    /** Puts a mod last in the order (a newly added one), unless it has a place already. */
    fun placeLast(context: Context, id: String) {
        val lines = readLines(context).toMutableList()
        val order = orderOf(lines).toMutableList()
        if (id !in order) order.add(id)
        setKey(lines, "mods.order", order.joinToString(","))
        writeLines(context, lines)
    }

    /** Takes a mod out of the switches and the order, when it is removed. */
    fun forget(context: Context, id: String) {
        val lines = readLines(context).toMutableList()
        lines.removeAll { keyOf(it) == "mods.$id.enabled" }
        val order = orderOf(lines).filter { it != id }
        setKey(lines, "mods.order", order.joinToString(","))
        writeLines(context, lines)
    }

    private fun parse(folder: File, enabled: Boolean): Mod? = try {
        val json = JSONObject(File(folder, "mod.json").readText())
        Mod(
            id = json.optString("id", folder.name),
            name = json.optString("name", folder.name),
            version = json.optString("version", ""),
            author = json.optString("author", ""),
            description = json.optString("description", ""),
            folder = folder,
            enabled = enabled,
        )
    } catch (_: Exception) {
        null
    }

    private fun readLines(context: Context): List<String> {
        val f = iniFile(context)
        return if (f.isFile) f.readLines() else emptyList()
    }

    private fun writeLines(context: Context, lines: List<String>) {
        val f = iniFile(context)
        f.parentFile?.mkdirs()
        f.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    private fun keyOf(line: String): String? {
        val t = line.substringBefore(';').trim()
        val eq = t.indexOf('=')
        return if (eq < 0) null else t.substring(0, eq).trim()
    }

    private fun valueOf(line: String): String =
        line.substringBefore(';').substringAfter('=', "").trim()

    private fun setKey(lines: MutableList<String>, key: String, value: String) {
        val at = lines.indexOfFirst { keyOf(it) == key }
        val text = "$key = $value"
        if (at >= 0) lines[at] = text else lines.add(text)
    }

    private fun enabledOf(lines: List<String>, id: String): Boolean {
        val line = lines.lastOrNull { keyOf(it) == "mods.$id.enabled" } ?: return false
        return valueOf(line).equals("on", ignoreCase = true)
    }

    private fun orderOf(lines: List<String>): List<String> {
        val line = lines.lastOrNull { keyOf(it) == "mods.order" } ?: return emptyList()
        return valueOf(line).split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
