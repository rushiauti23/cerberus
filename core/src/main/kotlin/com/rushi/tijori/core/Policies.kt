package com.rushi.tijori.core

enum class Violation { TOO_SHORT, NEEDS_UPPER, NEEDS_LOWER, NEEDS_DIGIT, NEEDS_SYMBOL }

enum class RotationStatus {
    /** No tag on the entry asks for rotation. */
    NONE,
    OK,
    /** Due within [Policies.DUE_SOON_DAYS] days. */
    DUE_SOON,
    OVERDUE,
}

object Policies {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val DUE_SOON_DAYS = 7

    /**
     * Combines several tag policies into the strictest one: the shortest
     * rotation period, the longest minimum length, and every required
     * character class.
     */
    fun merge(policies: List<TagPolicy>): TagPolicy {
        val rotations = policies.map { it.rotationDays }.filter { it > 0 }
        return TagPolicy(
            rotationDays = rotations.minOrNull() ?: 0,
            minLength = policies.maxOfOrNull { it.minLength }?.coerceAtLeast(0) ?: 0,
            requireUpper = policies.any { it.requireUpper },
            requireLower = policies.any { it.requireLower },
            requireDigit = policies.any { it.requireDigit },
            requireSymbol = policies.any { it.requireSymbol },
        )
    }

    /** The policy that applies to an entry carrying [tagIds]. */
    fun forTags(tagIds: List<String>, tags: List<Tag>): TagPolicy =
        merge(tags.filter { it.id in tagIds }.map { it.policy })

    fun forEntry(entry: Entry, tags: List<Tag>): TagPolicy = forTags(entry.tagIds, tags)

    fun violations(password: String, policy: TagPolicy): List<Violation> = buildList {
        if (password.length < policy.minLength) add(Violation.TOO_SHORT)
        if (policy.requireUpper && password.none { it.isUpperCase() }) add(Violation.NEEDS_UPPER)
        if (policy.requireLower && password.none { it.isLowerCase() }) add(Violation.NEEDS_LOWER)
        if (policy.requireDigit && password.none { it.isDigit() }) add(Violation.NEEDS_DIGIT)
        if (policy.requireSymbol && password.all { it.isLetterOrDigit() }) add(Violation.NEEDS_SYMBOL)
    }

    /** When the entry's password is due for rotation, or null when it never expires. */
    fun dueAt(entry: Entry, policy: TagPolicy): Long? =
        if (policy.rotationDays > 0) entry.passwordChangedAt + policy.rotationDays * DAY_MS else null

    fun status(entry: Entry, policy: TagPolicy, now: Long): RotationStatus {
        val due = dueAt(entry, policy) ?: return RotationStatus.NONE
        return when {
            now >= due -> RotationStatus.OVERDUE
            now >= due - DUE_SOON_DAYS * DAY_MS -> RotationStatus.DUE_SOON
            else -> RotationStatus.OK
        }
    }

    /** Whole days until the due date; negative when overdue. Null when it never expires. */
    fun daysLeft(entry: Entry, policy: TagPolicy, now: Long): Long? {
        val due = dueAt(entry, policy) ?: return null
        return Math.floorDiv(due - now, DAY_MS)
    }

    /**
     * Generator settings that satisfy [policy]. Options the user already chose
     * in [base] are kept; the policy can only add requirements and length.
     */
    fun generatorSpec(policy: TagPolicy, base: GeneratorSpec = GeneratorSpec()): GeneratorSpec = base.copy(
        length = maxOf(base.length, policy.minLength).coerceAtMost(PasswordGenerator.MAX_LENGTH),
        upper = base.upper || policy.requireUpper,
        lower = base.lower || policy.requireLower,
        digits = base.digits || policy.requireDigit,
        symbols = base.symbols || policy.requireSymbol,
    )
}
