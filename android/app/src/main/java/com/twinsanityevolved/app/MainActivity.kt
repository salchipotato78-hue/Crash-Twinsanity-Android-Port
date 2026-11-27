package com.twinsanityevolved.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * The launcher. The first time, it asks for the disc; after that it goes straight to the game.
 * The disc can be changed later from the game's touch menu.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (RomStore.get(this) != null) {
            openGame()
        } else {
            Toast.makeText(this, R.string.pick_disc, Toast.LENGTH_LONG).show()
            startActivityForResult(RomStore.pickerIntent(), REQ_PICK_DISC)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK_DISC) return
        val uri = if (resultCode == RESULT_OK) data?.data else null
        if (uri == null) {
            finish() // no disc, no first run
        } else if (RomStore.accept(this, uri)) {
            openGame()
        } else {
            Toast.makeText(this, R.string.rom_not_disc, Toast.LENGTH_LONG).show()
            startActivityForResult(RomStore.pickerIntent(), REQ_PICK_DISC)
        }
    }

    private fun openGame() {
        val uri = RomStore.get(this) ?: return
        Toast.makeText(this, "Preparando el disco…", Toast.LENGTH_SHORT).show()
        Thread {
            val path = try {
                DiscImport.importDisc(this, uri)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (path == null) {
                    Toast.makeText(this, R.string.disc_open_failed, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                startActivity(Intent(this, GameActivity::class.java).putExtra(DiscImport.EXTRA_DISC_PATH, path))
                finish()
            }
        }.start()
    }

    companion object {
        private const val REQ_PICK_DISC = 1
    }
}
