package com.example.alepposcreensaver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log

class AdhanPlaybackService : Service() {
    private var player: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_PLAY) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "الصلاة"
        createPlaybackChannel()
        startForeground(NOTIFICATION_ID, buildNotification(prayerName))
        stopPlayer()

        try {
            val customUri = AppPrefs(this).adhanUri
            player = if (customUri == null) {
                MediaPlayer.create(this, R.raw.default_adhan)
            } else {
                MediaPlayer().apply {
                    setDataSource(this@AdhanPlaybackService, customUri)
                    prepare()
                }
            }
            player?.setOnCompletionListener { stopSelf(startId) }
                ?: error("Could not open the selected adhan audio")
            player?.start()
        } catch (error: Exception) {
            Log.e("AleppoDashboard", "Adhan playback failed; using bundled recording", error)
            try {
                stopPlayer()
                player = MediaPlayer.create(this, R.raw.default_adhan)
                player?.setOnCompletionListener { stopSelf(startId) }
                player?.start()
            } catch (fallbackError: Exception) {
                Log.e("AleppoDashboard", "Bundled adhan playback also failed", fallbackError)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopPlayer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createPlaybackChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "تشغيل الأذان",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        setSound(null, null)
                        description = "إشعار أثناء تشغيل ملف الأذان"
                    }
                )
            }
        }
    }

    private fun buildNotification(prayerName: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("الأذان")
            .setContentText("يُشغّل أذان صلاة $prayerName")
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .build()

    private fun stopPlayer() {
        player?.run {
            if (isPlaying) stop()
            release()
        }
        player = null
    }

    companion object {
        const val ACTION_PLAY = "com.example.alepposcreensaver.PLAY_ADHAN"
        const val EXTRA_PRAYER_NAME = "prayer_name"
        private const val CHANNEL_ID = "adhan_playback"
        private const val NOTIFICATION_ID = 8701
    }
}
