package com.example.atradio

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RadioService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel() // Create notification channel for Android O and above
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_PLAY -> {
                val radioUrl = intent.getStringExtra(EXTRA_RADIO_URL)
                startForegroundService(radioUrl)
            }
            ACTION_STOP -> stopMusic()
        }

        return START_STICKY
    }

    private fun startForegroundService(radioUrl: String?) {
        if (radioUrl != null) {
            // Initialize MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(radioUrl)
                setOnPreparedListener {
                    start()
                }
                setOnErrorListener { _, _, _ ->
                    stopMusic()
                    false
                }
                prepareAsync()
            }

            // Start Foreground Service with Notification
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, RadioService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Playing Radio")
            .setContentText("Radio is playing")
            .setSmallIcon(R.drawable.ic_radio)
            .addAction(R.drawable.stop_64, "Stop", pendingStopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Radio Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    private fun stopMusic() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
        stopForeground(STOP_FOREGROUND_REMOVE) // Removes the notification
        stopSelf()
    }

    override fun onDestroy() {
        stopMusic()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        const val ACTION_PLAY = "com.example.atradio.action.PLAY"
        const val ACTION_STOP = "com.example.atradio.action.STOP"
        const val EXTRA_RADIO_URL = "com.example.atradio.extra.RADIO_URL"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "RadioServiceChannel"
    }
}
