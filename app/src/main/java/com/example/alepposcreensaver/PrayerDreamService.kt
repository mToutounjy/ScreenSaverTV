package com.example.alepposcreensaver

import android.service.dreams.DreamService
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

class PrayerDreamService : DreamService() {
    private var executor = Executors.newSingleThreadExecutor()
    private val zone = ZoneId.of("Asia/Aleppo")
    private val handler = android.os.Handler()
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var nextPrayerView: TextView
    private lateinit var prayerRow: LinearLayout
    private lateinit var weatherRow: LinearLayout
    private lateinit var weatherStatus: TextView

    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale("ar", "SY"))
    private val dayFormat = DateTimeFormatter.ofPattern("EEE", Locale("ar", "SY"))

    private val refreshClock = object : Runnable {
        override fun run() {
            if (!::clockView.isInitialized) return
            val now = Instant.now()
            clockView.text = clockFormat.withZone(zone).format(now)
            dateView.text = dateFormat.withZone(zone).format(now)
            showPrayerTimes(now)
            handler.postDelayed(this, 30_000)
        }
    }

    private val refreshWeatherTask = object : Runnable {
        override fun run() {
            refreshWeather()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (executor.isShutdown) executor = Executors.newSingleThreadExecutor()
        isInteractive = false
        isFullscreen = true
        setContentView(createScreen())
        refreshClock.run()
        handler.removeCallbacks(refreshWeatherTask)
        handler.post(refreshWeatherTask)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.removeCallbacks(refreshClock)
        refreshClock.run()
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(refreshClock)
        handler.removeCallbacks(refreshWeatherTask)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(refreshClock)
        executor.shutdownNow()
        super.onDetachedFromWindow()
    }

    private fun createScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(64), dp(36), dp(64), dp(36))
            setBackgroundColor(Color.rgb(10, 20, 35))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        root.addView(label("حلب", 24, Color.rgb(125, 211, 192), true))
        clockView = label("", 88, Color.WHITE, true)
        root.addView(clockView)
        dateView = label("", 25, Color.LTGRAY)
        root.addView(dateView)
        nextPrayerView = label("", 27, Color.rgb(255, 211, 128), true).apply {
            setPadding(0, dp(20), 0, dp(16))
        }
        root.addView(nextPrayerView)

        prayerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(prayerRow, LinearLayout.LayoutParams(-1, dp(110)))

        weatherStatus = label("جاري تحميل توقعات الطقس…", 20, Color.LTGRAY).apply {
            setPadding(0, dp(22), 0, dp(10))
        }
        root.addView(weatherStatus)
        weatherRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(weatherRow, LinearLayout.LayoutParams(-1, dp(120)))
        return root
    }

    private fun showPrayerTimes(now: Instant) {
        val today = LocalDate.now(zone)
        val prayers = PrayerTimes.forDate(today, zone)
        val next = prayers.firstOrNull { it.isPrayer && it.instant.isAfter(now) }
            ?: PrayerTimes.forDate(today.plusDays(1), zone).first { it.isPrayer }
                .copy(name = "الفجر غدًا")
        val remainingMinutes = java.time.Duration.between(now, next.instant).toMinutes().coerceAtLeast(0)
        nextPrayerView.text = "الصلاة القادمة: ${next.name}  •  بعد ${remainingMinutes / 60} س ${remainingMinutes % 60} د"

        prayerRow.removeAllViews()
        prayers.forEach { prayer ->
            val time = clockFormat.withZone(zone).format(prayer.instant)
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            column.addView(label(prayer.name, 20, Color.LTGRAY))
            column.addView(label(time, 23, if (prayer.isPrayer) Color.WHITE else Color.GRAY, true))
            prayerRow.addView(column, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun refreshWeather() {
        weatherStatus.text = "جاري تحميل توقعات الطقس للأيام السبعة…"
        executor.execute {
            try {
                val forecast = WeatherClient.fetchWeek()
                handler.post {
                    weatherRow.removeAllViews()
                    forecast.forEach { day ->
                        val column = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(dp(5), dp(4), dp(5), dp(4))
                        }
                        column.addView(label(dayFormat.format(day.date), 17, Color.LTGRAY))
                        column.addView(label(weatherSymbol(day.weatherCode), 27, Color.WHITE))
                        column.addView(label("${day.highCelsius}° / ${day.lowCelsius}°", 17, Color.WHITE))
                        weatherRow.addView(column, LinearLayout.LayoutParams(0, -1, 1f))
                    }
                    weatherStatus.text = "الطقس في حلب • تحديث تلقائي كل 6 ساعات"
                    handler.removeCallbacks(refreshWeatherTask)
                    handler.postDelayed(refreshWeatherTask, 6 * 60 * 60 * 1000L)
                }
            } catch (error: Exception) {
                android.util.Log.w("AleppoScreen", "Could not load forecast", error)
                handler.post {
                    weatherStatus.text = "تعذر تحميل الطقس — تحقق من اتصال الإنترنت"
                    handler.removeCallbacks(refreshWeatherTask)
                    handler.postDelayed(refreshWeatherTask, 6 * 60 * 60 * 1000L)
                }
            }
        }
    }

    private fun weatherSymbol(code: Int): String = when (code) {
        0 -> "☀"
        1, 2 -> "⛅"
        3 -> "☁"
        45, 48 -> "〰"
        in 51..67, in 80..82 -> "☂"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⚡"
        else -> "·"
    }

    private fun label(
        text: String,
        sizeSp: Int,
        color: Int,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = sizeSp.toFloat()
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        includeFontPadding = true
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
