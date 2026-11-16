package com.rushi.tijori.core

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The master password did not open the vault. */
class WrongPasswordException : Exception("Wrong master password.")

/** A stored key (for example the biometric one) does not belong to this vault file. */
class WrongKeyException : Exception("This key does not open the vault.")

/** The file is not a vault, is damaged, or comes from a newer app version. */
class VaultFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Argon2id cost settings. They are stored in the file header, so they can be raised later. */
data class KdfParams(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
) {
    companion object {
        /** 64 MiB, 3 passes: the second recommended setting of RFC 9106, single lane. */
        val DEFAULT = KdfParams(memoryKiB = 64 * 1024, iterations = 3, parallelism = 1)
    }
}

/** A new or re-keyed vault file together with the key that opens its payload. */
class SealedVault(val file: ByteArray, val key: ByteArray)

/** A vault opened with the master password. */
class UnlockedVault(val vault: Vault, val key: ByteArray)

/**
 * The encrypted vault file.
 *
 * Design:
 *  - A random 256-bit vault key encrypts the payload with AES-256-GCM.
 *  - The master password goes through Argon2id to produce a second key, which
 *    only wraps (encrypts) the vault key.
 *  - Every byte of the header is authenticated, so cost settings and salt
 *    cannot be changed without the file failing to open.
 *
 * Because the payload is encrypted with the vault key and not directly with the
 * password, the app can keep saving while unlocked without holding the password,
 * and biometric unlock can store its own wrapped copy of the vault key.
 *
 * Layout (big-endian):
 *
 *     magic "TJRI" (4) | format (1) | kdf (1)
 *     memoryKiB (4) | iterations (4) | parallelism (4) | salt (16)
 *     keyNonce (12) | wrappedKey (32 + 16 tag)
 *     payloadNonce (12) | payload ciphertext + tag (rest)
 */
object VaultFile {
    private val MAGIC = byteArrayOf('T'.code.toByte(), 'J'.code.toByte(), 'R'.code.toByte(), 'I'.code.toByte())
    private const val FORMAT_VERSION: Byte = 1
    private const val KDF_ARGON2ID: Byte = 1

    private const val SALT_LEN = 16
    private const val NONCE_LEN = 12
    private const val KEY_LEN = 32
    private const val TAG_BITS = 128
    private const val TAG_LEN = TAG_BITS / 8

    /** Everything up to and including the salt. Authenticated when wrapping the key. */
    private const val KDF_BLOCK_LEN = 4 + 1 + 1 + 4 + 4 + 4 + SALT_LEN
    /** The KDF block plus the wrapped key. Authenticated when encrypting the payload. */
    private const val HEADER_LEN = KDF_BLOCK_LEN + NONCE_LEN + KEY_LEN + TAG_LEN
    private const val MIN_FILE_LEN = HEADER_LEN + NONCE_LEN + TAG_LEN

    /** Creates a new vault file protected by [password]. */
    fun create(
        password: CharArray,
        vault: Vault,
        params: KdfParams = KdfParams.DEFAULT,
        rng: SecureRandom = SecureRandom(),
    ): SealedVault {
        val vaultKey = ByteArray(KEY_LEN).also { rng.nextBytes(it) }
        val header = buildHeader(password, vaultKey, params, rng)
        return SealedVault(file = header + sealPayload(header, vaultKey, vault, rng), key = vaultKey)
    }

    /** Opens [file] with the master password. Runs Argon2id, so call it off the main thread. */
    fun unlock(file: ByteArray, password: CharArray): UnlockedVault {
        val header = parseHeader(file)
        val passwordKey = deriveKey(password, header.salt, header.params)
        val vaultKey = try {
            gcm(Cipher.DECRYPT_MODE, passwordKey, header.keyNonce, header.kdfBlock, header.wrappedKey)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        } finally {
            passwordKey.fill(0)
        }
        val vault = try {
            openPayload(file, vaultKey)
        } catch (e: AEADBadTagException) {
            vaultKey.fill(0)
            throw VaultFormatException("The vault file is damaged.", e)
        }
        return UnlockedVault(vault = vault, key = vaultKey)
    }

    /** Opens [file] with an already known vault key (biometric unlock). */
    fun unlockWithKey(file: ByteArray, key: ByteArray): Vault {
        parseHeader(file)
        if (key.size != KEY_LEN) throw WrongKeyException()
        return try {
            openPayload(file, key)
        } catch (e: AEADBadTagException) {
            throw WrongKeyException()
        }
    }

    /** Returns new file bytes holding [vault], keeping the header (and so the password) of [file]. */
    fun save(file: ByteArray, key: ByteArray, vault: Vault, rng: SecureRandom = SecureRandom()): ByteArray {
        parseHeader(file)
        val header = file.copyOfRange(0, HEADER_LEN)
        return header + sealPayload(header, key, vault, rng)
    }

    /**
     * Re-wraps the vault key under [newPassword] with a fresh salt. The vault key
     * itself does not change, so biometric unlock keeps working.
     */
    fun changePassword(
        file: ByteArray,
        key: ByteArray,
        newPassword: CharArray,
        params: KdfParams = KdfParams.DEFAULT,
        rng: SecureRandom = SecureRandom(),
    ): ByteArray {
        val vault = unlockWithKey(file, key)
        val header = buildHeader(newPassword, key, params, rng)
        return header + sealPayload(header, key, vault, rng)
    }

