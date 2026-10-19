package com.example.alepposcreensaver

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = TextView(this).apply {
            text = "التطبيق مثبت. لاختيار شاشة التوقف، افتح إعداداتها من الزر أدناه."
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val settingsButton = Button(this).apply {
            text = "إعداد شاشة التوقف"
            textSize = 20f
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    status.text = "افتح إعدادات التلفاز ثم النظام > شاشة التوقف، واختر مواقيت حلب."
                }
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
            setBackgroundColor(0xFF0A1423.toInt())
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(status)
            addView(settingsButton)
        })
    }
}
