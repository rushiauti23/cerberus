package com.rushi.tijori.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rushi.tijori.core.Category
import com.rushi.tijori.core.Entry
import com.rushi.tijori.core.Tag
import com.rushi.tijori.core.Vault
import com.rushi.tijori.core.VaultFile
import com.rushi.tijori.core.VaultFormatException
import com.rushi.tijori.core.VaultOps
import com.rushi.tijori.core.WrongKeyException
import com.rushi.tijori.core.WrongPasswordException
import com.rushi.tijori.data.BiometricVault
import com.rushi.tijori.data.DueIndex
import com.rushi.tijori.data.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/** A one-off message for the snackbar. The id makes two equal texts distinct. */
data class Notice(val id: Long, val text: String)

data class UiState(
    val hasVault: Boolean,
    /** Non-null exactly while the vault is unlocked. */
    val vault: Vault? = null,
    val busy: Boolean = false,
    val unlockError: String? = null,
    val notice: Notice? = null,
    val biometricEnabled: Boolean = false,
)

sealed interface Screen {
    data object Home : Screen
    data class EditEntry(val entryId: String?) : Screen
    data object Organize : Screen
    data object Settings : Screen
}

class VaultViewModel(app: Application) : AndroidViewModel(app) {
    private val store = VaultStore(app)
    val biometric = BiometricVault(app)

    /** The vault key and the current file bytes. Both exist only while unlocked. */
    private var key: ByteArray? = null
    private var file: ByteArray? = null

    /** Saves are written in order by one background job; only the newest pending one matters. */
    private val pendingWrites = Channel<ByteArray>(Channel.CONFLATED)
    private val noticeIds = AtomicLong()

    private val _state = MutableStateFlow(
        UiState(hasVault = store.exists(), biometricEnabled = biometric.isEnabled())
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    var screen: Screen by mutableStateOf<Screen>(Screen.Home)
        private set

    // List filters live here so they survive opening an entry and coming back.
    var query: String by mutableStateOf("")
    var categoryFilter: String? by mutableStateOf<String?>(null)
    var onlyDue: Boolean by mutableStateOf(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (bytes in pendingWrites) {
                try {
                    store.write(bytes)
                } catch (e: IOException) {
                    notify("Could not save the vault: ${e.message}")
                }
            }
        }
    }

    // --- navigation --------------------------------------------------------

    fun navigate(to: Screen) {
        screen = to
    }

    fun notify(text: String) {
        _state.update { it.copy(notice = Notice(noticeIds.incrementAndGet(), text)) }
    }

    // --- create, unlock, lock ---------------------------------------------

    fun createVault(password: String) = runBusy {
        try {
            val vault = VaultOps.defaultVault()
            val sealed = withContext(Dispatchers.Default) {
                val created = VaultFile.create(password.toCharArray(), vault)
                store.write(created.file)
                created
            }
            setUnlocked(sealed.file, sealed.key, vault)
            _state.update { it.copy(hasVault = true) }
        } catch (e: IOException) {
            notify("Could not create the vault: ${e.message}")
        } catch (e: OutOfMemoryError) {
            notify("Not enough memory to create the vault. Close other apps and try again.")
        }
    }

    fun unlock(password: String) = runBusy {
        try {
            val (bytes, opened) = withContext(Dispatchers.Default) {
                val read = store.read()
                read to VaultFile.unlock(read, password.toCharArray())
            }
            setUnlocked(bytes, opened.key, opened.vault)
        } catch (e: WrongPasswordException) {
            _state.update { it.copy(unlockError = "Wrong master password.") }
        } catch (e: VaultFormatException) {
            _state.update { it.copy(unlockError = e.message) }
        } catch (e: IOException) {
            _state.update { it.copy(unlockError = "Could not read the vault file.") }
        } catch (e: OutOfMemoryError) {
            _state.update { it.copy(unlockError = "Not enough memory to unlock. Close other apps and try again.") }
        }
    }

    /** Called with the vault key after a successful biometric check. */
    fun unlockWithKey(vaultKey: ByteArray) = runBusy {
        try {
            val (bytes, vault) = withContext(Dispatchers.Default) {
                val read = store.read()
                read to VaultFile.unlockWithKey(read, vaultKey)
            }
            setUnlocked(bytes, vaultKey, vault)
        } catch (e: WrongKeyException) {
            biometric.disable()
            _state.update {
                it.copy(
                    biometricEnabled = false,
                    unlockError = "Biometric unlock no longer matches this vault. Use your master password.",
                )
            }
        } catch (e: VaultFormatException) {
            _state.update { it.copy(unlockError = e.message) }
        } catch (e: IOException) {
            _state.update { it.copy(unlockError = "Could not read the vault file.") }
        }
    }

    fun biometricFailed(message: String?) {
        _state.update { it.copy(biometricEnabled = biometric.isEnabled(), unlockError = message ?: it.unlockError) }
    }

    fun refreshBiometric() {
        _state.update { it.copy(biometricEnabled = biometric.isEnabled()) }
    }

    /** A copy of the vault key for turning biometric unlock on. The caller must wipe it. */
    fun keyCopy(): ByteArray? = key?.copyOf()

    fun lock() {
        key?.fill(0)
        key = null
        file = null
        screen = Screen.Home
        query = ""
        categoryFilter = null
        onlyDue = false
        _state.update { it.copy(vault = null, unlockError = null) }
    }

