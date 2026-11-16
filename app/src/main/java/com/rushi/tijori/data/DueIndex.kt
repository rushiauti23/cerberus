package com.rushi.tijori.data

import android.content.Context
import com.rushi.tijori.core.DueSummary

/**
 * What the reminder job is allowed to know while the vault is locked: only the
 * times at which passwords fall due. No titles, usernames or secrets are kept
 * here, so a reminder can say how many passwords are due but not which.
 */
object DueIndex {
    private const val PREFS = "rotation_reminders"
    private const val KEY_DUE = "due_times"
    private const val KEY_LAST_OVERDUE = "last_overdue"
    private const val KEY_LAST_SOON = "last_soon"
    private const val KEY_LAST_AT = "last_notified_at"

    fun save(context: Context, dueTimes: List<Long>) {
        prefs(context).edit().putString(KEY_DUE, dueTimes.joinToString(",")).apply()
    }

    fun load(context: Context): List<Long> =
        prefs(context).getString(KEY_DUE, "").orEmpty()
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }

    fun lastNotified(context: Context): Pair<DueSummary?, Long> {
        val p = prefs(context)
        if (!p.contains(KEY_LAST_AT)) return null to 0L
        return DueSummary(p.getInt(KEY_LAST_OVERDUE, 0), p.getInt(KEY_LAST_SOON, 0)) to p.getLong(KEY_LAST_AT, 0L)
    }

    fun recordNotified(context: Context, summary: DueSummary, at: Long) {
        prefs(context).edit()
            .putInt(KEY_LAST_OVERDUE, summary.overdue)
            .putInt(KEY_LAST_SOON, summary.dueSoon)
            .putLong(KEY_LAST_AT, at)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
