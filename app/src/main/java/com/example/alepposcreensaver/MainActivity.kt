package com.example.alepposcreensaver

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
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
    private val zone = ZoneId.of("Asia/Damascus")
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale("ar", "SY"))
    private val dayFormat = DateTimeFormatter.ofPattern("EEE d/M", Locale("ar", "SY"))

    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var nextPrayerView: TextView
    private lateinit var prayerContent: LinearLayout
    private lateinit var weatherContent: LinearLayout
    private lateinit var weatherStatus: TextView
    private lateinit var hadithText: TextView
    private lateinit var hadithSourceText: TextView
    private lateinit var azkarSeekBar: SeekBar
    private lateinit var azkarTimeText: TextView
    private lateinit var azkarPlayPauseButton: TextView
    private lateinit var clockSection: LinearLayout
    private lateinit var dateSection: View
    private lateinit var prayerSection: View
    private lateinit var weatherSection: View
    private lateinit var wisdomSection: View
    private var weatherRequest = 0
    private var renderedPrayerDate: LocalDate? = null
    private var azkarPlayer: MediaPlayer? = null
    private var azkarSeekInProgress = false
    private var adhanPlaying = false
    private var resumeAzkarAfterAdhan = false
    private var adhanReceiverRegistered = false

    private val updateAzkarProgress = object : Runnable {
        override fun run() {
            val player = azkarPlayer ?: return
            if (player.isPlaying) {
                if (!azkarSeekInProgress) azkarSeekBar.progress = player.currentPosition
                updateAzkarTime(player.currentPosition, player.duration)
                handler.postDelayed(this, 500)
            }
        }
    }

    private val adhanStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != AdhanPlaybackService.ACTION_PLAYBACK_STATE) return
            adhanPlaying = intent.getBooleanExtra(AdhanPlaybackService.EXTRA_IS_PLAYING, false)
            if (adhanPlaying) {
                resumeAzkarAfterAdhan = resumeAzkarAfterAdhan || azkarPlayer?.isPlaying == true
                pauseAzkar()
            } else if (resumeAzkarAfterAdhan) {
                resumeAzkarAfterAdhan = false
                startAzkarPlayback()
            }
        }
    }

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
            updateDailyHadith()
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

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(AdhanPlaybackService.ACTION_PLAYBACK_STATE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(adhanStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(adhanStateReceiver, filter)
        }
        adhanReceiverRegistered = true
    }

    override fun onStop() {
        if (adhanReceiverRegistered) {
            unregisterReceiver(adhanStateReceiver)
            adhanReceiverRegistered = false
        }
        resumeAzkarAfterAdhan = false
        pauseAzkar()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshClock)
        handler.removeCallbacks(refreshWeather)
        handler.removeCallbacks(updateAzkarProgress)
        releaseAzkarPlayer()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun renderDashboard() {
        handler.removeCallbacks(refreshClock)
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(7, 16, 27))
        }
        val blurredWallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = null
        }
        prefs.wallpaper?.let { uri ->
            try {
                blurredWallpaper.setImageURI(uri)
            } catch (error: RuntimeException) {
                android.util.Log.w("AleppoDashboard", "Could not load saved wallpaper", error)
                Toast.makeText(this, "تعذر فتح صورة الخلفية. اختر صورة أخرى من الإعدادات.", Toast.LENGTH_LONG).show()
            }
        }
        blurredWallpaper.alpha = 0.4f
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            prefs.wallpaperBlur > 0
        ) {
            blurredWallpaper.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(
                    prefs.wallpaperBlur.toFloat(),
                    prefs.wallpaperBlur.toFloat(),
                    android.graphics.Shader.TileMode.CLAMP
                )
            )
        }
        root.addView(blurredWallpaper, FrameLayout.LayoutParams(-1, -1))
        val wallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = null
        }
        prefs.wallpaper?.let { wallpaper.setImageURI(it) }
        root.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x66071020, 0x330A1422, 0x7007101C)
            )
        }, FrameLayout.LayoutParams(-1, -1))

        val dashboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(30), dp(12), dp(30), dp(10))
        }
        root.addView(dashboard, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        header.addView(createAzkarPlayer(), LinearLayout.LayoutParams(0, dp(58), 1f).apply {
            setMargins(dp(10), 0, 0, 0)
        })
        header.addView(ImageButton(this).apply {
            contentDescription = "الإعدادات"
            setImageResource(android.R.drawable.ic_menu_preferences)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            background = rounded(0x9920313C.toInt(), 50)
            isFocusable = true
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { showSettings() }
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        dashboard.addView(header, LinearLayout.LayoutParams(-1, dp(58)))

        val mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        dashboard.addView(
            mainRow,
            LinearLayout.LayoutParams(-1, 0, 1f).apply {
                setMargins(0, dp(8), 0, dp(8))
            }
        )

        clockSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(22), dp(16), dp(22), dp(16))
            background = cardBackground()
        }
        clockView = text("", 82, Color.WHITE)
        clockSection.addView(clockView)
        dateView = text("", 20, 0xFFB8C4C7.toInt())
        dateSection = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(dateView)
        }
        clockSection.addView(dateSection, LinearLayout.LayoutParams(-1, dp(38)))
        val nextPrayerCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(0x1AF1D08B, 18).apply {
                setStroke(dp(1), 0x3BF1D08B)
            }
        }
        nextPrayerCard.addView(text("◷", 25, GOLD, true), LinearLayout.LayoutParams(dp(42), -1))
        nextPrayerView = text("", 17, GOLD, true).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            maxLines = 2
        }
        nextPrayerCard.addView(nextPrayerView, LinearLayout.LayoutParams(0, -2, 1f))
        clockSection.addView(nextPrayerCard, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(22), 0, 0)
        })

        prayerSection = cardSection()
        prayerSection.apply {
            val column = this as LinearLayout
            column.orientation = LinearLayout.VERTICAL
            val heading = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(text("مواقيت الصلاة", 22, MINT, true), LinearLayout.LayoutParams(0, dp(42), 1f))
                addView(text("اليوم في حلب", 14, 0xFFB8C4C7.toInt()))
            }
            column.addView(heading, LinearLayout.LayoutParams(-1, dp(42)))
            prayerContent = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            column.addView(prayerContent, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        mainRow.addView(prayerSection, LinearLayout.LayoutParams(0, -1, 1.12f).apply {
            setMargins(dp(12), 0, 0, 0)
        })
        mainRow.addView(clockSection, LinearLayout.LayoutParams(0, -1, 0.88f))

        weatherSection = cardSection()
        weatherSection.apply {
            val column = this as LinearLayout
            column.orientation = LinearLayout.HORIZONTAL
            column.gravity = Gravity.CENTER_VERTICAL
            val weatherHeading = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(text("الطقس هذا الأسبوع", 17, MINT, true))
                weatherStatus = text("°م", 14, 0xFFB8C4C7.toInt())
                addView(weatherStatus)
            }
            column.addView(weatherHeading, LinearLayout.LayoutParams(dp(115), -1))
            weatherContent = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            column.addView(weatherContent, LinearLayout.LayoutParams(0, -1, 1f))
        }
        dashboard.addView(weatherSection, LinearLayout.LayoutParams(-1, dp(126)))

        wisdomSection = cardSection()
        wisdomSection.apply {
            val column = this as LinearLayout
            column.orientation = LinearLayout.HORIZONTAL
            column.gravity = Gravity.CENTER_VERTICAL
            column.addView(text("❝", 30, GOLD, true), LinearLayout.LayoutParams(dp(55), -1))
            val hadithCopy = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                hadithSourceText = text("", 15, GOLD, true)
                addView(hadithSourceText)
            }
            hadithText = text("", 20, Color.WHITE)
            hadithText.gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            hadithCopy.addView(hadithText)
            column.addView(hadithCopy, LinearLayout.LayoutParams(0, -1, 1f))
        }
        dashboard.addView(
            wisdomSection,
            LinearLayout.LayoutParams(-1, dp(76)).apply { setMargins(0, dp(8), 0, 0) }
        )
        dashboard.addView(
                text("تصميم ملك البرمجة  و الاختراعات محمد توتونجي أبو عبدو", 14, 0xFFCFD8D8.toInt()),
                LinearLayout.LayoutParams(-1, dp(30))
        )

        setContentView(root)
        applyVisibility()
        updateDailyHadith()
        renderPrayerTimes()
        applyCardOpacity()
    }

    private fun renderPrayerTimes() {
        if (!::prayerContent.isInitialized) return
        prayerContent.removeAllViews()
        val now = Instant.now()
        val today = LocalDate.now(zone)
        renderedPrayerDate = today
        val prayers = PrayerTimes.forDate(today, zone)
        prayers.chunked(3).forEach { rowPrayers ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            rowPrayers.forEach { prayer ->
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    setPadding(dp(4), dp(7), dp(4), dp(7))
                }
                cell.addView(text(prayer.name, 17, 0xFFB8C4C7.toInt()))
                cell.addView(
                    text(clockFormat.withZone(zone).format(prayer.instant), 22, Color.WHITE, true)
                )
                if (prayer.isPrayer && prayer.instant.isAfter(now) &&
                    prayers.firstOrNull { it.isPrayer && it.instant.isAfter(now) }?.name == prayer.name
                ) {
                    cell.background = rounded(0x553CBFA2, 14)
                }
                row.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
            }
            prayerContent.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
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
            for (rowIndex in 0 until prayerContent.childCount) {
                val row = prayerContent.getChildAt(rowIndex) as? LinearLayout ?: continue
                for (cellIndex in 0 until row.childCount) {
                    row.getChildAt(cellIndex).background = null
                }
            }
            return
        }
        val remaining = java.time.Duration.between(now, next.instant).toMinutes().coerceAtLeast(0)
        nextPrayerView.text = "الصلاة القادمة: ${next.name} • بعد ${remaining / 60} س ${remaining % 60} د"
        var highlight: View? = null
        for (rowIndex in 0 until prayerContent.childCount) {
            val row = prayerContent.getChildAt(rowIndex) as? LinearLayout ?: continue
            for (cellIndex in 0 until row.childCount) {
                val cell = row.getChildAt(cellIndex)
                cell.background = null
                if (cell is LinearLayout && cell.childCount > 0 &&
                    (cell.getChildAt(0) as? TextView)?.text == next.name
                ) {
                    highlight = cell
                }
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
                        item.addView(text(dayFormat.format(day.date), 15, 0xFFE3E9F0.toInt()))
                        item.addView(text(weatherSymbol(day.weatherCode), 24, Color.WHITE))
                        item.addView(text("${day.highCelsius}° / ${day.lowCelsius}°", 15, Color.WHITE, true))
                        weatherContent.addView(item, LinearLayout.LayoutParams(0, dp(88), 1f))
                    }
                    weatherStatus.text = "العظمى / الصغرى • °م"
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
        addSettingSwitch(panel, "حديث اليوم", prefs.showWisdom) { prefs.showWisdom = it }
        addSettingSwitch(panel, "تنبيه قبل الأذان", prefs.remindersEnabled) {
            prefs.remindersEnabled = it
            PrayerAlarmScheduler.schedule(this)
        }
        addSettingSwitch(panel, "تشغيل صوت الأذان في موعده", prefs.adhanEnabled) {
            prefs.adhanEnabled = it
            PrayerAlarmScheduler.schedule(this)
        }
        val opacityLabel = text("شفافية البطاقات: ${100 - prefs.cardOpacity}%", 17, Color.WHITE)
        panel.addView(opacityLabel)
        panel.addView(SeekBar(this).apply {
            max = 75
            progress = (100 - prefs.cardOpacity) - 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val transparency = progress + 10
                    prefs.cardOpacity = 100 - transparency
                    opacityLabel.text = "شفافية البطاقات: $transparency%"
                    applyCardOpacity()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
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

    private fun createAzkarPlayer(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = rounded(0x9920313C.toInt(), 18)
        }
        card.addView(text("أذكار الصباح", 14, MINT, true), LinearLayout.LayoutParams(dp(100), -1))
        azkarPlayPauseButton = text("▶", 20, Color.WHITE, true).apply {
            contentDescription = "تشغيل الأذكار"
            isFocusable = true
            setOnClickListener { toggleAzkarPlayback() }
        }
        card.addView(azkarPlayPauseButton, LinearLayout.LayoutParams(dp(40), -1))
        card.addView(text("■", 18, Color.WHITE, true).apply {
            contentDescription = "إيقاف الأذكار"
            isFocusable = true
            setOnClickListener { stopAzkarPlayback() }
        }, LinearLayout.LayoutParams(dp(38), -1))
        azkarSeekBar = SeekBar(this).apply {
            max = 1
            progressTintList = android.content.res.ColorStateList.valueOf(MINT)
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        azkarTimeText.text =
                            "${formatAudioTime(progress)} / ${formatAudioTime(azkarPlayer?.duration ?: 0)}"
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    azkarSeekInProgress = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    azkarSeekInProgress = false
                    azkarPlayer?.seekTo(seekBar?.progress ?: 0)
                }
            })
        }
        card.addView(azkarSeekBar, LinearLayout.LayoutParams(0, -1, 1f))
        azkarTimeText = text("00:00 / 00:00", 12, 0xFFCFD8D8.toInt())
        card.addView(azkarTimeText, LinearLayout.LayoutParams(dp(90), -1))
        return card
    }

    private fun toggleAzkarPlayback() {
        if (adhanPlaying) {
            Toast.makeText(this, "الأذكار متوقفة حتى انتهاء الأذان.", Toast.LENGTH_SHORT).show()
            return
        }
        if (azkarPlayer?.isPlaying == true) {
            pauseAzkar()
        } else {
            startAzkarPlayback()
        }
    }

    private fun startAzkarPlayback() {
        if (adhanPlaying) return
        try {
            val player = azkarPlayer ?: (MediaPlayer.create(this, R.raw.azkar)
                ?: error("تعذر فتح ملف أذكار الصباح")).also { created ->
                azkarPlayer = created
                created.setOnCompletionListener {
                    handler.removeCallbacks(updateAzkarProgress)
                    azkarSeekBar.progress = 0
                    updateAzkarTime(0, it.duration)
                    updateAzkarPlayButton(false)
                }
                created.setOnErrorListener { _, what, extra ->
                    android.util.Log.e("AleppoDashboard", "Azkar playback failed: $what/$extra")
                    releaseAzkarPlayer()
                    updateAzkarPlayButton(false)
                    Toast.makeText(this, "تعذر تشغيل ملف الأذكار.", Toast.LENGTH_LONG).show()
                    true
                }
            }
            player.start()
            azkarSeekBar.max = player.duration.coerceAtLeast(1)
            updateAzkarPlayButton(true)
            handler.removeCallbacks(updateAzkarProgress)
            handler.post(updateAzkarProgress)
        } catch (error: Exception) {
            android.util.Log.e("AleppoDashboard", "Could not play azkar audio", error)
            releaseAzkarPlayer()
            updateAzkarPlayButton(false)
            Toast.makeText(this, "تعذر تشغيل ملف الأذكار.", Toast.LENGTH_LONG).show()
        }
    }

    private fun pauseAzkar() {
        val player = azkarPlayer
        if (player?.isPlaying == true) player.pause()
        handler.removeCallbacks(updateAzkarProgress)
        updateAzkarPlayButton(false)
        if (player != null) updateAzkarTime(player.currentPosition, player.duration)
    }

    private fun stopAzkarPlayback() {
        resumeAzkarAfterAdhan = false
        azkarPlayer?.let { player ->
            if (player.isPlaying) player.pause()
            player.seekTo(0)
            azkarSeekBar.progress = 0
            updateAzkarTime(0, player.duration)
        }
        handler.removeCallbacks(updateAzkarProgress)
        updateAzkarPlayButton(false)
    }

    private fun releaseAzkarPlayer() {
        azkarPlayer?.release()
        azkarPlayer = null
        if (::azkarSeekBar.isInitialized) {
            azkarSeekBar.progress = 0
            azkarSeekBar.max = 1
            azkarTimeText.text = "00:00 / 00:00"
        }
    }

    private fun updateAzkarPlayButton(isPlaying: Boolean) {
        if (!::azkarPlayPauseButton.isInitialized) return
        azkarPlayPauseButton.text = if (isPlaying) "Ⅱ" else "▶"
        azkarPlayPauseButton.contentDescription = if (isPlaying) "إيقاف مؤقت للأذكار" else "تشغيل الأذكار"
    }

    private fun updateAzkarTime(position: Int, duration: Int) {
        if (::azkarTimeText.isInitialized) {
            azkarTimeText.text = "${formatAudioTime(position)} / ${formatAudioTime(duration)}"
        }
    }

    private fun formatAudioTime(milliseconds: Int): String {
        val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
        return "%02d:%02d".format(Locale.ROOT, totalSeconds / 60, totalSeconds % 60)
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
        clockSection.visibility = View.VISIBLE
        clockView.visibility = if (prefs.showClock) View.VISIBLE else View.INVISIBLE
        dateSection.visibility = if (prefs.showDate) View.VISIBLE else View.INVISIBLE
        prayerSection.visibility = if (prefs.showPrayers) View.VISIBLE else View.INVISIBLE
        weatherSection.visibility = if (prefs.showWeather) View.VISIBLE else View.INVISIBLE
        wisdomSection.visibility = if (prefs.showWisdom) View.VISIBLE else View.INVISIBLE
    }

    private fun updateDailyHadith() {
        val hadith = DailyHadith.forDate(LocalDate.now(zone))
        hadithText.text = hadith.text
        hadithSourceText.text = "حديث اليوم · صحيح البخاري ${hadith.bukhariNumber}"
    }

    private fun applyCardOpacity() {
        if (!::prayerSection.isInitialized) return
        listOf(clockSection, prayerSection, weatherSection, wisdomSection).forEach { section ->
            section.background = cardBackground()
        }
    }

    private fun cardSection(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(14), dp(20), dp(14))
        background = cardBackground()
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun cardBackground() =
        rounded(Color.argb(prefs.cardOpacity * 255 / 100, 9, 23, 32), 30).apply {
            setStroke(dp(1), 0x21FFFFFF)
        }

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
