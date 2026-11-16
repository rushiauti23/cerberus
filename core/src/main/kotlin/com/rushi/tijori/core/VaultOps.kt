package com.rushi.tijori.core

import java.util.UUID

/** Pure functions that change a [Vault]. Each returns a new vault; nothing is mutated. */
object VaultOps {
    const val MAX_HISTORY = 10

    fun newId(): String = UUID.randomUUID().toString()

    /** The starter vault: the categories and example tag policies a new user sees. */
    fun defaultVault(): Vault = Vault(
        categories = listOf("Personal", "Work", "Microsoft", "GitHub", "GPay").map { Category(newId(), it) },
        tags = listOf(
            Tag(
                newId(), "work-90d",
                TagPolicy(
                    rotationDays = 90, minLength = 14,
                    requireUpper = true, requireLower = true, requireDigit = true, requireSymbol = true,
                ),
            ),
            Tag(
                newId(), "banking-180d",
                TagPolicy(rotationDays = 180, minLength = 12, requireUpper = true, requireLower = true, requireDigit = true),
            ),
            Tag(newId(), "yearly", TagPolicy(rotationDays = 365, minLength = 12)),
        ),
    )

    /**
     * Inserts [draft] or replaces the entry with the same id. Timestamps and
     * history are managed here: when the password of an existing entry changes,
     * the old one moves to the history and the rotation clock restarts.
     */
    fun saveEntry(vault: Vault, draft: Entry, now: Long): Vault {
        val old = vault.entries.firstOrNull { it.id == draft.id }
        if (old == null) {
            val created = draft.copy(createdAt = now, updatedAt = now, passwordChangedAt = now, history = emptyList())
            return vault.copy(entries = vault.entries + created)
        }
        val passwordChanged = old.password != draft.password
        val history = if (passwordChanged && old.password.isNotEmpty()) {
            (listOf(OldPassword(old.password, now)) + old.history).take(MAX_HISTORY)
        } else {
            old.history
        }
        val updated = draft.copy(
            createdAt = old.createdAt,
            updatedAt = now,
            passwordChangedAt = if (passwordChanged) now else old.passwordChangedAt,
            history = history,
        )
        return vault.copy(entries = vault.entries.map { if (it.id == updated.id) updated else it })
    }

    fun deleteEntry(vault: Vault, entryId: String): Vault =
        vault.copy(entries = vault.entries.filterNot { it.id == entryId })

    fun saveCategory(vault: Vault, category: Category): Vault =
        if (vault.categories.any { it.id == category.id }) {
            vault.copy(categories = vault.categories.map { if (it.id == category.id) category else it })
        } else {
            vault.copy(categories = vault.categories + category)
        }

    /** Removes the category. Its entries are kept and become uncategorised. */
    fun deleteCategory(vault: Vault, categoryId: String): Vault = vault.copy(
        categories = vault.categories.filterNot { it.id == categoryId },
        entries = vault.entries.map { if (it.categoryId == categoryId) it.copy(categoryId = null) else it },
    )

    fun saveTag(vault: Vault, tag: Tag): Vault =
        if (vault.tags.any { it.id == tag.id }) {
            vault.copy(tags = vault.tags.map { if (it.id == tag.id) tag else it })
        } else {
            vault.copy(tags = vault.tags + tag)
        }

    /** Removes the tag from the vault and from every entry that carried it. */
    fun deleteTag(vault: Vault, tagId: String): Vault = vault.copy(
        tags = vault.tags.filterNot { it.id == tagId },
        entries = vault.entries.map { if (tagId in it.tagIds) it.copy(tagIds = it.tagIds - tagId) else it },
    )

    /** Ids of entries whose password is also used by at least one other entry. */
    fun reusedPasswordIds(vault: Vault): Set<String> =
        vault.entries
            .filter { it.password.isNotEmpty() }
            .groupBy { it.password }
            .values
            .filter { it.size > 1 }
            .flatten()
            .map { it.id }
            .toSet()

    /** Rotation due times of every entry that has one. Holds no names or secrets. */
    fun dueTimes(vault: Vault): List<Long> =
        vault.entries.mapNotNull { Policies.dueAt(it, Policies.forEntry(it, vault.tags)) }

    /**
     * Entries for the list screen: filtered, then favourites first, then by title.
     * [categoryId] null means all categories. [onlyDue] keeps entries that are
     * overdue or due soon.
     */
    fun filter(vault: Vault, query: String, categoryId: String?, onlyDue: Boolean, now: Long): List<Entry> {
        val needle = query.trim().lowercase()
        val tagNames = vault.tags.associate { it.id to it.name.lowercase() }
        return vault.entries
            .asSequence()
            .filter { categoryId == null || it.categoryId == categoryId }
            .filter { entry ->
                needle.isEmpty() ||
                    entry.title.lowercase().contains(needle) ||
                    entry.username.lowercase().contains(needle) ||
                    entry.url.lowercase().contains(needle) ||
                    entry.tagIds.any { tagNames[it]?.contains(needle) == true }
            }
            .filter { entry ->
                if (!onlyDue) return@filter true
                val status = Policies.status(entry, Policies.forEntry(entry, vault.tags), now)
                status == RotationStatus.OVERDUE || status == RotationStatus.DUE_SOON
            }
            .sortedWith(compareByDescending<Entry> { it.favorite }.thenBy { it.title.lowercase() })
            .toList()
    }
}
