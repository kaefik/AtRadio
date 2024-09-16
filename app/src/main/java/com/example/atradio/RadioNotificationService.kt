package com.example.atradio

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

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
            ACTION_CURRENT_STATION -> {
                val station = intent.getParcelableExtra<RadioStation>(EXTRA_STATION)
                station?.let {
                    currentStation = it
                    Log.d("iAtRadio RadioService", "onStartCommand -> ACTION_CURRENT_STATION -> станция: $it")
                    isTaskRunning = false  // Указываем, что задача запущена
                }
            }
            ACTION_PLAY -> {
                val station = intent.getParcelableExtra<RadioStation>(EXTRA_STATION)
                station?.let {
                    currentStation = it
                    Log.d("iAtRadio RadioService", "onStartCommand -> ACTION_PLAY -> станция: $it")
                    isTaskRunning = true  // Указываем, что задача запущена
                    playStation(it)
                }
            }
            ACTION_STOP -> {
                stopPlayback()
//                stopSelf(startId)  // Останавливаем сервис
            }
            ACTION_CLOSE -> {
                stopPlayback()
                stopSelf(startId)  // Останавливаем сервис
            }
            ACTION_PREVIOUS -> {
                Log.d("iAtRadio RadioService", "onStartCommand -> ACTION_PREVIOUS -> станция: ")
//                stopPlayback()
//                stopSelf(startId)  // Останавливаем сервис
            }
            ACTION_NEXT -> {
                Log.d("iAtRadio RadioService", "onStartCommand -> ACTION_NEXT -> станция: ")
//                stopPlayback()
//                stopSelf(startId)  // Останавливаем сервис
            }
            else -> {
                Log.w("iAtRadio RadioService", "Unknown action")
                isTaskRunning = false
                stopSelf(startId)  // Останавливаем сервис при неизвестном действии
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())

        return START_STICKY
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun playStation(station: RadioStation) {
        Log.d("iAtRadio RadioService", "playStation запуск станции $station")
        try {
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
                    sendInfoBroadcast(true)
//                    createNotification()

//                    startForeground(NOTIFICATION_ID, createNotification())
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("RadioService", "Playback error: $what, extra: $extra")
                    sendErrorBroadcast("Ошибка воспроизведения: код $what")
                    true // Возвращаем true, чтобы указать, что ошибка обработана
                }

            }
        } catch (e: Exception) {
            Log.e("RadioService", "Error initializing MediaPlayer: ${e.message}")
            sendErrorBroadcast("Ошибка при инициализации MediaPlayer: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun stopPlayback() {
        Log.d("iAtRadio RadioService", "stopPlayback called")
        Log.d("iAtRadio RadioService", "stopPlayback текущая станция $currentStation")

        mediaPlayer?.apply {
            stop()
            release()
            sendInfoBroadcast(false)
        }
        mediaPlayer = null
        updateNotification()
        Log.d("iAtRadio RadioService", "Service stopped")
    }

    private fun createNotification(): Notification {
        Log.d("iAtRadio RadioService", "createNotification start")

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "radio_playback_channel"
            val channelName = "Radio Playback"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                setSound(null, null)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            NotificationCompat.Builder(this, channelId)
        } else {
            NotificationCompat.Builder(this)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val toggleIntent = Intent(this, RadioNotificationService::class.java).apply {
            action = if (mediaPlayer?.isPlaying == true) ACTION_STOP else ACTION_PLAY
            putExtra(EXTRA_STATION, currentStation)
        }
        val togglePendingIntent = PendingIntent.getService(this, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val previousIntent = Intent(this, RadioNotificationService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val previousPendingIntent = PendingIntent.getService(this, 1, previousIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, RadioNotificationService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val closeIntent = Intent(this, RadioNotificationService::class.java).apply {
            action = ACTION_CLOSE
        }
        val closePendingIntent = PendingIntent.getService(this, 3, closeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Определяем активен ли ночной режим
        val isNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        val expandedView = RemoteViews(packageName, R.layout.notification_expanded)
        // Установка текста
        expandedView.setTextViewText(
            R.id.text_station_name,
            currentStation?.name ?: (getString(R.string.text_preparing) + "...")
        )
        val textColor = if (isNightMode) {
            ContextCompat.getColor(this, R.color.textColorDark) // Цвет для темной темы
        } else {
            ContextCompat.getColor(this, R.color.textColorLight) // Цвет для светлой темы
        }
        expandedView.setTextColor(R.id.text_station_name, textColor)



        val buttonIcon = if (mediaPlayer?.isPlaying == true) {
            if (isNightMode) R.drawable.stop3_24_dark else R.drawable.stop3_24
        } else {
            if (isNightMode) R.drawable.play3_24_dark else R.drawable.play3_24
        }

        // добавить замену остальных кнопок управления


        expandedView.setImageViewResource(R.id.button_play_stop, buttonIcon)
        expandedView.setOnClickPendingIntent(R.id.button_play_stop, togglePendingIntent)
        expandedView.setOnClickPendingIntent(R.id.button_previous, previousPendingIntent)
        expandedView.setOnClickPendingIntent(R.id.button_next, nextPendingIntent)
        expandedView.setOnClickPendingIntent(R.id.button_close, closePendingIntent)

        builder.setSmallIcon(R.drawable.logo)
            .setContentIntent(pendingIntent)
            .setCustomContentView(expandedView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null)
            .setVibrate(null)
            .setDefaults(0)

        return builder.build()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateNotification() {
        Log.d("iAtRadio RadioService", "updateNotification start")
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendErrorBroadcast(message: String) {
        val intent = Intent(ACTION_ERROR).apply {
            putExtra("ERROR_MESSAGE", message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // для отправки информации из сервиса
    private fun sendInfoBroadcast(isPlayed: Boolean) {
        val intent = Intent(ACTION_INFO).apply {
            putExtra("PLAY", isPlayed)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    companion object {
        const val ACTION_PLAY = "com.example.atradio.ACTION_PLAY"
        const val ACTION_CURRENT_STATION = "com.example.atradio.ACTION_CURRENT_STATION"
        const val ACTION_STOP = "com.example.atradio.ACTION_STOP"
        const val ACTION_CLOSE = "com.example.atradio.ACTION_CLOSE"
        const val ACTION_PREVIOUS = "com.example.atradio.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.example.atradio.ACTION_NEXT"
        const val ACTION_ERROR = "com.example.atradio.ERROR" // для отправки ошибок из сервиса
        const val ACTION_INFO = "com.example.atradio.INFO" // для отправки информации из сервиса
        const val EXTRA_STATION = "com.example.atradio.EXTRA_STATION"
        const val NOTIFICATION_ID = 1
    }
}