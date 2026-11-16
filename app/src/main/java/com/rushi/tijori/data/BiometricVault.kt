package com.rushi.tijori.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Biometric unlock.
 *
 * A key that lives in the Android Keystore (hardware-backed on most phones)
 * encrypts a copy of the vault key. The Keystore only lets that key be used
 * right after a successful fingerprint or face check, and destroys it if the
 * enrolled biometrics change. The master password is never stored.
 */
class BiometricVault(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True when the phone has a strong biometric enrolled. */
    fun isAvailable(): Boolean =
        BiometricManager.from(appContext)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun isEnabled(): Boolean = prefs.contains(KEY_BLOB) && prefs.contains(KEY_IV)

    fun disable() {
        prefs.edit().clear().apply()
        try {
            keyStore().deleteEntry(ALIAS)
        } catch (e: Exception) {
            // Nothing to delete, or the Keystore is unavailable. Either way the wrapped key is gone.
        }
    }

    /**
     * Asks for a biometric check and, when it passes, stores [vaultKey] wrapped
     * by a new Keystore key. [onDone] gets (true, null) on success, (false, null)
     * when the user cancelled, and (false, message) on a real error.
     */
    fun enable(activity: FragmentActivity, vaultKey: ByteArray, onDone: (Boolean, String?) -> Unit) {
        val cipher = try {
            disable()
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, createKey()) }
        } catch (e: Exception) {
            onDone(false, "Could not set up biometric unlock: ${e.message}")
            return
        }
        authenticate(
            activity = activity,
            cipher = cipher,
            title = "Turn on biometric unlock",
            onSuccess = { authed ->
                try {
                    val blob = authed.doFinal(vaultKey)
                    prefs.edit()
                        .putString(KEY_IV, Base64.encodeToString(authed.iv, Base64.NO_WRAP))
                        .putString(KEY_BLOB, Base64.encodeToString(blob, Base64.NO_WRAP))
                        .apply()
                    onDone(true, null)
                } catch (e: Exception) {
                    disable()
                    onDone(false, "Could not set up biometric unlock: ${e.message}")
                }
            },
            onError = { message ->
                disable()
                onDone(false, message)
            },
        )
    }

    /**
     * Asks for a biometric check and hands the vault key to [onKey]. [onError]
     * gets null when the user cancelled.
     */
    fun unlock(activity: FragmentActivity, onKey: (ByteArray) -> Unit, onError: (String?) -> Unit) {
        val iv = prefs.getString(KEY_IV, null)
        val blob = prefs.getString(KEY_BLOB, null)
        if (iv == null || blob == null) {
            onError("Biometric unlock is not set up.")
            return
        }
        val cipher = try {
            val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: throw KeyPermanentlyInvalidatedException()
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable()
            onError(
                "The fingerprints or face on this phone changed, so biometric unlock was turned off. " +
                    "Unlock with your master password and turn it on again in Settings."
            )
            return
        } catch (e: Exception) {
            onError("Biometric unlock failed: ${e.message}")
            return
        }
        authenticate(
            activity = activity,
            cipher = cipher,
            title = "Unlock Tijori",
            onSuccess = { authed ->
                try {
                    onKey(authed.doFinal(Base64.decode(blob, Base64.NO_WRAP)))
                } catch (e: Exception) {
                    onError("Biometric unlock failed. Use your master password.")
                }
            },
            onError = onError,
        )
    }

    private fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        onSuccess: (Cipher) -> Unit,
        onError: (String?) -> Unit,
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authed = result.cryptoObject?.cipher
                if (authed == null) onError("Biometric unlock failed.") else onSuccess(authed)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onError(if (cancelled) null else errString.toString())
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun createKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Every single use needs a fresh strong-biometric check.
            spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(spec.build())
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private companion object {
        const val PREFS = "biometric_unlock"
        const val KEY_IV = "iv"
        const val KEY_BLOB = "wrapped_vault_key"
        const val ALIAS = "tijori_biometric_key"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
