package com.rushi.tijori

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.rushi.tijori.ui.AppRoot
import com.rushi.tijori.ui.TijoriTheme
import com.rushi.tijori.ui.VaultViewModel

class MainActivity : FragmentActivity() {
    private val vm: VaultViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())
    private val autoLock = Runnable { vm.lock() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots and screen recording, and blanks the app in the recent-apps view.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            TijoriTheme {
                AppRoot(
                    vm = vm,
                    biometricAvailable = vm.biometric.isAvailable(),
                    onBiometricUnlock = ::biometricUnlock,
                    onSetBiometric = ::setBiometric,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        handler.removeCallbacks(autoLock)
    }

    override fun onStop() {
        super.onStop()
        // Lock when the app has been out of sight for a while. Pickers for backup
        // and restore also put the app in the background, hence the grace period.
        handler.postDelayed(autoLock, AUTO_LOCK_MS)
    }

    private fun biometricUnlock() {
        vm.biometric.unlock(
            activity = this,
            onKey = { key -> vm.unlockWithKey(key) },
            onError = { message -> vm.biometricFailed(message) },
        )
    }

    private fun setBiometric(enabled: Boolean) {
        if (!enabled) {
            vm.biometric.disable()
            vm.refreshBiometric()
            vm.notify("Biometric unlock is off.")
            return
        }
        val key = vm.keyCopy() ?: return
        vm.biometric.enable(this, key) { ok, error ->
            key.fill(0)
            vm.refreshBiometric()
            when {
                ok -> vm.notify("Biometric unlock is on.")
                error != null -> vm.notify(error)
            }
        }
    }

    private companion object {
        const val AUTO_LOCK_MS = 60_000L
    }
}
