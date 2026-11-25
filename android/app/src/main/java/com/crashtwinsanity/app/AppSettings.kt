package com.crashtwinsanity.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** How the picture fills the screen. The picture is never stretched: a fixed aspect is letterboxed, not scaled. */
enum class ScreenMode(val label: String, val aspect: Float) {
    NATIVE("Sin estirar (pantalla)", 0f),
    WIDE_16_9("16:9 centrado", 16f / 9f),
    ULTRAWIDE_21_9("21:9 centrado", 21f / 9f),
    CLASSIC_4_3("4:3 centrado", 4f / 3f);

    fun next(): ScreenMode = values()[(ordinal + 1) % values().size]
}

/** Everything the player changes from the touch menus, kept on the device. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var profile: Profile
        get() = Profile.values().firstOrNull { it.name == prefs.getString("profile", null) } ?: Profile.CORTEX
        set(value) {
            prefs.edit().putString("profile", value.name).apply()
        }

    var screen: ScreenMode
        get() = ScreenMode.values().firstOrNull { it.name == prefs.getString("screen", null) } ?: ScreenMode.NATIVE
        set(value) {
            prefs.edit().putString("screen", value.name).apply()
        }

    fun loadSlots(profile: Profile): MutableList<Slot> {
        val raw = prefs.getString("layout_${profile.name}", null) ?: return Layouts.preset(profile)
        return try {
            val array = JSONArray(raw)
            MutableList(array.length()) { i ->
                val o = array.getJSONObject(i)
                Slot(
                    control = Control.valueOf(o.getString("c")),
                    x = o.getDouble("x").toFloat(),
                    y = o.getDouble("y").toFloat(),
                    scale = o.getDouble("s").toFloat(),
                    alpha = o.getDouble("a").toFloat(),
                )
            }
        } catch (e: Exception) {
            Layouts.preset(profile)
        }
    }

    fun saveSlots(profile: Profile, slots: List<Slot>) {
        val array = JSONArray()
        for (s in slots) {
            array.put(
                JSONObject()
                    .put("c", s.control.name)
                    .put("x", s.x.toDouble())
                    .put("y", s.y.toDouble())
                    .put("s", s.scale.toDouble())
                    .put("a", s.alpha.toDouble())
            )
        }
        prefs.edit().putString("layout_${profile.name}", array.toString()).apply()
    }

    fun resetSlots(profile: Profile) {
        prefs.edit().remove("layout_${profile.name}").apply()
    }
}
