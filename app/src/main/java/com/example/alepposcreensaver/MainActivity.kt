package com.example.alepposcreensaver

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val prefs by lazy { AppPrefs(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val zone = ZoneId.of("Asia/Aleppo")
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale("ar", "SY"))
    private val dayFormat = DateTimeFormatter.ofPattern("EEE d/M", Locale("ar", "SY"))

    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var nextPrayerView: TextView
    private lateinit var prayerContent: LinearLayout
    private lateinit var weatherContent: LinearLayout
    private lateinit var weatherStatus: TextView
    private lateinit var wisdomText: TextView
    private lateinit var clockSection: View
    private lateinit var dateSection: View
    private lateinit var prayerSection: View
    private lateinit var weatherSection: View
    private lateinit var wisdomSection: View
    private var weatherRequest = 0
    private var renderedPrayerDate: LocalDate? = null

    private val refreshWeather = object : Runnable {
        override fun run() {
            if (prefs.showWeather) loadWeather()
            handler.postDelayed(this, 6 * 60 * 60 * 1000L)
        }
    }

    private val refreshClock = object : Runnable {
        override fun run() {
            if (!::clockView.isInitialized) return
            val now = Instant.now()
            clockView.text = clockFormat.withZone(zone).format(now)
            dateView.text = dateFormat.withZone(zone).format(now)
            wisdomText.text = DailyWisdom.forDate(LocalDate.now(zone))
            if (renderedPrayerDate != LocalDate.now(zone)) renderPrayerTimes() else updateNextPrayer(now)
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderDashboard()
        PrayerAlarmReceiver.ensureNotificationChannels(this)
        refreshClock.run()
        PrayerAlarmScheduler.schedule(this)
        if (prefs.showWeather) loadWeather()
        handler.postDelayed(refreshWeather, 6 * 60 * 60 * 1000L)
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (::clockView.isInitialized) {
            handler.removeCallbacks(refreshClock)
            refreshClock.run()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshClock)
        handler.removeCallbacks(refreshWeather)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun renderDashboard() {
        handler.removeCallbacks(refreshClock)
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(7, 16, 27))
        }
        val wallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = null
        }
        prefs.wallpaper?.let { uri ->
            try {
                wallpaper.setImageURI(uri)
            } catch (error: RuntimeException) {
                android.util.Log.w("AleppoDashboard", "Could not load saved wallpaper", error)
                Toast.makeText(this, "تعذر فتح صورة الخلفية. اختر صورة أخرى من الإعدادات.", Toast.LENGTH_LONG).show()
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            prefs.wallpaperBlur > 0
        ) {
            wallpaper.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(
                    prefs.wallpaperBlur.toFloat(),
                    prefs.wallpaperBlur.toFloat(),
                    android.graphics.Shader.TileMode.CLAMP
                )
            )
        }
        root.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xD9071020.toInt(), 0xA60A1422.toInt(), 0xE607101C.toInt())
            )
        }, FrameLayout.LayoutParams(-1, -1))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(50), dp(20), dp(50), dp(22))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
            addView(content)
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        header.addView(text("حلب", 23, MINT, true), LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(button("الإعدادات") { showSettings() })
        content.addView(header)

        clockSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        clockView = text("", 80, Color.WHITE, true)
        clockSection.addView(clockView)
        nextPrayerView = text("", 20, GOLD, true)
        clockSection.addView(nextPrayerView)
        dateView = text("", 21, 0xFFE3E9F0.toInt())
        dateSection = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(dateView)
        }
        content.addView(clockSection)
        content.addView(dateSection, LinearLayout.LayoutParams(-1, dp(38)))

        prayerSection = cardSection()
        prayerSection.apply {
            val column = this as LinearLayout
            column.orientation = LinearLayout.VERTICAL
            column.addView(text("مواقيت الصلاة", 22, MINT, true))
            prayerContent = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            column.addView(prayerContent)
        }
        content.addView(prayerSection, sectionParams())

        weatherSection = cardSection()
        weatherSection.apply {
            val column = this as LinearLayout
            column.orientation = LinearLayout.VERTICAL
            column.addView(text("الطقس للأيام السبعة", 22, MINT, true))
            weatherStatus = text("يتم تحميل التوقعات…", 17, 0xFFE3E9F0.toInt())
            column.addView(weatherStatus)
            weatherContent = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            column.addView(weatherContent)
        }
        content.addView(weatherSection, sectionParams())

        wisdomSection = cardSection()
        wisdomSection.apply {
            val column = this as LinearLayout
            column.orientation = LinearLayout.VERTICAL
            column.addView(text("حكمة اليوم", 21, GOLD, true))
            wisdomText = text(DailyWisdom.forDate(LocalDate.now(zone)), 23, Color.WHITE)
            wisdomText.gravity = Gravity.CENTER
            column.addView(wisdomText)
        }
        content.addView(wisdomSection, sectionParams())

        setContentView(root)
        applyVisibility()
        renderPrayerTimes()
    }

    private fun renderPrayerTimes() {
        if (!::prayerContent.isInitialized) return
        prayerContent.removeAllViews()
        val now = Instant.now()
        val today = LocalDate.now(zone)
        renderedPrayerDate = today
        PrayerTimes.forDate(today, zone).forEach { prayer ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }
            row.addView(text(prayer.name, 19, 0xFFE3E9F0.toInt()), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(
                text(clockFormat.withZone(zone).format(prayer.instant), 20, Color.WHITE, true),
                LinearLayout.LayoutParams(dp(96), -2)
            )
            if (prayer.isPrayer) {
                val next = PrayerTimes.forDate(LocalDate.now(zone), zone)
                    .firstOrNull { it.isPrayer && it.instant.isAfter(now) }
                if (next?.name == prayer.name) {
                    row.background = rounded(0x553CBFA2, 12)
                }
            }
            prayerContent.addView(row)
        }
        updateNextPrayer(now)
    }

    private fun updateNextPrayer(now: Instant) {
        if (!::prayerContent.isInitialized) return
        val prayers = PrayerTimes.forDate(LocalDate.now(zone), zone).filter { it.isPrayer }
        val next = prayers.firstOrNull { it.instant.isAfter(now) }
        if (next == null) {
            val tomorrow = PrayerTimes.forDate(LocalDate.now(zone).plusDays(1), zone)
                .first { it.isPrayer }
            val minutes = java.time.Duration.between(now, tomorrow.instant).toMinutes().coerceAtLeast(0)
            nextPrayerView.text = "الصلاة القادمة: الفجر غدًا • بعد ${minutes / 60} س ${minutes % 60} د"
            for (index in 0 until prayerContent.childCount) {
                prayerContent.getChildAt(index).background = null
            }
            return
        }
        val remaining = java.time.Duration.between(now, next.instant).toMinutes().coerceAtLeast(0)
        nextPrayerView.text = "الصلاة القادمة: ${next.name} • بعد ${remaining / 60} س ${remaining % 60} د"
        var highlight: View? = null
        for (index in 0 until prayerContent.childCount) {
            val child = prayerContent.getChildAt(index)
            child.background = null
            if (child is LinearLayout && child.childCount > 0 &&
                (child.getChildAt(0) as? TextView)?.text == next.name
            ) {
                highlight = child
            }
        }
        highlight?.background = rounded(0x553CBFA2, 12)
    }

    private fun loadWeather() {
        if (!prefs.showWeather) return
        val request = ++weatherRequest
        weatherStatus.text = "يتم تحميل التوقعات…"
        executor.execute {
            try {
                val forecast = WeatherClient.fetchWeek()
                handler.post {
                    if (isFinishing || request != weatherRequest) return@post
                    weatherContent.removeAllViews()
                    forecast.forEach { day ->
                        val item = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(dp(7), dp(8), dp(7), dp(8))
                        }

                        item.addView(text(dayFormat.format(day.date), 16, 0xFFE3E9F0.toInt()))
                        item.addView(text(weatherSymbol(day.weatherCode), 25, Color.WHITE))
                        item.addView(text("${day.highCelsius}° / ${day.lowCelsius}°", 16, Color.WHITE, true))
                        weatherContent.addView(item, LinearLayout.LayoutParams(0, dp(88), 1f))
                    }
                    weatherStatus.text = "درجات الحرارة العظمى / الصغرى • °م"
                }
            } catch (error: Exception) {
                android.util.Log.w("AleppoDashboard", "Weather forecast request failed", error)
                handler.post {
                    if (isFinishing || request != weatherRequest) return@post
                    weatherStatus.text = "تعذر تحميل الطقس. تحقق من اتصال الإنترنت."
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 501)
        }
    }

    private fun showSettings() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val scroll = ScrollView(this).apply { addView(panel) }
        addSettingSwitch(panel, "الساعة", prefs.showClock) { prefs.showClock = it }
        addSettingSwitch(panel, "التاريخ", prefs.showDate) { prefs.showDate = it }
        addSettingSwitch(panel, "مواقيت الصلاة", prefs.showPrayers) { prefs.showPrayers = it }
        addSettingSwitch(panel, "الطقس", prefs.showWeather) { prefs.showWeather = it }
        addSettingSwitch(panel, "حكمة اليوم", prefs.showWisdom) { prefs.showWisdom = it }
        addSettingSwitch(panel, "تنبيه قبل الأذان", prefs.remindersEnabled) {
            prefs.remindersEnabled = it
            PrayerAlarmScheduler.schedule(this)
        }
        addSettingSwitch(panel, "تشغيل صوت الأذان في موعده", prefs.adhanEnabled) {
            prefs.adhanEnabled = it
            PrayerAlarmScheduler.schedule(this)
        }
        panel.addView(button("اختيار صورة الخلفية") { chooseFile(REQUEST_WALLPAPER, "image/*") })
        panel.addView(button("إزالة الصورة واستخدام الخلفية الافتراضية") {
            prefs.wallpaper = null
            renderDashboard()
            refreshClock.run()
            loadWeather()
        })
        panel.addView(button("تأثير الخلفية: ${prefs.wallpaperBlur}") { chooseBlur() })
        panel.addView(button("صوت الأذان: ${selectedAudioName()}") { chooseAdhan() })
        panel.addView(button("موعد التنبيه: قبل ${prefs.reminderMinutes} دقائق") { chooseReminderLead() })
        panel.addView(button("تجربة صوت الأذان") { playAdhanPreview() })
        panel.addView(text(
            "التسجيل الافتراضي: أداء Atcovi، بترخيص CC BY-SA 4.0 عبر Wikimedia Commons. لم أجد تسجيلًا موثّقًا من المدينة المنورة؛ يمكنك اختيار تسجيلك من الجهاز.",
            14,
            0xFFCBD5E1.toInt()
        ))

        AlertDialog.Builder(this)
            .setTitle("إعدادات مواقيت حلب")
            .setView(scroll)
            .setPositiveButton("تم") { _, _ ->
                applyVisibility()
                renderPrayerTimes()
                PrayerAlarmScheduler.schedule(this)
                if (prefs.showWeather) loadWeather()
            }
            .setNegativeButton("إغلاق") { _, _ ->
                applyVisibility()
                PrayerAlarmScheduler.schedule(this)
            }
            .show()
    }

    private fun addSettingSwitch(
        panel: LinearLayout,
        label: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ) {
        panel.addView(CheckBox(this).apply {
            text = label
            textSize = 18f
            setTextColor(Color.WHITE)
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        })
    }

    private fun chooseFile(requestCode: Int, mimeType: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, requestCode)
        } catch (error: android.content.ActivityNotFoundException) {
            android.util.Log.e("AleppoDashboard", "No file picker can open $mimeType", error)
            Toast.makeText(this, "لا يوجد مدير ملفات يدعم اختيار هذا النوع من الملفات.", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("The platform document picker returns persisted URI grants through this callback.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (error: SecurityException) {
            android.util.Log.w("AleppoDashboard", "The selected file provider did not grant persistent access", error)
            if (uri.scheme == "content") {
                Toast.makeText(this, "مدير الملفات لم يسمح بحفظ صلاحية الملف. جرّب اختيار الملف من تطبيق الملفات في النظام.", Toast.LENGTH_LONG).show()
                return
            }
        }
        when (requestCode) {
            REQUEST_WALLPAPER -> {
                prefs.wallpaper = uri
                renderDashboard()
                refreshClock.run()
                loadWeather()
                Toast.makeText(this, "تم تغيير صورة الخلفية", Toast.LENGTH_SHORT).show()
            }
            REQUEST_ADHAN -> {
                prefs.adhanUri = uri
                Toast.makeText(this, "تم اختيار ملف الأذان: ${displayName(uri)}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun chooseBlur() {
        val values = intArrayOf(0, 8, 16, 24)
        val labels = arrayOf("بدون تمويه", "خفيف", "متوسط", "قوي")
        AlertDialog.Builder(this)
            .setTitle("تأثير الخلفية")
            .setSingleChoiceItems(labels, values.indexOf(prefs.wallpaperBlur).coerceAtLeast(0)) { dialog, which ->
                prefs.wallpaperBlur = values[which]
                renderDashboard()
                refreshClock.run()
                loadWeather()
                dialog.dismiss()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun chooseAdhan() {
        AlertDialog.Builder(this)
            .setTitle("مصدر صوت الأذان")
            .setItems(arrayOf("التسجيل المرفق بالتطبيق", "اختيار ملف صوتي من الجهاز")) { _, which ->
                if (which == 0) {
                    prefs.adhanUri = null
                    Toast.makeText(this, "تم اختيار التسجيل الافتراضي", Toast.LENGTH_SHORT).show()
                } else {
                    chooseFile(REQUEST_ADHAN, "audio/*")
                }
            }
            .show()
    }

    private fun chooseReminderLead() {
        val values = intArrayOf(5, 10, 15, 20, 30)
        val labels = values.map { "قبل $it دقائق" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("وقت التنبيه")
            .setSingleChoiceItems(labels, values.indexOf(prefs.reminderMinutes).coerceAtLeast(0)) { dialog, which ->
                prefs.reminderMinutes = values[which]
                dialog.dismiss()
                PrayerAlarmScheduler.schedule(this)
                Toast.makeText(this, "تم تعديل موعد التنبيه", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun playAdhanPreview() {
        try {
            val uri = prefs.adhanUri
            val player = if (uri == null) MediaPlayer.create(this, R.raw.default_adhan) else MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                prepare()
            }
            player?.setOnCompletionListener { it.release() }
            player?.start() ?: error("Could not open selected audio")
        } catch (error: Exception) {
            android.util.Log.e("AleppoDashboard", "Could not preview adhan", error)
            Toast.makeText(this, "تعذر تشغيل الملف الصوتي المختار.", Toast.LENGTH_LONG).show()
        }
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment ?: "ملف صوتي"
    }

    private fun selectedAudioName(): String =
        prefs.adhanUri?.let(::displayName) ?: "التسجيل الافتراضي"

    private fun applyVisibility() {
        clockSection.visibility = if (prefs.showClock) View.VISIBLE else View.GONE
        dateSection.visibility = if (prefs.showDate) View.VISIBLE else View.GONE
        prayerSection.visibility = if (prefs.showPrayers) View.VISIBLE else View.GONE
        weatherSection.visibility = if (prefs.showWeather) View.VISIBLE else View.GONE
        wisdomSection.visibility = if (prefs.showWisdom) View.VISIBLE else View.GONE
    }

    private fun cardSection(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(14), dp(20), dp(14))
        background = rounded(0xBB102033.toInt(), 22).apply {
            setStroke(dp(1), 0x667DD3C0)
        }
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun sectionParams() =
        LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(10), 0, dp(5)) }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 18f
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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

    companion object {
        private const val REQUEST_WALLPAPER = 101
        private const val REQUEST_ADHAN = 102
        private val MINT = 0xFF7DD3C0.toInt()
        private val GOLD = 0xFFFFD180.toInt()
    }
}