    /** True when [file] starts like a vault file. Does not prove it can be opened. */
    fun looksLikeVault(file: ByteArray): Boolean =
        file.size >= MIN_FILE_LEN && file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    // ---------------------------------------------------------------------

    private class Header(
        val params: KdfParams,
        val salt: ByteArray,
        val kdfBlock: ByteArray,
        val keyNonce: ByteArray,
        val wrappedKey: ByteArray,
    )

    private fun buildHeader(
        password: CharArray,
        vaultKey: ByteArray,
        params: KdfParams,
        rng: SecureRandom,
    ): ByteArray {
        checkParams(params)
        val salt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
        val kdfBlock = ByteBuffer.allocate(KDF_BLOCK_LEN)
            .put(MAGIC)
            .put(FORMAT_VERSION)
            .put(KDF_ARGON2ID)
            .putInt(params.memoryKiB)
            .putInt(params.iterations)
            .putInt(params.parallelism)
            .put(salt)
            .array()
        val keyNonce = ByteArray(NONCE_LEN).also { rng.nextBytes(it) }
        val passwordKey = deriveKey(password, salt, params)
        val wrappedKey = try {
            gcm(Cipher.ENCRYPT_MODE, passwordKey, keyNonce, kdfBlock, vaultKey)
        } finally {
            passwordKey.fill(0)
        }
        return kdfBlock + keyNonce + wrappedKey
    }

    private fun parseHeader(file: ByteArray): Header {
        if (file.size < MIN_FILE_LEN) throw VaultFormatException("This is not a vault file (too short).")
        val buf = ByteBuffer.wrap(file)
        val magic = ByteArray(MAGIC.size).also { buf.get(it) }
        if (!magic.contentEquals(MAGIC)) throw VaultFormatException("This is not a vault file.")
        val format = buf.get()
        if (format != FORMAT_VERSION) {
            throw VaultFormatException("This vault was written by a newer version of the app (format v$format).")
        }
        val kdf = buf.get()
        if (kdf != KDF_ARGON2ID) throw VaultFormatException("Unknown key derivation method ($kdf).")
        val params = KdfParams(memoryKiB = buf.getInt(), iterations = buf.getInt(), parallelism = buf.getInt())
        try {
            checkParams(params)
        } catch (e: IllegalArgumentException) {
            throw VaultFormatException("The vault file has invalid key derivation settings.", e)
        }
        val salt = ByteArray(SALT_LEN).also { buf.get(it) }
        val keyNonce = ByteArray(NONCE_LEN).also { buf.get(it) }
        val wrappedKey = ByteArray(KEY_LEN + TAG_LEN).also { buf.get(it) }
        return Header(
            params = params,
            salt = salt,
            kdfBlock = file.copyOfRange(0, KDF_BLOCK_LEN),
            keyNonce = keyNonce,
            wrappedKey = wrappedKey,
        )
    }

    /** Limits are checked before any expensive work, so a hostile file cannot exhaust memory. */
    private fun checkParams(p: KdfParams) {
        require(p.parallelism in 1..16) { "parallelism out of range" }
        require(p.iterations in 1..64) { "iterations out of range" }
        require(p.memoryKiB in 1024..1024 * 1024) { "memory out of range" }
        require(p.memoryKiB >= 8 * p.parallelism) { "memory too small for parallelism" }
    }

    private fun sealPayload(header: ByteArray, key: ByteArray, vault: Vault, rng: SecureRandom): ByteArray {
        val nonce = ByteArray(NONCE_LEN).also { rng.nextBytes(it) }
        val plain = VaultCodec.encode(vault)
        try {
            return nonce + gcm(Cipher.ENCRYPT_MODE, key, nonce, header, plain)
        } finally {
            plain.fill(0)
        }
    }

    private fun openPayload(file: ByteArray, key: ByteArray): Vault {
        val header = file.copyOfRange(0, HEADER_LEN)
        val nonce = file.copyOfRange(HEADER_LEN, HEADER_LEN + NONCE_LEN)
        val cipherText = file.copyOfRange(HEADER_LEN + NONCE_LEN, file.size)
        val plain = gcm(Cipher.DECRYPT_MODE, key, nonce, header, cipherText)
        try {
            return VaultCodec.decode(plain)
        } finally {
            plain.fill(0)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        val passwordBytes = utf8(password)
        try {
            val argon = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(params.memoryKiB)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .withSalt(salt)
                .build()
            val generator = Argon2BytesGenerator()
            generator.init(argon)
            val out = ByteArray(KEY_LEN)
            generator.generateBytes(passwordBytes, out)
            return out
        } finally {
            passwordBytes.fill(0)
        }
    }

    /** UTF-8 bytes of [chars] without going through an immutable String. */
    private fun utf8(chars: CharArray): ByteArray {
        val encoded = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        val bytes = ByteArray(encoded.remaining())
        encoded.get(bytes)
        if (encoded.hasArray()) encoded.array().fill(0)
        return bytes
    }

    @Throws(GeneralSecurityException::class)
    private fun gcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(data)
    }
}
