package com.example.atradio

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import android.util.Log

class RadioNotificationService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var currentStation: RadioStation? = null

    private var currentStartId: Int = 0
    private var isTaskRunning: Boolean = false // для того чтобы была запущена одна задача



    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("RadioService", "Service started with startId: $startId and action: ${intent?.action}")

        // Если уже выполняется задача, остановите текущую перед выполнением новой
        if (isTaskRunning) {
            Log.d("iAtRadio RadioService", "Task is already running, stopping current task...")
            stopPlayback() // Останавливаем текущую задачу перед началом новой
        }

        // Сохраняем текущий startId
        currentStartId = startId



        when (intent?.action) {
            ACTION_PLAY -> {
                val station = intent.getParcelableExtra<RadioStation>(EXTRA_STATION)
                station?.let {
                    currentStation = it
                    isTaskRunning = true  // Указываем, что задача запущена
                    playStation(it)
                }
            }
            ACTION_STOP -> {
                stopPlayback()
//                stopSelf(startId)  // Останавливаем сервис
            }
            else -> {
                Log.w("iAtRadio RadioService", "Unknown action")
                stopSelf(startId)  // Останавливаем сервис при неизвестном действии
            }
        }

        return START_NOT_STICKY
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun playStation(station: RadioStation) {
        Log.d("iAtRadio RadioService", "playStation запуск станции $station")
//        stopPlayback()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(station.url)
            prepareAsync()
            setOnPreparedListener {
                start()
                updateNotification()
            }
        }
    }

    private fun stopPlayback() {
        Log.d("iAtRadio RadioService", "stopPlayback called")
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
        currentStation = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf(currentStartId) // Используем сохраненный startId
//        currentStartId=-1
        Log.d("iAtRadio RadioService", "Service stopped")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotification(): Notification {
        Log.d("iAtRadio RadioService", "createNotification start")
        val channelId = "radio_playback_channel"
        val channelName = "Radio Playback"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, channelName, importance)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, RadioNotificationService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Онлайн Радио")
            .setContentText(currentStation?.name ?: "Подготовка...")
            .setSmallIcon(R.drawable.logo)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.stop_64, "Остановить", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateNotification() {
        Log.d("iAtRadio RadioService", "updateNotification start")
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_PLAY = "com.example.atradio.ACTION_PLAY"
        const val ACTION_STOP = "com.example.atradio.ACTION_STOP"
        const val EXTRA_STATION = "com.example.atradio.EXTRA_STATION"
        const val NOTIFICATION_ID = 1
    }
}