package com.twinsanityevolved.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** How the picture fills the screen. The picture is never stretched: a fixed aspect is letterboxed, not scaled. */
enum class ScreenMode(val label: String, val aspect: Float, val engineAspect: String) {
    NATIVE("Native (no stretch)", 0f, "auto"),
    WIDE_16_9("16:9 centered", 16f / 9f, "16:9"),
    ULTRAWIDE_21_9("21:9 centered", 21f / 9f, "21:9"),
    CLASSIC_4_3("4:3 centered", 4f / 3f, "4:3");

    fun next(): ScreenMode = values()[(ordinal + 1) % values().size]
}

/** Everything the player changes from the touch menus, kept on the device. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    companion object {
        // v2: the reference sizes. Older saved layouts (other sizes) are not read.
        private const val LAYOUT_KEY = "layout_v2"
    }

    var screen: ScreenMode
        get() = ScreenMode.values().firstOrNull { it.name == prefs.getString("screen", null) } ?: ScreenMode.NATIVE
        set(value) {
            prefs.edit().putString("screen", value.name).apply()
            // The engine reads its aspect from ps2r.ini when it starts, so the choice is written there too.
            ModSettings.putSetting(appContext, "video.aspect", value.engineAspect)
        }

    fun loadSlots(): MutableList<Slot> {
        val raw = prefs.getString(LAYOUT_KEY, null) ?: return Layouts.cortex()
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
            Layouts.cortex()
        }
    }

    fun saveSlots(slots: List<Slot>) {
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
        prefs.edit().putString(LAYOUT_KEY, array.toString()).apply()
    }

    fun resetSlots() {
        prefs.edit().remove(LAYOUT_KEY).apply()
    }
}
