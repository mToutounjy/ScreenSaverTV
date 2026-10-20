package com.example.alepposcreensaver

import android.content.Context
import android.net.Uri

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("aleppo_dashboard", Context.MODE_PRIVATE)

    var showClock: Boolean
        get() = prefs.getBoolean("show_clock", true)
        set(value) = prefs.edit().putBoolean("show_clock", value).apply()
    var showDate: Boolean
        get() = prefs.getBoolean("show_date", true)
        set(value) = prefs.edit().putBoolean("show_date", value).apply()
    var showPrayers: Boolean
        get() = prefs.getBoolean("show_prayers", true)
        set(value) = prefs.edit().putBoolean("show_prayers", value).apply()
    var showWeather: Boolean
        get() = prefs.getBoolean("show_weather", true)
        set(value) = prefs.edit().putBoolean("show_weather", value).apply()
    var showWisdom: Boolean
        get() = prefs.getBoolean("show_wisdom", true)
        set(value) = prefs.edit().putBoolean("show_wisdom", value).apply()
    var remindersEnabled: Boolean
        get() = prefs.getBoolean("reminders_enabled", true)
        set(value) = prefs.edit().putBoolean("reminders_enabled", value).apply()
    var adhanEnabled: Boolean
        get() = prefs.getBoolean("adhan_enabled", false)
        set(value) = prefs.edit().putBoolean("adhan_enabled", value).apply()
    var reminderMinutes: Int
        get() = prefs.getInt("reminder_minutes", 10)
        set(value) = prefs.edit().putInt("reminder_minutes", value).apply()
    var wallpaper: Uri?
        get() = prefs.getString("wallpaper_uri", null)?.let(Uri::parse)
        set(value) = prefs.edit().putString("wallpaper_uri", value?.toString()).apply()
    var wallpaperBlur: Int
        get() = prefs.getInt("wallpaper_blur", 8)
        set(value) = prefs.edit().putInt("wallpaper_blur", value).apply()
    var adhanUri: Uri?
        get() = prefs.getString("adhan_uri", null)?.let(Uri::parse)
        set(value) = prefs.edit().putString("adhan_uri", value?.toString()).apply()
}
