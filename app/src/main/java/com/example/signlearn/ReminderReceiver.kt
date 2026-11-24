package com.example.signlearn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Woken up once a day by AlarmManager; posts the reminder if you haven't practiced today. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ProgressStore.init(context)
        if (!ProgressStore.practicedToday) {
            Notifier.showReminder(context)
        }
    }
}
