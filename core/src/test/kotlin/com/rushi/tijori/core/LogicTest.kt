package com.rushi.tijori.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private val day = Policies.DAY_MS

    // --- generator -------------------------------------------------------

    @Test
    fun generatorHonoursLengthAndClasses() {
        repeat(300) {
            val p = PasswordGenerator.generate(GeneratorSpec(length = 16))
            assertEquals(16, p.length)
            assertTrue(p.any { it.isUpperCase() })
            assertTrue(p.any { it.isLowerCase() })
            assertTrue(p.any { it.isDigit() })
            assertTrue(p.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun generatorUsesOnlyEnabledClasses() {
        repeat(100) {
            val p = PasswordGenerator.generate(GeneratorSpec(length = 30, upper = false, symbols = false))
            assertTrue(p.all { it.isLowerCase() || it.isDigit() })
        }
        repeat(100) {
            val p = PasswordGenerator.generate(GeneratorSpec(length = 40, avoidAmbiguous = true))
            assertTrue(p.none { it in "O0oIl1" })
        }
    }

    @Test
    fun generatorClampsLengthAndRejectsEmptyAlphabet() {
        assertEquals(PasswordGenerator.MIN_LENGTH, PasswordGenerator.generate(GeneratorSpec(length = 1)).length)
        assertEquals(PasswordGenerator.MAX_LENGTH, PasswordGenerator.generate(GeneratorSpec(length = 9999)).length)
        assertThrows(IllegalArgumentException::class.java) {
            PasswordGenerator.generate(GeneratorSpec(upper = false, lower = false, digits = false, symbols = false))
        }
    }

    @Test
    fun generatorDoesNotRepeatItself() {
        val seen = (1..200).map { PasswordGenerator.generate(GeneratorSpec()) }.toSet()
        assertEquals(200, seen.size)
    }

    @Test
    fun generatedPasswordsSatisfyThePolicyTheyWereMadeFor() {
        val policy = TagPolicy(90, 24, requireUpper = true, requireLower = true, requireDigit = true, requireSymbol = true)
        val base = GeneratorSpec(length = 8, upper = false, lower = true, digits = false, symbols = false)
        val spec = Policies.generatorSpec(policy, base)
        repeat(200) {
            assertEquals(emptyList<Violation>(), Policies.violations(PasswordGenerator.generate(spec), policy))
        }
    }

    // --- policies --------------------------------------------------------

    @Test
    fun mergeTakesTheStrictestOfEverything() {
        val merged = Policies.merge(
            listOf(
                TagPolicy(rotationDays = 180, minLength = 16, requireUpper = true),
                TagPolicy(rotationDays = 90, minLength = 8, requireSymbol = true),
                TagPolicy(rotationDays = 0, minLength = 0),
            )
        )
        assertEquals(TagPolicy(90, 16, requireUpper = true, requireSymbol = true), merged)
        assertEquals(TagPolicy(), Policies.merge(emptyList()))
    }

    @Test
    fun violationsAreReported() {
        val policy = TagPolicy(0, 10, true, true, true, true)
        assertEquals(
            listOf(Violation.TOO_SHORT, Violation.NEEDS_UPPER, Violation.NEEDS_DIGIT, Violation.NEEDS_SYMBOL),
            Policies.violations("abc", policy),
        )
        assertEquals(emptyList<Violation>(), Policies.violations("Abcdefgh1!", policy))
        assertEquals(emptyList<Violation>(), Policies.violations("", TagPolicy()))
    }

    @Test
    fun rotationStatusFollowsTheDueDate() {
        val entry = Entry(id = "e", title = "x", passwordChangedAt = 0)
        val policy = TagPolicy(rotationDays = 90)
        assertEquals(90 * day, Policies.dueAt(entry, policy))
        assertEquals(RotationStatus.OK, Policies.status(entry, policy, 82 * day))
        assertEquals(RotationStatus.DUE_SOON, Policies.status(entry, policy, 83 * day))
        assertEquals(RotationStatus.DUE_SOON, Policies.status(entry, policy, 90 * day - 1))
        assertEquals(RotationStatus.OVERDUE, Policies.status(entry, policy, 90 * day))
        assertEquals(RotationStatus.NONE, Policies.status(entry, TagPolicy(), 500 * day))
        assertNull(Policies.dueAt(entry, TagPolicy()))
        assertEquals(10L, Policies.daysLeft(entry, policy, 80 * day))
        assertEquals(-1L, Policies.daysLeft(entry, policy, 90 * day + 1))
    }

    // --- vault operations ------------------------------------------------

    @Test
    fun newEntryGetsTimestamps() {
        val v = VaultOps.saveEntry(Vault(), Entry(id = "e", title = "A", password = "one"), now = 100)
        val e = v.entries.single()
        assertEquals(100, e.createdAt)
        assertEquals(100, e.updatedAt)
        assertEquals(100, e.passwordChangedAt)
        assertTrue(e.history.isEmpty())
    }

    @Test
    fun changingPasswordMovesOldOneToHistoryAndRestartsClock() {
        var v = VaultOps.saveEntry(Vault(), Entry(id = "e", title = "A", password = "one"), now = 100)
        v = VaultOps.saveEntry(v, v.entries.single().copy(password = "two"), now = 200)
        val e = v.entries.single()
        assertEquals(100, e.createdAt)
        assertEquals(200, e.passwordChangedAt)
        assertEquals(listOf(OldPassword("one", 200)), e.history)
    }

    @Test
    fun editingOtherFieldsLeavesRotationClockAlone() {
        var v = VaultOps.saveEntry(Vault(), Entry(id = "e", title = "A", password = "one"), now = 100)
        v = VaultOps.saveEntry(v, v.entries.single().copy(title = "B", notes = "n"), now = 200)
        val e = v.entries.single()
        assertEquals("B", e.title)
        assertEquals(100, e.passwordChangedAt)
        assertEquals(200, e.updatedAt)
        assertTrue(e.history.isEmpty())
    }

    @Test
    fun historyIsCapped() {
        var v = VaultOps.saveEntry(Vault(), Entry(id = "e", title = "A", password = "p0"), now = 0)
        for (i in 1..25) v = VaultOps.saveEntry(v, v.entries.single().copy(password = "p$i"), now = i.toLong())
        val history = v.entries.single().history
        assertEquals(VaultOps.MAX_HISTORY, history.size)
        assertEquals("p24", history.first().password)
    }

    @Test
    fun deletingCategoryOrTagKeepsEntries() {
        var v = Vault(
            categories = listOf(Category("c", "Work")),
            tags = listOf(Tag("t", "x"), Tag("u", "y")),
            entries = listOf(Entry(id = "e", title = "A", categoryId = "c", tagIds = listOf("t", "u"))),
        )
        v = VaultOps.deleteCategory(v, "c")
        v = VaultOps.deleteTag(v, "t")
        assertTrue(v.categories.isEmpty())
        assertEquals(listOf(Tag("u", "y")), v.tags)
        assertNull(v.entries.single().categoryId)
        assertEquals(listOf("u"), v.entries.single().tagIds)
    }

    @Test
    fun saveCategoryAndTagInsertOrReplace() {
        var v = VaultOps.saveCategory(Vault(), Category("c", "Work"))
        v = VaultOps.saveCategory(v, Category("c", "Office"))
        v = VaultOps.saveTag(v, Tag("t", "a"))
        v = VaultOps.saveTag(v, Tag("t", "b", TagPolicy(rotationDays = 30)))
        assertEquals(listOf(Category("c", "Office")), v.categories)
        assertEquals(listOf(Tag("t", "b", TagPolicy(rotationDays = 30))), v.tags)
    }

    @Test
    fun reusedPasswordsAreFound() {
        val v = Vault(
            entries = listOf(
                Entry(id = "a", title = "A", password = "same"),
                Entry(id = "b", title = "B", password = "same"),
                Entry(id = "c", title = "C", password = "unique"),
                Entry(id = "d", title = "D", password = ""),
                Entry(id = "e", title = "E", password = ""),
            )
        )
        assertEquals(setOf("a", "b"), VaultOps.reusedPasswordIds(v))
    }

    @Test
    fun filterSearchesAndSorts() {
        val v = Vault(
            categories = listOf(Category("c", "Work")),
            tags = listOf(Tag("t", "work-90d", TagPolicy(rotationDays = 90))),
            entries = listOf(
                Entry(id = "1", title = "zeta", categoryId = "c", tagIds = listOf("t"), passwordChangedAt = 0),
                Entry(id = "2", title = "Alpha", username = "rushi@example.com", passwordChangedAt = 0),
                Entry(id = "3", title = "beta", favorite = true, passwordChangedAt = 0),
            ),
        )
        assertEquals(listOf("3", "2", "1"), VaultOps.filter(v, "", null, false, 0).map { it.id })
        assertEquals(listOf("2"), VaultOps.filter(v, "RUSHI", null, false, 0).map { it.id })
        assertEquals(listOf("1"), VaultOps.filter(v, "work-9", null, false, 0).map { it.id })
        assertEquals(listOf("1"), VaultOps.filter(v, "", "c", false, 0).map { it.id })
        assertEquals(emptyList<String>(), VaultOps.filter(v, "", null, true, 0).map { it.id })
        assertEquals(listOf("1"), VaultOps.filter(v, "", null, true, 85 * day).map { it.id })
        assertEquals(listOf(90 * day), VaultOps.dueTimes(v))
    }

    @Test
    fun defaultVaultHasStarterCategoriesWithUniqueIds() {
        val v = VaultOps.defaultVault()
        assertEquals(listOf("Personal", "Work", "Microsoft", "GitHub", "GPay"), v.categories.map { it.name })
        val ids = v.categories.map { it.id } + v.tags.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(v.tags.any { it.policy.rotationDays == 90 })
    }

    // --- reminders -------------------------------------------------------

    @Test
    fun remindersSummariseAndDoNotNag() {
        val now = 100 * day
        val summary = Reminders.summarize(listOf(99 * day, 100 * day, 103 * day, 107 * day, 108 * day), now)
        assertEquals(DueSummary(overdue = 2, dueSoon = 2), summary)

        assertFalse(Reminders.shouldNotify(DueSummary(0, 0), null, 0, now))
        assertTrue(Reminders.shouldNotify(summary, null, 0, now))
        assertFalse(Reminders.shouldNotify(summary, summary, now - day, now))
        assertTrue(Reminders.shouldNotify(summary, summary, now - 7 * day, now))
        assertTrue(Reminders.shouldNotify(summary, DueSummary(1, 2), now - day, now))

        assertEquals("1 password is overdue for rotation.", Reminders.message(DueSummary(1, 0)))
        assertEquals("3 passwords are due for rotation within a week.", Reminders.message(DueSummary(0, 3)))
        assertEquals(
            "2 passwords are overdue for rotation, 2 more due within a week.",
            Reminders.message(summary),
        )
    }
}
