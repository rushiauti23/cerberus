package com.rushi.tijori.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Copies secrets with the "sensitive" flag (Android 13+ then hides the preview)
 * and clears the clipboard after [CLEAR_AFTER_MS]. If Android kills the app
 * before the timer fires, the clipboard is not cleared.
 */
object SecureClipboard {
    const val CLEAR_AFTER_MS = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private var pendingClear: Runnable? = null

    fun copy(context: Context, text: String) {
        val clipboard = context.applicationContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Tijori", text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        clipboard.setPrimaryClip(clip)

        pendingClear?.let { handler.removeCallbacks(it) }
        val clear = Runnable {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            } catch (e: Exception) {
                // Some phones refuse clipboard access from the background.
            }
        }
        pendingClear = clear
        handler.postDelayed(clear, CLEAR_AFTER_MS)
    }
}