    // --- editing -----------------------------------------------------------

    fun saveEntry(draft: Entry) {
        if (mutate { VaultOps.saveEntry(it, draft, System.currentTimeMillis()) }) {
            screen = Screen.Home
            notify("Saved.")
        }
    }

    fun deleteEntry(entryId: String) {
        if (mutate { VaultOps.deleteEntry(it, entryId) }) {
            screen = Screen.Home
            notify("Deleted.")
        }
    }

    fun saveCategory(category: Category) {
        mutate { VaultOps.saveCategory(it, category) }
    }

    fun deleteCategory(categoryId: String) {
        if (categoryFilter == categoryId) categoryFilter = null
        mutate { VaultOps.deleteCategory(it, categoryId) }
    }

    fun saveTag(tag: Tag) {
        mutate { VaultOps.saveTag(it, tag) }
    }

    fun deleteTag(tagId: String) {
        mutate { VaultOps.deleteTag(it, tagId) }
    }

    /**
     * Applies [transform], re-encrypts and queues the write. Encryption is done
     * here, in order, so two quick edits can never be saved out of order.
     */
    private fun mutate(transform: (Vault) -> Vault): Boolean {
        val current = _state.value.vault
        val vaultKey = key
        val currentFile = file
        if (current == null || vaultKey == null || currentFile == null) {
            notify("The vault is locked. Unlock it and try again.")
            return false
        }
        val next = transform(current)
        val bytes = VaultFile.save(currentFile, vaultKey, next)
        file = bytes
        _state.update { it.copy(vault = next) }
        pendingWrites.trySend(bytes)
        DueIndex.save(getApplication(), VaultOps.dueTimes(next))
        return true
    }

    // --- master password, backup, restore ----------------------------------

    fun changePassword(current: String, new: String) = runBusy {
        val vaultKey = key
        val currentFile = file
        if (vaultKey == null || currentFile == null) {
            notify("The vault is locked. Unlock it and try again.")
            return@runBusy
        }
        try {
            val newFile = withContext(Dispatchers.Default) {
                VaultFile.unlock(currentFile, current.toCharArray()).key.fill(0)
                val rekeyed = VaultFile.changePassword(currentFile, vaultKey, new.toCharArray())
                store.write(rekeyed)
                rekeyed
            }
            file = newFile
            notify("Master password changed. Backups made earlier still open with the old password.")
        } catch (e: WrongPasswordException) {
            notify("The current password is wrong. Nothing was changed.")
        } catch (e: IOException) {
            notify("Could not save the vault: ${e.message}")
        } catch (e: OutOfMemoryError) {
            notify("Not enough memory. Close other apps and try again.")
        }
    }

    /** Writes the encrypted vault file, as it is, to a location the user picked. */
    fun exportTo(uri: Uri) {
        val bytes = file
        if (bytes == null) {
            notify("The vault is locked. Unlock it and try again.")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: throw IOException("the chosen location could not be opened")
                    out.use { it.write(bytes) }
                }
                notify("Backup saved. It is encrypted and opens with your master password.")
            } catch (e: Exception) {
                notify("Backup failed: ${e.message}")
            }
        }
    }

    /** Replaces the vault on this phone with a backup file, after proving [password] opens it. */
    fun restoreFrom(uri: Uri, password: String) = runBusy {
        try {
            val (bytes, opened) = withContext(Dispatchers.Default) {
                val read = readLimited(uri)
                val unlocked = VaultFile.unlock(read, password.toCharArray())
                store.write(read)
                read to unlocked
            }
            // The backup has its own vault key, so the stored biometric copy no longer fits.
            biometric.disable()
            setUnlocked(bytes, opened.key, opened.vault)
            _state.update { it.copy(hasVault = true, biometricEnabled = false) }
            notify("Backup restored.")
        } catch (e: WrongPasswordException) {
            notify("That is not the master password of this backup. Nothing was changed.")
        } catch (e: VaultFormatException) {
            notify("${e.message} Nothing was changed.")
        } catch (e: IOException) {
            notify("Could not read the backup: ${e.message}")
        } catch (e: OutOfMemoryError) {
            notify("Not enough memory to open the backup. Close other apps and try again.")
        }
    }

    // --- helpers -----------------------------------------------------------

    private fun setUnlocked(bytes: ByteArray, vaultKey: ByteArray, vault: Vault) {
        key = vaultKey
        file = bytes
        screen = Screen.Home
        _state.update { it.copy(vault = vault, unlockError = null) }
        DueIndex.save(getApplication(), VaultOps.dueTimes(vault))
    }

    /** Runs slow work (Argon2id takes a second or two) while the UI shows a spinner. */
    private fun runBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, unlockError = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                notify("Something went wrong: ${e.message}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    @Throws(IOException::class)
    private fun readLimited(uri: Uri): ByteArray {
        val input = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: throw IOException("the file could not be opened")
        val out = ByteArrayOutputStream()
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            var n = stream.read(buffer)
            while (n >= 0) {
                out.write(buffer, 0, n)
                if (out.size() > MAX_BACKUP_BYTES) throw IOException("the file is too large to be a vault backup")
                n = stream.read(buffer)
            }
        }
        return out.toByteArray()
    }

    override fun onCleared() {
        key?.fill(0)
        pendingWrites.close()
        super.onCleared()
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
    }
}
