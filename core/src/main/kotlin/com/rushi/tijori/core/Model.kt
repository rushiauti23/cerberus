package com.rushi.tijori.core

/**
 * Rules attached to a tag. An entry with several tags gets the strictest
 * combination of all of them (see [Policies.merge]).
 */
data class TagPolicy(
    /** Days after which the password should be rotated. 0 means it never expires. */
    val rotationDays: Int = 0,
    /** Minimum password length. 0 means no minimum. */
    val minLength: Int = 0,
    val requireUpper: Boolean = false,
    val requireLower: Boolean = false,
    val requireDigit: Boolean = false,
    val requireSymbol: Boolean = false,
)

data class Tag(
    val id: String,
    val name: String,
    val policy: TagPolicy = TagPolicy(),
)

data class Category(
    val id: String,
    val name: String,
)

data class OldPassword(
    val password: String,
    val retiredAt: Long,
)

data class Entry(
    val id: String,
    val title: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val categoryId: String? = null,
    val tagIds: List<String> = emptyList(),
    val favorite: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** When the current password was set. Rotation due dates count from here. */
    val passwordChangedAt: Long = 0L,
    /** Previous passwords, newest first. */
    val history: List<OldPassword> = emptyList(),
)

data class Vault(
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val entries: List<Entry> = emptyList(),
)
