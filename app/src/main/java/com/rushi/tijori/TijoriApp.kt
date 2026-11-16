package com.rushi.tijori

import android.app.Application
import com.rushi.tijori.reminders.ReminderWorker

class TijoriApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderWorker.createChannel(this)
        ReminderWorker.schedule(this)
    }
}
