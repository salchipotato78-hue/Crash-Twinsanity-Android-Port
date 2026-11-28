package com.twinsanityevolved.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.libsdl.app.SDLActivity
import java.io.File
import kotlin.math.roundToInt

/**
 * The game. SDLActivity (SDL's own activity) owns the window and calls the engine's SDL_main on its thread; the touch
 * overlay, the menu and the edit bars are added on top of it, so the touch layout is the same as before.
 */
class GameActivity : SDLActivity() {

    private lateinit var settings: AppSettings
    private lateinit var touchPad: TouchOverlay
    private lateinit var menuPanel: LinearLayout
    /** The main menu scrolls: the list can be longer than the screen. The wrapper is what shows and hides. */
    private lateinit var menuScroll: ScrollView
    private lateinit var editPanel: LinearLayout
    private lateinit var controllerPanel: LinearLayout
    private lateinit var mapPanel: ScrollView
    /** The gamepad's button map (PadMapping): the control being set, and the rows that show each control's button. */
    private var padMap: MutableMap<String, String> = PadMapping.defaults()
    private var capturing: String? = null
    private val mapRows = HashMap<String, Button>()
    private lateinit var notice: TextView
    private lateinit var screenButton: Button
    private lateinit var sizeBar: SeekBar
    private lateinit var alphaBar: SeekBar
    private var engineStarted = false
    private val mods by lazy { ModsPanel(this) }

    /** SDL loads libSDL2 first, then this library, which exports SDL_main (native/src/twinsanityevolved_jni.cpp). */
    override fun getLibraries(): Array<String> = arrayOf("SDL2", "twinsanityevolved")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        settings = AppSettings(this)

