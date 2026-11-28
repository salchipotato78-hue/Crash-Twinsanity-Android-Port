package com.twinsanityevolved.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * The Mods section of the game's touch menu: the mods of the mods folder with their switches, a button that imports a
 * file (a Mod Crate .zip, a patched disc image, an xdelta / PPF / BPS patch, one file of the game's archive) through the
 * engine's importer, and Remove. The game reads the switches when it starts.
 */
class ModsPanel(private val activity: GameActivity) {

    private var dialog: AlertDialog? = null

    fun show() {
        dialog?.dismiss()
        val ctx = activity
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        root.addView(TextView(ctx).apply {
            text = "Los cambios se aplican la próxima vez que inicies el juego."
            setTextColor(Color.LTGRAY)
            textSize = 13f
        })
        val mods = ModSettings.list(ctx)
        if (mods.isEmpty()) {
            root.addView(TextView(ctx).apply {
                text = "No hay mods importados."
                setTextColor(Color.WHITE)
                setPadding(0, dp(12), 0, dp(12))
            })
        }
        for (m in mods) root.addView(row(m))
        root.addView(Button(ctx).apply {
            text = "Importar mod…"
            setOnClickListener { pick() }
        })
        dialog = AlertDialog.Builder(ctx)
            .setTitle("Mods")
            .setView(ScrollView(ctx).apply { addView(root) })
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun row(m: Mod): View {
        val ctx = activity
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val text = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        text.addView(TextView(ctx).apply {
            this.text = if (m.version.isEmpty()) m.name else "${m.name}  ${m.version}"
            setTextColor(Color.WHITE)
            textSize = 16f
        })
        val by = listOf(m.author, m.id).filter { it.isNotEmpty() }.joinToString("  ·  ")
        text.addView(TextView(ctx).apply {
            this.text = by
            setTextColor(Color.GRAY)
            textSize = 12f
        })
        if (m.description.isNotEmpty()) {
            text.addView(TextView(ctx).apply {
                this.text = m.description
                setTextColor(Color.LTGRAY)
                textSize = 12f
                maxLines = 3
            })
        }
        box.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(Switch(ctx).apply {
            isChecked = m.enabled
            setOnCheckedChangeListener { _, on -> ModSettings.setEnabled(ctx, m.id, on) }
        })
        box.addView(Button(ctx).apply {
            text = "Quitar"
            setOnClickListener { confirmRemove(m) }
        })
        return box
    }

    // ----- importing ---------------------------------------------------------------------------------------------

    private fun pick() {
        dialog?.dismiss()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        activity.startActivityForResult(intent, REQ_PICK)
    }

    /** The file the player picked: copied, identified, checked, then imported off the UI thread. */
    fun onPicked(uri: Uri) {
        val ctx = activity
        Toast.makeText(ctx, "Importando…", Toast.LENGTH_SHORT).show()
        Thread {
            val picked = ModImporter.copyPicked(ctx, uri)
            if (picked == null) {
                ui { info("No se pudo leer el archivo.", "Elígelo otra vez.") { show() } }
                return@Thread
            }
            val ident = ModImporter.identify(picked.file)
            if (ident == null) {
                val (kind, why) = ModImporter.detect(picked.file)
                picked.file.delete()
                ui { info("Ese archivo no es un mod.", "$kind\n$why") { show() } }
                return@Thread
            }
            val (id, name) = ident
            if (id in ModImporter.RESERVED_IDS) {
                picked.file.delete()
                ui {
                    info(
                        "Nombre reservado",
                        "«$name» tiene el nombre de un mod que ya trae el juego ($id). Cámbiale el nombre al mod y vuelve a importarlo."
                    ) { show() }
                }
                return@Thread
            }
            val exists = File(ModImporter.modsDir(ctx), "$id/mod.json").isFile
            if (exists) {
                ui {
                    AlertDialog.Builder(ctx)
                        .setTitle("¿Reemplazar «$name»?")
                        .setMessage("Ya tienes un mod con ese nombre. La copia que tienes se quita cuando la nueva esté completa. Su interruptor y su lugar se conservan.")
                        .setPositiveButton("Reemplazar") { _, _ -> importNow(picked.file) }
                        .setNegativeButton("Cancelar") { _, _ -> picked.file.delete(); show() }
                        .setCancelable(false)
                        .show()
                }
            } else {
                importNow(picked.file)
            }
        }.start()
    }

    private fun importNow(file: File) {
        val ctx = activity
        Thread {
            val result = ModImporter.importFile(ctx, file)
            file.delete()
            ui {
                when (result) {
                    is ModImporter.Result.Added -> {
                        if (!result.replaced) {
                            ModSettings.placeLast(ctx, result.id)
                            ModSettings.setEnabled(ctx, result.id, true)
                        }
                        val lines = mutableListOf(
                            "«${result.name}» ${if (result.replaced) "reemplazado" else "añadido y activado"}.",
                            "${result.filesChanged} archivo(s) cambiados.",
                            "Se aplica la próxima vez que inicies el juego.",
                        )
                        if (result.warnings.isNotEmpty()) {
                            lines.add("")
                            lines.add("Avisos:")
                            result.warnings.forEach { lines.add("• $it") }
                        }
                        info("Mod importado", lines.joinToString("\n")) { show() }
                    }
                    is ModImporter.Result.Failed -> info("No se pudo importar", result.message) { show() }
                }
            }
        }.start()
    }

    // ----- removing ------------------------------------------------------------------------------------------------

    private fun confirmRemove(m: Mod) {
        val ctx = activity
        AlertDialog.Builder(ctx)
            .setTitle("¿Quitar «${m.name}»?")
            .setMessage("Se borra del teléfono. Si quieres volver a usarlo, tendrás que importarlo otra vez.")
            .setPositiveButton("Quitar") { _, _ ->
                Thread {
                    m.folder.deleteRecursively()
                    ModSettings.forget(ctx, m.id)
                    ui { show() }
                }.start()
            }
            .setNegativeButton("Cancelar") { _, _ -> show() }
            .show()
    }

    // ----- helpers -------------------------------------------------------------------------------------------------

    private fun info(title: String, message: String, then: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Aceptar") { _, _ -> then() }
            .show()
    }

    private fun ui(block: () -> Unit) = activity.runOnUiThread(block)

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    companion object {
        /** The request code of the file picker's result (GameActivity routes it here). */
        const val REQ_PICK = 3
    }
}
