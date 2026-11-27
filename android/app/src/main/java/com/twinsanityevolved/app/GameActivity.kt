package com.twinsanityevolved.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
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
    private lateinit var editPanel: LinearLayout
    private lateinit var notice: TextView
    private lateinit var screenButton: Button
    private lateinit var sizeBar: SeekBar
    private lateinit var alphaBar: SeekBar
    private var engineStarted = false

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
            onSelectionChanged = { s -> syncEditBars(s) }
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
                menuPanel.visibility =
                    if (menuPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        menuPanel = buildMenu()
        editPanel = buildEditPanel()

        // Transparent: the engine's picture is underneath.
        addContentView(
            FrameLayout(this).apply {
                addView(touchPad, FrameLayout.LayoutParams(MATCH, MATCH))
                addView(notice, FrameLayout.LayoutParams(MATCH, MATCH))
                addView(hamburger, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START))
                addView(
                    menuPanel,
                    FrameLayout.LayoutParams(dp(280), WRAP, Gravity.TOP or Gravity.START).apply {
                        topMargin = dp(56)
                        leftMargin = dp(8)
                    }
                )
                addView(editPanel, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
            },
            FrameLayout.LayoutParams(MATCH, MATCH)
        )
        openDisc()
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
            visibility = View.GONE
            addView(menuItem(getString(R.string.menu_pause)) {
                hideMenu()
                touchPad.pulse(Pad.START)
            })
            addView(menuItem(getString(R.string.menu_edit)) {
                hideMenu()
                setEditing(true)
            })
            addView(screenButton)
            addView(menuItem(getString(R.string.menu_change_disc)) {
                hideMenu()
                startActivityForResult(RomStore.pickerIntent(), REQ_CHANGE_DISC)
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
        menuPanel.visibility = View.GONE
    }

    private fun cycleScreen() {
        settings.screen = settings.screen.next()
        // The picture's aspect is the engine's (its Widescreen and display options); the choice is kept for it.
        screenButton.text = getString(R.string.menu_screen, settings.screen.label)
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
        notice.text = NativeBridge.status()
        notice.visibility = View.VISIBLE
        // The game draws underneath: the status is for the start-up only.
        notice.postDelayed({ notice.visibility = View.GONE }, 4000)
    }

    private fun showNotice(res: Int) {
        notice.text = getString(res)
        notice.visibility = View.VISIBLE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CHANGE_DISC || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (RomStore.accept(this, uri)) {
            recreate() // onDestroy stops the engine; the new one starts with the new disc
        } else {
            Toast.makeText(this, R.string.rom_not_disc, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        if (engineStarted) NativeBridge.stop()
        engineStarted = false
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_CHANGE_DISC = 2
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
