package com.rushi.tijori.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.rushi.tijori.MainActivity
import com.rushi.tijori.R
import com.rushi.tijori.core.Reminders
import com.rushi.tijori.data.DueIndex
import java.util.concurrent.TimeUnit

/**
 * Runs about once a day. It cannot open the vault, so it only counts due dates
 * from [DueIndex] and never shows which accounts are affected.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val now = System.currentTimeMillis()
        val summary = Reminders.summarize(DueIndex.load(context), now)
        val (last, lastAt) = DueIndex.lastNotified(context)
        if (!Reminders.shouldNotify(summary, last, lastAt, now)) return Result.success()

        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return Result.success()

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(Reminders.message(summary))
            .setStyle(NotificationCompat.BigTextStyle().bigText(Reminders.message(summary) + " Open Tijori to see which."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            DueIndex.recordNotified(context, summary, now)
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the call.
        }
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "rotation_reminders"
        private const val WORK_NAME = "rotation_reminders"
        private const val NOTIFICATION_ID = 1001

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
