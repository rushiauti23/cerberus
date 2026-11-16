package com.rushi.tijori.core

data class DueSummary(val overdue: Int, val dueSoon: Int) {
    val isEmpty: Boolean get() = overdue == 0 && dueSoon == 0
}

/**
 * Reminder decisions. The background job cannot read the encrypted vault, so it
 * works only from a list of due times that the app stores while unlocked.
 */
object Reminders {
    /** How often to repeat a reminder when nothing has changed. */
    const val REPEAT_AFTER_MS = 7 * Policies.DAY_MS

    fun summarize(dueTimes: List<Long>, now: Long): DueSummary {
        val soonLimit = now + Policies.DUE_SOON_DAYS * Policies.DAY_MS
        return DueSummary(
            overdue = dueTimes.count { it <= now },
            dueSoon = dueTimes.count { it > now && it <= soonLimit },
        )
    }

    /**
     * Notify when something is due and either the picture changed since the last
     * notification or a week has passed. Avoids a daily nag for the same thing.
     */
    fun shouldNotify(current: DueSummary, last: DueSummary?, lastNotifiedAt: Long, now: Long): Boolean {
        if (current.isEmpty) return false
        if (last == null || last != current) return true
        return now - lastNotifiedAt >= REPEAT_AFTER_MS
    }

    fun message(summary: DueSummary): String = when {
        summary.overdue > 0 && summary.dueSoon > 0 ->
            "${plural(summary.overdue)} overdue for rotation, ${summary.dueSoon} more due within a week."
        summary.overdue > 0 -> "${plural(summary.overdue)} overdue for rotation."
        else -> "${plural(summary.dueSoon)} due for rotation within a week."
    }

    private fun plural(n: Int): String = if (n == 1) "1 password is" else "$n passwords are"
}
