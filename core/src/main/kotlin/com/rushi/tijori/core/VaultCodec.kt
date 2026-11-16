package com.rushi.tijori.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException

/**
 * Turns a [Vault] into bytes and back. This is the plaintext that gets
 * encrypted by [VaultFile]; it never touches disk unencrypted.
 *
 * The format is a simple versioned binary layout so the core needs no
 * serialization library.
 */
internal object VaultCodec {
    private const val PAYLOAD_VERSION = 1
    private const val MAX_ITEMS = 1_000_000

    fun encode(vault: Vault): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(PAYLOAD_VERSION)

            out.writeInt(vault.categories.size)
            for (c in vault.categories) {
                out.writeString(c.id)
                out.writeString(c.name)
            }

            out.writeInt(vault.tags.size)
            for (t in vault.tags) {
                out.writeString(t.id)
                out.writeString(t.name)
                out.writeInt(t.policy.rotationDays)
                out.writeInt(t.policy.minLength)
                out.writeBoolean(t.policy.requireUpper)
                out.writeBoolean(t.policy.requireLower)
                out.writeBoolean(t.policy.requireDigit)
                out.writeBoolean(t.policy.requireSymbol)
            }

            out.writeInt(vault.entries.size)
            for (e in vault.entries) {
                out.writeString(e.id)
                out.writeString(e.title)
                out.writeString(e.username)
                out.writeString(e.password)
                out.writeString(e.url)
                out.writeString(e.notes)
                out.writeBoolean(e.categoryId != null)
                if (e.categoryId != null) out.writeString(e.categoryId)
                out.writeInt(e.tagIds.size)
                for (id in e.tagIds) out.writeString(id)
                out.writeBoolean(e.favorite)
                out.writeLong(e.createdAt)
                out.writeLong(e.updatedAt)
                out.writeLong(e.passwordChangedAt)
                out.writeInt(e.history.size)
                for (h in e.history) {
                    out.writeString(h.password)
                    out.writeLong(h.retiredAt)
                }
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): Vault {
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val version = input.readInt()
                if (version != PAYLOAD_VERSION) {
                    throw VaultFormatException(
                        "This vault was written by a newer version of the app (payload v$version)."
                    )
                }

                val categories = List(input.readCount()) {
                    Category(id = input.readString(), name = input.readString())
                }

                val tags = List(input.readCount()) {
                    Tag(
                        id = input.readString(),
                        name = input.readString(),
                        policy = TagPolicy(
                            rotationDays = input.readInt(),
                            minLength = input.readInt(),
                            requireUpper = input.readBoolean(),
                            requireLower = input.readBoolean(),
                            requireDigit = input.readBoolean(),
                            requireSymbol = input.readBoolean(),
                        ),
                    )
                }

                val entries = List(input.readCount()) {
                    val id = input.readString()
                    val title = input.readString()
                    val username = input.readString()
                    val password = input.readString()
                    val url = input.readString()
                    val notes = input.readString()
                    val categoryId = if (input.readBoolean()) input.readString() else null
                    val tagIds = List(input.readCount()) { input.readString() }
                    val favorite = input.readBoolean()
                    val createdAt = input.readLong()
                    val updatedAt = input.readLong()
                    val passwordChangedAt = input.readLong()
                    val history = List(input.readCount()) {
                        OldPassword(password = input.readString(), retiredAt = input.readLong())
                    }
                    Entry(
                        id = id,
                        title = title,
                        username = username,
                        password = password,
                        url = url,
                        notes = notes,
                        categoryId = categoryId,
                        tagIds = tagIds,
                        favorite = favorite,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        passwordChangedAt = passwordChangedAt,
                        history = history,
                    )
                }

                return Vault(categories = categories, tags = tags, entries = entries)
            }
        } catch (e: EOFException) {
            throw VaultFormatException("Vault data is truncated.", e)
        } catch (e: IOException) {
            throw VaultFormatException("Vault data is unreadable.", e)
        }
    }

    private fun DataOutputStream.writeString(value: String) {
        val utf8 = value.toByteArray(Charsets.UTF_8)
        writeInt(utf8.size)
        write(utf8)
    }

    private fun DataInputStream.readString(): String {
        val length = readInt()
        if (length < 0 || length > available()) {
            throw VaultFormatException("Vault data is corrupt (bad text length).")
        }
        val utf8 = ByteArray(length)
        readFully(utf8)
        return String(utf8, Charsets.UTF_8)
    }

    private fun DataInputStream.readCount(): Int {
        val count = readInt()
        if (count < 0 || count > MAX_ITEMS) {
            throw VaultFormatException("Vault data is corrupt (bad item count).")
        }
        return count
    }
}
