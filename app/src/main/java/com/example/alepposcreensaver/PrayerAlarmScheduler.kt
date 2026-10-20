package com.example.alepposcreensaver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId

object PrayerAlarmScheduler {
    private val zone = ZoneId.of("Asia/Aleppo")

    fun schedule(context: Context) {
        val prefs = AppPrefs(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)

        for (dayOffset in 0..3) {
            val date = today.plusDays(dayOffset.toLong())
            PrayerTimes.forDate(date, zone).filter { it.isPrayer }.forEachIndexed { prayerIndex, prayer ->
                val prayerMillis = prayer.instant.toEpochMilli()
                val dateCode = date.toEpochDay().toInt()
                val prayerCode = dateCode * 20 + prayerIndex * 2

                if (prefs.remindersEnabled) {
                    val reminderMillis = prayerMillis - prefs.reminderMinutes * 60_000L
                    if (reminderMillis > now) {
                        scheduleOne(
                            context,
                            manager,
                            reminderMillis,
                            prayerCode,
                            "reminder",
                            prayer.name
                        )
                    }
                }
                if (prefs.remindersEnabled || prefs.adhanEnabled) {
                    if (prayerMillis > now) {
                        scheduleOne(
                            context,
                            manager,
                            prayerMillis,
                            prayerCode + 1,
                            "prayer",
                            prayer.name
                        )
                    }
                }
            }
        }
    }

    private fun scheduleOne(
        context: Context,
        manager: AlarmManager,
        atMillis: Long,
        requestCode: Int,
        type: String,
        prayerName: String
    ) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = "com.example.alepposcreensaver.PRAYER_ALARM"
            putExtra("type", type)
            putExtra("prayer_name", prayerName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
            } else {
                manager.setExact(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
            }
        } catch (error: SecurityException) {
            Log.w(
                "AleppoDashboard",
                "Exact prayer alarm denied; using an inexact alarm for $prayerName",
                error
            )
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
        }
    }
}
