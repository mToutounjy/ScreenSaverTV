package com.example.alepposcreensaver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = AppPrefs(context)
        val type = intent.getStringExtra("type") ?: return
        val prayerName = intent.getStringExtra("prayer_name") ?: "الصلاة"
        if ((type == "reminder" && !prefs.remindersEnabled) ||
            (type == "prayer" && !prefs.remindersEnabled && !prefs.adhanEnabled)
        ) return

        val isReminder = type == "reminder"
        val channelId = when {
            isReminder -> CHANNEL_REMINDERS
            prefs.adhanEnabled -> CHANNEL_ADHAN
            else -> CHANNEL_ALERTS
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (isReminder) "اقترب موعد الصلاة" else "حان وقت الصلاة")
            .setContentText(if (isReminder) "بقي ${prefs.reminderMinutes} دقائق على صلاة $prayerName" else "حان الآن موعد صلاة $prayerName")
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            manager.notify(type.hashCode() + prayerName.hashCode(), notification)
        } catch (error: SecurityException) {
            Log.w("AleppoDashboard", "Notification permission is unavailable", error)
        }

        if (!isReminder && prefs.adhanEnabled) {
            try {
                val playIntent = Intent(context, AdhanPlaybackService::class.java).apply {
                    action = AdhanPlaybackService.ACTION_PLAY
                    putExtra(AdhanPlaybackService.EXTRA_PRAYER_NAME, prayerName)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(playIntent)
                } else {
                    context.startService(playIntent)
                }
            } catch (error: Exception) {
                Log.e("AleppoDashboard", "Unable to start adhan playback", error)
            }
        }

        PrayerAlarmScheduler.schedule(context)
    }

    companion object {
        private const val CHANNEL_REMINDERS = "prayer_reminders"
        private const val CHANNEL_ALERTS = "prayer_alerts"
        private const val CHANNEL_ADHAN = "prayer_adhan"

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_REMINDERS) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_REMINDERS,
                        "التنبيه قبل الصلاة",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "تذكير قبل دخول وقت الصلاة"
                        enableVibration(true)
                    }
                )
            }
            if (manager.getNotificationChannel(CHANNEL_ALERTS) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ALERTS,
                        "تنبيهات الصلاة",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "تنبيه عند دخول وقت الصلاة"
                        enableVibration(true)
                    }
                )
            }
            if (manager.getNotificationChannel(CHANNEL_ADHAN) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ADHAN,
                        "موعد الصلاة مع الأذان",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "إشعار صامت أثناء تشغيل صوت الأذان"
                        setSound(null, null)
                        enableVibration(true)
                    }
                )
            }
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            PrayerAlarmScheduler.schedule(context)
        }
    }
}
