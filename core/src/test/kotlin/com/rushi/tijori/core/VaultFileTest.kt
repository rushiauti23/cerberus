package com.rushi.tijori.core

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultFileTest {
    /** Cheap settings so the tests run fast. The app uses KdfParams.DEFAULT. */
    private val fast = KdfParams(memoryKiB = 1024, iterations = 1, parallelism = 1)

    private val sample = Vault(
        categories = listOf(Category("c1", "Work"), Category("c2", "Personal")),
        tags = listOf(
            Tag("t1", "work-90d", TagPolicy(90, 14, true, true, true, true)),
            Tag("t2", "plain"),
        ),
        entries = listOf(
            Entry(
                id = "e1", title = "GitHub", username = "rushi", password = "p@ss-ωορδ-पासवर्ड-🔐",
                url = "https://github.com", notes = "line1\nline2", categoryId = "c1",
                tagIds = listOf("t1", "t2"), favorite = true,
                createdAt = 1_000, updatedAt = 2_000, passwordChangedAt = 1_500,
                history = listOf(OldPassword("old-one", 1_500), OldPassword("older", 1_200)),
            ),
            Entry(id = "e2", title = "No category", createdAt = 5, updatedAt = 6, passwordChangedAt = 7),
        ),
    )

    private fun hex(s: String): ByteArray =
        s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** RFC 9106, section 5.3: proves the Argon2id on the classpath is a correct one. */
    @Test
    fun argon2idMatchesRfc9106TestVector() {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(32)
            .withIterations(3)
            .withParallelism(4)
            .withSalt(ByteArray(16) { 0x02 })
            .withSecret(ByteArray(8) { 0x03 })
            .withAdditional(ByteArray(12) { 0x04 })
            .build()
        val out = ByteArray(32)
        Argon2BytesGenerator().apply { init(params) }.generateBytes(ByteArray(32) { 0x01 }, out)
        assertArrayEquals(
            hex("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659"),
            out,
        )
    }

    @Test
    fun roundTripKeepsEveryField() {
        val sealed = VaultFile.create("correct horse".toCharArray(), sample, fast)
        val opened = VaultFile.unlock(sealed.file, "correct horse".toCharArray())
        assertEquals(sample, opened.vault)
        assertArrayEquals(sealed.key, opened.key)
    }

    @Test
    fun emptyVaultRoundTrips() {
        val sealed = VaultFile.create("pw".toCharArray(), Vault(), fast)
        assertEquals(Vault(), VaultFile.unlock(sealed.file, "pw".toCharArray()).vault)
    }

    @Test
    fun wrongPasswordIsRejected() {
        val sealed = VaultFile.create("right".toCharArray(), sample, fast)
        assertThrows(WrongPasswordException::class.java) {
            VaultFile.unlock(sealed.file, "wrong".toCharArray())
        }
    }

    @Test
    fun plaintextDoesNotAppearInFile() {
        val sealed = VaultFile.create("pw".toCharArray(), sample, fast)
        val asText = String(sealed.file, Charsets.ISO_8859_1)
        assertFalse(asText.contains("GitHub"))
        assertFalse(asText.contains("rushi"))
        assertFalse(asText.contains("old-one"))
    }

    @Test
    fun everySingleBitFlipIsDetected() {
        val sealed = VaultFile.create("pw".toCharArray(), sample, fast)
        // Flipping any byte must never yield a vault: header, wrapped key and payload are all authenticated.
        // Skip the cost fields (bytes 6..17): changing them only changes the derived key, covered below.
        for (i in sealed.file.indices) {
            if (i in 6..17) continue
            val damaged = sealed.file.copyOf()
            damaged[i] = (damaged[i].toInt() xor 0x01).toByte()
            try {
                VaultFile.unlock(damaged, "pw".toCharArray())
                throw AssertionError("byte $i was changed and the vault still opened")
            } catch (e: WrongPasswordException) {
                // expected for header and wrapped-key bytes
            } catch (e: VaultFormatException) {
                // expected for magic, version and payload bytes
            }
        }
    }

    @Test
    fun changingCostSettingsInHeaderFailsToOpen() {
        val sealed = VaultFile.create("pw".toCharArray(), sample, fast)
        val damaged = sealed.file.copyOf()
        damaged[13] = 2 // iterations 1 -> 2
        assertThrows(WrongPasswordException::class.java) {
            VaultFile.unlock(damaged, "pw".toCharArray())
        }
    }

    @Test
    fun hostileCostSettingsAreRefusedBeforeAnyWork() {
        val sealed = VaultFile.create("pw".toCharArray(), sample, fast)
        val hostile = sealed.file.copyOf()
        hostile[6] = 0x7f // memory -> about 2 TiB
        assertThrows(VaultFormatException::class.java) {
            VaultFile.unlock(hostile, "pw".toCharArray())
        }
    }

    @Test
    fun garbageAndTruncatedFilesAreRejected() {
        assertThrows(VaultFormatException::class.java) { VaultFile.unlock(ByteArray(0), "pw".toCharArray()) }
        assertThrows(VaultFormatException::class.java) { VaultFile.unlock(ByteArray(500), "pw".toCharArray()) }
        val sealed = VaultFile.create("pw".toCharArray(), sample, fast)
        val cut = sealed.file.copyOf(sealed.file.size - 5)
        assertThrows(VaultFormatException::class.java) { VaultFile.unlock(cut, "pw".toCharArray()) }
        assertFalse(VaultFile.looksLikeVault(ByteArray(500)))
        assertTrue(VaultFile.looksLikeVault(sealed.file))
    }

    @Test
    fun saveKeepsPasswordAndUsesFreshNonce() {
        val sealed = VaultFile.create("pw".toCharArray(), sample, fast)
        val changed = sample.copy(entries = sample.entries.drop(1))
        val saved1 = VaultFile.save(sealed.file, sealed.key, changed)
        val saved2 = VaultFile.save(sealed.file, sealed.key, changed)
        assertEquals(changed, VaultFile.unlock(saved1, "pw".toCharArray()).vault)
        assertEquals(changed, VaultFile.unlockWithKey(saved2, sealed.key))
        assertFalse("two saves of the same data must differ", saved1.contentEquals(saved2))
    }

    @Test
    fun unlockWithWrongKeyIsRejected() {
        val a = VaultFile.create("pw".toCharArray(), sample, fast)
        val b = VaultFile.create("pw".toCharArray(), sample, fast)
        assertThrows(WrongKeyException::class.java) { VaultFile.unlockWithKey(a.file, b.key) }
        assertThrows(WrongKeyException::class.java) { VaultFile.unlockWithKey(a.file, ByteArray(5)) }
    }

    @Test
    fun changePasswordKeepsDataAndKeyButRetiresOldPassword() {
        val sealed = VaultFile.create("old".toCharArray(), sample, fast)
        val rekeyed = VaultFile.changePassword(sealed.file, sealed.key, "new".toCharArray(), fast)
        val opened = VaultFile.unlock(rekeyed, "new".toCharArray())
        assertEquals(sample, opened.vault)
        assertArrayEquals(sealed.key, opened.key)
        assertThrows(WrongPasswordException::class.java) { VaultFile.unlock(rekeyed, "old".toCharArray()) }
    }

    @Test
    fun samePasswordGivesDifferentFilesAndKeys() {
        val a = VaultFile.create("pw".toCharArray(), sample, fast)
        val b = VaultFile.create("pw".toCharArray(), sample, fast)
        assertFalse(a.key.contentEquals(b.key))
        assertNotEquals(a.file.toList(), b.file.toList())
    }

    @Test
    fun defaultSettingsOpenTheirOwnFile() {
        // One run at the real 64 MiB cost, to prove the production settings work end to end.
        val sealed = VaultFile.create("pw".toCharArray(), sample)
        assertEquals(sample, VaultFile.unlock(sealed.file, "pw".toCharArray()).vault)
    }
}
