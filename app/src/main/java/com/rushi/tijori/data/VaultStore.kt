package com.rushi.tijori.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The encrypted vault file in app-private storage. Writes are atomic: a crash
 * or a dead battery mid-save leaves the previous version intact.
 */
class VaultStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, FILE_NAME))

    @Synchronized
    fun exists(): Boolean = try {
        file.openRead().close()
        true
    } catch (e: FileNotFoundException) {
        false
    } catch (e: IOException) {
        true
    }

    @Synchronized
    @Throws(IOException::class)
    fun read(): ByteArray = file.readFully()

    @Synchronized
    @Throws(IOException::class)
    fun write(bytes: ByteArray) {
        val out = file.startWrite()
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            throw e
        }
    }

    private companion object {
        const val FILE_NAME = "vault.tjr"
    }
}
