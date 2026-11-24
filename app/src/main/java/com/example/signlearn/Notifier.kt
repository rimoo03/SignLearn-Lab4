package com.example.signlearn

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

/** Notifications: daily streak reminder + "badge unlocked". */
object Notifier {

    private const val CH_REMINDER = "reminders"
    private const val CH_ACHIEVE = "achievements"
    private const val ID_REMINDER = 1001
    private const val REQ_ALARM = 77

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_REMINDER, "Daily reminders", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Reminds you to keep your streak" }
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_ACHIEVE, "Achievements", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Badges you unlock" }
            )
        }
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun requestPermissionIfNeeded(activity: Activity) {
        if (!hasPermission(activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    /** Tapping a notification opens the app's dashboard (PendingIntent = an intent fired later). */
    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showReminder(context: Context) {
        if (!hasPermission(context)) return
        ProgressStore.init(context)
        val s = ProgressStore.streak
        val title = if (s > 0) "🔥 Keep your $s-day streak alive!" else "👋 Time to practice your signs!"
        val text = if (s > 0) "A quick 2-minute lesson keeps the flame burning."
        else "Learn a new sign today — it only takes a couple of minutes."
        val n = NotificationCompat.Builder(context, CH_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_hand)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_REMINDER, n)
        } catch (e: SecurityException) { /* permission revoked */ }
    }

    fun showBadge(context: Context, badge: Badge) {
        if (!hasPermission(context)) return
        val n = NotificationCompat.Builder(context, CH_ACHIEVE)
            .setSmallIcon(R.drawable.ic_stat_hand)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle("${badge.emoji} Badge unlocked: ${badge.title}")
            .setContentText(badge.desc)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(badge.id.hashCode(), n)
        } catch (e: SecurityException) { }
    }

    /** Schedules (or cancels) the daily reminder with AlarmManager. */
    fun scheduleDaily(context: Context) {
        ProgressStore.init(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, REQ_ALARM, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        if (!ProgressStore.reminderOn) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, ProgressStore.reminderHour)
            set(Calendar.MINUTE, ProgressStore.reminderMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }
}
