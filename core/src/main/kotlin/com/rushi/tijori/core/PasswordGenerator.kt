package com.rushi.tijori.core

import java.security.SecureRandom

data class GeneratorSpec(
    val length: Int = 20,
    val upper: Boolean = true,
    val lower: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    /** Leaves out characters that look alike, such as O and 0 or l and 1. */
    val avoidAmbiguous: Boolean = false,
)

object PasswordGenerator {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 128

    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#%^&*()-_=+[]{};:,.?"
    private const val AMBIGUOUS = "O0oIl1"

    /**
     * Returns a random password with at least one character from every enabled
     * class. Uses [SecureRandom]; `nextInt(bound)` is unbiased.
     */
    fun generate(spec: GeneratorSpec, rng: SecureRandom = SecureRandom()): String {
        val classes = buildList {
            if (spec.upper) add(UPPER)
            if (spec.lower) add(LOWER)
            if (spec.digits) add(DIGITS)
            if (spec.symbols) add(SYMBOLS)
        }.map { set -> if (spec.avoidAmbiguous) set.filterNot { it in AMBIGUOUS } else set }

        require(classes.isNotEmpty()) { "Turn on at least one kind of character." }

        val length = spec.length.coerceIn(maxOf(MIN_LENGTH, classes.size), MAX_LENGTH)
        val all = classes.joinToString("")
        val chars = CharArray(length)

        // One from each class first, so every requirement is met...
        for ((i, set) in classes.withIndex()) chars[i] = set[rng.nextInt(set.length)]
        // ...the rest from the whole alphabet...
        for (i in classes.size until length) chars[i] = all[rng.nextInt(all.length)]
        // ...then shuffle so the guaranteed ones are not always at the front.
        for (i in length - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }
}