        touchPad = TouchOverlay(this).apply {
            slots = settings.loadSlots()
            onPad = { bits, lx, ly -> NativeBridge.setPad(bits, lx, ly) }
            onEdited = { settings.saveSlots(slots) }
            onLook = { dx, dy -> NativeBridge.look(dx, dy) }
            onSelectionChanged = { s -> syncEditBars(s) }
        }
        // The engine draws the picture at the screen's own shape (auto and the wide choices): it needs the pixels.
        touchPad.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            NativeBridge.setScreen(right - left, bottom - top)
        }
        notice = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(32), dp(32), dp(32), dp(32))
            visibility = View.GONE
        }
        val hamburger = TextView(this).apply {
            text = "\u2261"
            textSize = 30f
            setTextColor(Color.WHITE)
            alpha = 0.6f
            setPadding(dp(14), dp(4), dp(14), dp(4))
            setOnClickListener {
                menuScroll.visibility =
                    if (menuScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        menuPanel = buildMenu()
        menuScroll = ScrollView(this).apply {
            visibility = View.GONE
            addView(menuPanel)
        }
        editPanel = buildEditPanel()
        controllerPanel = buildControllerPanel()
        mapPanel = buildMapPanel()

        // Transparent: the engine's picture is underneath.
        addContentView(
            FrameLayout(this).apply {
                addView(touchPad, FrameLayout.LayoutParams(MATCH, MATCH))
                addView(notice, FrameLayout.LayoutParams(MATCH, MATCH))
                addView(hamburger, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START))
                addView(
                    menuScroll,
                    FrameLayout.LayoutParams(dp(280), MATCH, Gravity.TOP or Gravity.START).apply {
                        topMargin = dp(56)
                        leftMargin = dp(8)
                        bottomMargin = dp(8)
                    }
                )
                addView(editPanel, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP).apply { topMargin = dp(56) })
                addView(controllerPanel, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP).apply { topMargin = dp(56) })
                addView(mapPanel, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.TOP).apply { topMargin = dp(56) })
            },
            FrameLayout.LayoutParams(MATCH, MATCH)
        )
        touchPad.postDelayed(menuWatch, 100)
        // The engine reads the gamepad map when it starts, and takes a new one on its next frame.
        padMap = settings.loadPadMap()
        NativeBridge.setPadMap(PadMapping.serialize(padMap))
        importPendingSaves()
        openDisc()
    }

    /** The game's menu state, read ten times a second: the touch controls step back while a menu is open. */
    private val menuWatch = object : Runnable {
        override fun run() {
            val open = NativeBridge.menuOpen()
            if (open != touchPad.menuMode) touchPad.menuMode = open
            touchPad.postDelayed(this, 100)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars() else touchPad.releaseAll()
    }

    // A notification, a call or the home button can swallow the fingers' up events: the pad must not keep them held.
    override fun onPause() {
        touchPad.releaseAll()
        super.onPause()
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    // ----- menus (touch) ------------------------------------------------------------------------------------------

    private fun buildMenu(): LinearLayout {
        screenButton = menuItem(getString(R.string.menu_screen, settings.screen.label)) { cycleScreen() }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE6101010.toInt())
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(menuItem(getString(R.string.menu_pause)) {
                hideMenu()
                touchPad.pulse(Pad.START)
            })
            addView(menuItem(getString(R.string.menu_edit)) {
                hideMenu()
                setEditing(true)
            })
            addView(menuItem(getString(R.string.menu_controller)) {
                showControllerPanel(true)
            })
            addView(screenButton)
            addView(menuItem(getString(R.string.menu_mods)) {
                hideMenu()
                mods.show()
            })
            addView(menuItem(getString(R.string.menu_change_disc)) {
                hideMenu()
                startActivityForResult(RomStore.pickerIntent(), REQ_CHANGE_DISC)
            })
            addView(menuItem(getString(R.string.menu_export_saves)) {
                hideMenu()
                startActivityForResult(
                    Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/zip")
                        .putExtra(Intent.EXTRA_TITLE, "crash-twinsanity-saves.zip"),
                    REQ_EXPORT_SAVES
                )
            })
            addView(menuItem(getString(R.string.menu_import_saves)) {
                hideMenu()
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*")
                        .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/octet-stream")),
                    REQ_IMPORT_SAVES
                )
            })
            addView(menuItem(getString(R.string.menu_quit)) { finish() })
        }
    }

    private fun menuItem(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun hideMenu() {
        menuScroll.visibility = View.GONE
    }

    private fun cycleScreen() {
        settings.screen = settings.screen.next()
        screenButton.text = getString(R.string.menu_screen, settings.screen.label)
        Toast.makeText(this, R.string.screen_applies_next, Toast.LENGTH_SHORT).show()
    }

    // ----- editing the touch controls ----------------------------------------------------------------------------

    private fun buildEditPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xE6101010.toInt())
        setPadding(dp(16), dp(10), dp(16), dp(10))
        visibility = View.GONE
        addView(TextView(this@GameActivity).apply {
            text = getString(R.string.edit_title)
            setTextColor(Color.WHITE)
        })
        addView(label(getString(R.string.edit_size)))
        sizeBar = seekBar { p ->
            touchPad.selected?.let {
                it.scale = 0.5f + p / 100f * 1.5f
                touchPad.refresh()
            }
        }
        addView(sizeBar)
        addView(label(getString(R.string.edit_opacity)))
        alphaBar = seekBar { p ->
            touchPad.selected?.let {
                it.alpha = 0.1f + p / 100f * 0.9f
                touchPad.refresh()
            }
        }
        addView(alphaBar)
        addView(LinearLayout(this@GameActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(menuItem(getString(R.string.edit_done)) { setEditing(false) })
            addView(menuItem(getString(R.string.edit_reset)) { resetLayout() })
        })
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
    }

    private fun seekBar(onChange: (Int) -> Unit): SeekBar = SeekBar(this).apply {
        max = 100
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress)
            }

            override fun onStartTrackingTouch(bar: SeekBar) {}

            override fun onStopTrackingTouch(bar: SeekBar) {
                touchPad.commitEdit()
            }
        })
    }

    private fun syncEditBars(s: Slot?) {
        if (s == null) return
        sizeBar.progress = ((s.scale - 0.5f) / 1.5f * 100).roundToInt().coerceIn(0, 100)
        alphaBar.progress = ((s.alpha - 0.1f) / 0.9f * 100).roundToInt().coerceIn(0, 100)
    }

    private fun setEditing(on: Boolean) {
        hideMenu()
        touchPad.editing = on
        editPanel.visibility = if (on) View.VISIBLE else View.GONE
        if (!on) touchPad.commitEdit()
    }

    private fun resetLayout() {
        settings.resetSlots()
        touchPad.selected = null
        touchPad.slots = settings.loadSlots()
        touchPad.refresh()
    }

    // ----- the controller: hiding the touch pad, and the button map -----------------------------------------------

    private fun buildControllerPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xE6101010.toInt())
        setPadding(dp(16), dp(10), dp(16), dp(10))
        visibility = View.GONE
        addView(TextView(this@GameActivity).apply {
            text = getString(R.string.controller_title)
            setTextColor(Color.WHITE)
        })
        addView(Switch(this@GameActivity).apply {
            text = getString(R.string.controller_hide_touch)
            setTextColor(Color.WHITE)
            isChecked = settings.hideTouchWithPad
            setOnCheckedChangeListener { _, on -> settings.hideTouchWithPad = on }
        })
        addView(menuItem(getString(R.string.controller_edit_map)) { showMapPanel(true) })
        addView(menuItem(getString(R.string.controller_reset_map)) { resetPadMap() })
        addView(menuItem(getString(R.string.edit_done)) { showControllerPanel(false) })
    }

    private fun buildMapPanel(): ScrollView = ScrollView(this).apply {
        setBackgroundColor(0xE6101010.toInt())
        visibility = View.GONE
        addView(LinearLayout(this@GameActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            addView(TextView(this@GameActivity).apply {
                text = getString(R.string.map_title)
                setTextColor(Color.WHITE)
            })
            for (c in PadMapping.controls) {
                val row = menuItem("") { toggleCapture(c.key) }
                mapRows[c.key] = row
                addView(row)
            }
            addView(menuItem(getString(R.string.edit_done)) { showMapPanel(false) })
        })
    }

    private fun showControllerPanel(on: Boolean) {
        hideMenu()
        controllerPanel.visibility = if (on) View.VISIBLE else View.GONE
        if (on) mapPanel.visibility = View.GONE
    }

    private fun showMapPanel(on: Boolean) {
        hideMenu()
        capturing = null
        mapPanel.visibility = if (on) View.VISIBLE else View.GONE
        if (on) {
            controllerPanel.visibility = View.GONE
            refreshMapRows()
        } else {
            showControllerPanel(true)   // back to the controller's menu
        }
    }

    /** Tapping a control arms it: the next controller button pressed becomes its button. Tapping it again cancels. */
    private fun toggleCapture(key: String) {
        capturing = if (capturing == key) null else key
        refreshMapRows()
    }

    private fun refreshMapRows() {
        for (c in PadMapping.controls) {
            val row = mapRows[c.key] ?: continue
            row.text = if (capturing == c.key) {
                "${c.label}  \u2192  ${getString(R.string.map_press)}"
            } else {
                "${c.label}  \u2192  ${PadMapping.controllerLabel(padMap[c.key] ?: c.default)}"
            }
        }
    }

    private fun finishCapture(name: String) {
        val key = capturing ?: return
        PadMapping.assign(padMap, key, name)
        capturing = null
        settings.savePadMap(padMap)
        NativeBridge.setPadMap(PadMapping.serialize(padMap))
        refreshMapRows()
    }

    private fun resetPadMap() {
        padMap = PadMapping.defaults()
        settings.resetPadMap()
        NativeBridge.setPadMap(PadMapping.serialize(padMap))
        refreshMapRows()
    }

    /**
     * A controller's button or stick, seen by the activity (SDL's own events come through here too): while a control is
     * being set, the press is the new button; otherwise it hides the touch pad when the setting asks for it.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (PadMapping.isGamepad(event.source)) {
            if (capturing != null) {
                if (event.action == KeyEvent.ACTION_DOWN) PadMapping.keyName(event.keyCode)?.let { finishCapture(it) }
                return true   // the controller's buttons are not the game's while one is being set
            }
            if (event.action == KeyEvent.ACTION_DOWN && settings.hideTouchWithPad) touchPad.onControllerUsed()
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (PadMapping.isJoystick(event.source) && event.actionMasked == MotionEvent.ACTION_MOVE) {
            if (capturing != null) {
                PadMapping.motionName(event)?.let { finishCapture(it) }
            } else if (settings.hideTouchWithPad && PadMapping.moved(event)) {
                touchPad.onControllerUsed()
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    // ----- the engine ---------------------------------------------------------------------------------------------

    /** Checks the disc and hands its descriptor to the native side; SDL_main then runs the engine. */
    private fun openDisc() {
        val path = intent.getStringExtra(DiscImport.EXTRA_DISC_PATH)
        if (path == null) {
            showNotice(R.string.disc_open_failed)
            return
        }
        // Before SDL's first frame: SDL starts the engine's main() as soon as the surface is ready, and main() reads the disc.
        engineStarted = NativeBridge.start(path, filesDir.absolutePath)
        // Nothing is shown when the game starts. Only a disc that cannot be opened gets a message.
        if (!engineStarted) {
            showNotice(R.string.disc_open_failed)
            notice.postDelayed({ notice.visibility = View.GONE }, 4000)
        }
    }

    private fun showNotice(res: Int) {
        notice.text = getString(res)
        notice.visibility = View.VISIBLE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ModsPanel.REQ_PICK) {
            if (resultCode == RESULT_OK) data?.data?.let { mods.onPicked(it) }
            return
        }
        if (requestCode == REQ_EXPORT_SAVES) {
            if (resultCode == RESULT_OK) data?.data?.let { exportSaves(it) }
            return
        }
        if (requestCode == REQ_IMPORT_SAVES) {
            if (resultCode == RESULT_OK) data?.data?.let { confirmImport(it) }
            return
        }
        if (requestCode != REQ_CHANGE_DISC || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (RomStore.accept(this, uri)) {
            recreate() // onDestroy stops the engine; the new one starts with the new disc
        } else {
            Toast.makeText(this, R.string.rom_not_disc, Toast.LENGTH_LONG).show()
        }
    }

    /** Writes the player's saves to the file the user chose. */
    private fun exportSaves(uri: Uri) {
        Thread {
            val message = try {
                val count = contentResolver.openOutputStream(uri)?.use { SaveBackup.export(this, it) } ?: 0
                if (count == 0) getString(R.string.saves_none) else getString(R.string.saves_exported, count)
            } catch (e: Exception) {
                getString(R.string.saves_failed, e.message ?: "")
            }
            runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
        }.start()
    }

    /** Asks before a backup replaces the saves. The import itself runs when the game restarts (onCreate). */
    private fun confirmImport(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.saves_import_title)
            .setMessage(R.string.saves_import_warning)
            .setPositiveButton(R.string.saves_import_ok) { _, _ ->
                pendingImport = uri
                recreate() // onDestroy stops the engine; the import runs before the new one reads the saves
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Restores the backup chosen before the restart, while the engine is stopped and before it reads the cards. */
    private fun importPendingSaves() {
        val uri = pendingImport ?: return
        pendingImport = null
        val message = try {
            val count = contentResolver.openInputStream(uri)?.use { SaveBackup.restore(this, it) } ?: 0
            getString(R.string.saves_imported, count)
        } catch (e: Exception) {
            getString(R.string.saves_failed, e.message ?: "")
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        touchPad.removeCallbacks(menuWatch)
        if (engineStarted) NativeBridge.stop()
        engineStarted = false
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_CHANGE_DISC = 2
        private const val REQ_EXPORT_SAVES = 5
        private const val REQ_IMPORT_SAVES = 6
        /** A backup picked in the menu, applied by the activity that the restart creates. */
        private var pendingImport: Uri? = null
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
