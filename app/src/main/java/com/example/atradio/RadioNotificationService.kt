package com.example.atradio

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.reflect.TypeToken
import com.google.gson.Gson


class RadioNotificationService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var currentStation: RadioStation? = null

    private var currentStartId: Int = 0
    private var isTaskRunning: Boolean = false // для того чтобы была запущена одна задача
    private var isNotificationRunning: Boolean = false // запущено ли было  createNotification

    private var isPlaying: MusicStatus = MusicStatus.STOPPED

    // для управлением громкостью при потери звукового фокуса, например, если усть звук от навигатора
    private var audioManager: AudioManager? = null
    private var originalVolume: Float = 1.0f  // Для хранения исходной громкости

    // Определяем AudioFocusRequest для API >= 26
    private var audioFocusRequest: AudioFocusRequest? = null

    // для нескольких попыток подключения при ошибке
    private val maxRetries = 20
    private var retryCount = 0
    private val retryDelayMillis = 2000L // Задержка в 3 секунды
    private val handler = Handler(Looper.getMainLooper())
    @RequiresApi(Build.VERSION_CODES.O)
    private val retryRunnable = Runnable {
        if (retryCount < maxRetries) {
            retryCount++
            Log.d("iAtRadio", "RadioPlayer -> Попытка подключения №$retryCount")
            // Попробовать подключиться и воспроизвести музыку
            stopPlayback(false)
            isPlaying = MusicStatus.LOADING
            currentStation?.let { playStation(it) }
        } else {
            Log.d("iAtRadio", "RadioPlayer -> Не удалось подключиться после $maxRetries попыток")
            // Прекратить попытки после достижения максимума
            stopPlayback(true)
            resetRetries() // Сбросить счетчик при остановке музыки вручную
        }
    }

    private val bluetoothReceiver =  object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d("iAtRadio", "MainActivity -> BluetoothReceiver -> begin")
            if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return
                    }
                    Log.d("iAtRadio", "MainActivity -> BluetoothReceiver -> Bluetooth device disconnected: ${device.name}")
                    // Остановите музыку здесь
//                    statusPlay = false
//                    updateUIForStopped()
                    stopPlayback(false)
                    sendInfoBroadcast(MusicStatus.STOPPED)
                }
            }
            Log.d("iAtRadio", "MainActivity -> BluetoothReceiver -> end")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        requestAudioFocus()

        // Регистрируем BroadcastReceiver для обработки blutooth соединения
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        }
        registerReceiver(bluetoothReceiver, filter)

    }



    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("iAtRadio", "RadioService -> Service started with startId: $startId and action: ${intent?.action}")

        val appSettings = loadAppSettings()

        // Если уже выполняется задача, остановите текущую перед выполнением новой
        // Сохраняем текущий startId
        currentStartId = startId
        when (intent?.action) {
            ACTION_CURRENT_STATION -> {
                val station = intent.getParcelableExtra<RadioStation>(EXTRA_STATION)
                station?.let {
                    currentStation = it
                    Log.d("iAtRadio", "RadioService -> onStartCommand -> ACTION_CURRENT_STATION -> станция: $it")
                }
            }
            ACTION_INFO -> {
                val station = intent.getParcelableExtra<RadioStation>(EXTRA_STATION)
                station?.let {
                    Log.d("iAtRadio", "RadioService -> onStartCommand -> ACTION_CURRENT_STATION -> станция: $it")
                    currentStation = it
                    sendInfoBroadcast(isPlaying)
                }

            }
            ACTION_PLAY -> {  // данное действие получается от вызвающего активити
                val station = intent.getParcelableExtra<RadioStation>(EXTRA_STATION)
                station?.let {
                    currentStation = it
                    isPlaying = MusicStatus.LOADING
                    sendInfoBroadcast(isPlaying)
                    updateNotification()
                    Log.d("iAtRadio", "RadioService -> onStartCommand -> ACTION_PLAY -> станция: $it  -> $isPlaying")
                    isTaskRunning = true  // Указываем, что задача запущена
                    playStation(it)
                }

            }

            ACTION_PLAY_PANEL -> {  // данное действие получается от панели управления плеером
                val station = intent.getParcelableExtra<RadioStation>(EXTRA_STATION)
                station?.let {
                    currentStation = it
                    isPlaying = MusicStatus.LOADING
                    sendInfoBroadcast(isPlaying)
                    updateNotification()
                    Log.d("iAtRadio", "RadioService -> onStartCommand -> ACTION_PLAY -> станция: $it  -> $isPlaying")
                    isTaskRunning = true  // Указываем, что задача запущена
                    playStation(it)
                }
            }

            ACTION_PAUSE -> {  // данное действие получается от вызвающего активити
                pausePlayback(false)
                resetRetries() // Сбросить счетчик при остановке музыки вручную
            }

            ACTION_PAUSE_PANEL -> {  // данное действие получается от панели управления плеером
                pausePlayback(true)
                resetRetries() // Сбросить счетчик при остановке музыки вручную
            }

            ACTION_STOP -> {
                stopPlayback(false)
                resetRetries() // Сбросить счетчик при остановке музыки вручную
            }

            ACTION_STOP_PANEL -> {
                stopPlayback(true)
                resetRetries() // Сбросить счетчик при остановке музыки вручную

            }

            ACTION_CLOSE -> {
                stopPlayback(false)
                stopSelf(startId)  // Останавливаем сервис
                resetRetries() // Сбросить счетчик при остановке музыки вручную
                sendInfoBroadcast(MusicStatus.STOPPED)
            }

            ACTION_PREVIOUS -> {
                Log.d("iAtRadio", "RadioService -> onStartCommand -> ACTION_PREVIOUS -> станция: ")
                pausePlayback(false)
                appSettings.lastRadioStationIndex -= 1
                if (appSettings.lastRadioStationIndex < 0)
                    appSettings.lastRadioStationIndex = appSettings.radioStations.size - 1
                appSettings.currentStation = appSettings.radioStations[appSettings.lastRadioStationIndex]
                currentStation = appSettings.currentStation
                updateNotification()
                saveAppSettings(appSettings)
                isPlaying = MusicStatus.LOADING
                sendInfoBroadcast(isPlaying)
                updateNotification()
                Log.d("iAtRadio", "RadioService -> onStartCommand -> ACTION_PREVIOUS -> станция: $currentStation")
                isTaskRunning = true  // Указываем, что задача запущена
                playStation(currentStation!!)
            }

            ACTION_NEXT -> {
                Log.d("iAtRadio", "RadioService -> onStartCommand -> ACTION_NEXT -> станция: ")
                pausePlayback(false)
                appSettings.lastRadioStationIndex += 1
                if (appSettings.radioStations.size <= appSettings.lastRadioStationIndex)
                    appSettings.lastRadioStationIndex = 0
                appSettings.currentStation = appSettings.radioStations[appSettings.lastRadioStationIndex]
                currentStation=appSettings.currentStation
                updateNotification()
                saveAppSettings(appSettings)
                isPlaying=MusicStatus.LOADING
                sendInfoBroadcast(isPlaying)
                updateNotification()
                Log.d("iAtRadio", "RadioService -> onStartCommand -> ACTION_NEXT -> станция: $currentStation")
                isTaskRunning = true  // Указываем, что задача запущена
                playStation(currentStation!!)
            }
            else -> {
                Log.w("iAtRadio", "RadioService -> Unknown action")
                isTaskRunning = false
                stopSelf(startId)  // Останавливаем сервис при неизвестном действии
            }
        }

        if (!isNotificationRunning) {
            isNotificationRunning = true
            startForeground(NOTIFICATION_ID, createNotification())
            Log.w("iAtRadio", "RadioService -> startForeground createNotification")
        }

        return START_STICKY
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun playStation(station: RadioStation) { //, flagSendInfoBroadcast: Boolean) {
        // flagSendInfoBroadcast - флаг который говорит что нужно послать инфо откуда вызывали
        Log.d("iAtRadio", "RadioService -> playStation запуск станции $station")
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
                    this@RadioNotificationService.isPlaying = MusicStatus.PLAYING
                    updateNotification()
//                    if (flagSendInfoBroadcast)
                    sendInfoBroadcast(MusicStatus.PLAYING)
                    resetRetries() // Сбросить счетчик попыток в случае успеха
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("iAtRadio", "RadioService -> Playback error: $what, extra: $extra")
                    this@RadioNotificationService.isPlaying = MusicStatus.STOPPED
                    sendErrorBroadcast(getString(R.string.error_play_code) + what)
                    scheduleRetry() // Запланировать повторную попытку в случае ошибки
                    true // Возвращаем true, чтобы указать, что ошибка обработана
                }

            }
        } catch (e: Exception) {
            Log.e("iAtRadio", "RadioService -> Error initializing MediaPlayer: ${e.message}")
            this@RadioNotificationService.isPlaying = MusicStatus.STOPPED
            sendErrorBroadcast(getString(R.string.error_init_mediaplayer) + e.message)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun stopPlayback(flagSendInfoBroadcast: Boolean) {
        Log.d("iAtRadio", "RadioService -> stopPlayback called")
        Log.d("iAtRadio", "RadioService -> stopPlayback текущая станция $currentStation")

        mediaPlayer?.apply {
            stop()
            release()
            this@RadioNotificationService.isPlaying = MusicStatus.STOPPED
            if (flagSendInfoBroadcast)
                sendInfoBroadcast(MusicStatus.STOPPED)
        }
        mediaPlayer = null
        updateNotification()
        Log.d("iAtRadio", "RadioService -> Service stopped")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pausePlayback(flagSendInfoBroadcast: Boolean) {
        Log.d("iAtRadio", "RadioService -> pausePlayback called")
        Log.d("iAtRadio", "RadioService -> pausePlayback текущая станция $currentStation")

        mediaPlayer?.apply {
            stop()
            release()
            this@RadioNotificationService.isPlaying = MusicStatus.LOADING
            if (flagSendInfoBroadcast)
                sendInfoBroadcast(MusicStatus.LOADING)
        }
        mediaPlayer = null
        updateNotification()
        Log.d("iAtRadio", "RadioService -> Service stopped")
    }

    private fun createNotification(): Notification {
        Log.d("iAtRadio", "RadioService -> createNotification start")

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

//        val notificationIntent = Intent(this, MainActivity::class.java)
//        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("currentStation", currentStation) // передаем данные
            putExtra("isPlaying", mediaPlayer?.isPlaying == true) // передаем состояние проигрывания
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)


        val toggleIntent = Intent(this, RadioNotificationService::class.java).apply {
            action = if (isPlaying == MusicStatus.PLAYING) ACTION_STOP_PANEL else ACTION_PLAY_PANEL
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



        val buttonIcon = if (isPlaying == MusicStatus.PLAYING) {
            if (isNightMode) R.drawable.stop3_24_dark else R.drawable.stop3_24
        } else {
            if (isPlaying == MusicStatus.STOPPED) {
                if (isNightMode) R.drawable.play3_24_dark else R.drawable.play3_24
            } else {
                if (isPlaying == MusicStatus.LOADING) {
                    if (isNightMode) R.drawable.connect24_dark else R.drawable.connect64
                } else {
                    if (isNightMode) R.drawable.connect24_dark else R.drawable.connect64
                }
            }
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
        Log.d("iAtRadio", "RadioService -> updateNotification start")
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
    private fun sendInfoBroadcast(isPlayed: MusicStatus) {
        val intent = Intent(ACTION_INFO).apply {
            putExtra("PLAY", isPlayed)
            putExtra("STATION", currentStation)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // методы для повтора попытки воспроизведения
    @RequiresApi(Build.VERSION_CODES.O)
    private fun scheduleRetry() {
        Log.d("iAtRadio", "RadioService -> scheduleRetry")
        handler.postDelayed(retryRunnable, retryDelayMillis)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun resetRetries() {
        Log.d("iAtRadio", "RadioService -> resetRetries")
        retryCount = 0
        handler.removeCallbacks(retryRunnable) // Удалить все запланированные попытки
    }

    // Метод для получения состояния проигрывания
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    // для управлением громкостью при потери звукового фокуса, например, если усть звук от навигатора

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener { focusChange -> handleAudioFocusChange(focusChange) }
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .build()

            val result = audioManager?.requestAudioFocus(audioFocusRequest!!)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // Фокус получен
            }
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager?.requestAudioFocus(
                { focusChange -> handleAudioFocusChange(focusChange) },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // Фокус получен
            }
        }
    }

    // Обработчик изменения аудио-фокуса
    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Временная потеря фокуса, снижаем громкость на 50%
                mediaPlayer?.let {
                    it.setVolume(originalVolume * 0.5f, originalVolume * 0.5f)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Фокус возвращен, восстанавливаем громкость
                mediaPlayer?.let {
                    it.setVolume(originalVolume, originalVolume)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Полная потеря фокуса, останавливаем воспроизведение
                stopPlayback(true)
                audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
            }
        }
    }
    // END для управлением громкостью при потери звукового фокуса, например, если усть звук от навигатора


    private fun saveAppSettings(settings: AppSettings) {
        val gson = Gson()
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val json = gson.toJson(settings)
        editor.putString("AppSettingsData", json)
        editor.apply()
    }

    private fun loadAppSettings(): AppSettings {
        val gson = Gson()
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val json = sharedPreferences.getString("AppSettingsData", null)
        return if (json != null) {
            val type = object : TypeToken<AppSettings>() {}.type
            gson.fromJson(json, type)
        } else {
            // Возвращаем настройки по умолчанию, если они отсутствуют
            initAppSettings(this)
        }
    }

    companion object {
        const val ACTION_PLAY = "com.example.atradio.ACTION_PLAY"
        const val ACTION_PLAY_PANEL = "com.example.atradio.ACTION_PLAY_PANEL" // команда с панели управления плеером
        const val ACTION_CURRENT_STATION = "com.example.atradio.ACTION_CURRENT_STATION"
        const val ACTION_STOP = "com.example.atradio.ACTION_STOP"
        const val ACTION_PAUSE = "com.example.atradio.ACTION_PAUSE"
        const val ACTION_PAUSE_PANEL = "com.example.atradio.ACTION_PAUSE_PANEL"
        const val ACTION_STOP_PANEL  = "com.example.atradio.ACTION_STOP_PANEL"
        const val ACTION_CLOSE = "com.example.atradio.ACTION_CLOSE"
        const val ACTION_PREVIOUS = "com.example.atradio.ACTION_PREVIOUS"
        const val ACTION_PREVIOUS_PANEL  = "com.example.atradio.ACTION_PREVIOUS_PANEL"
        const val ACTION_NEXT = "com.example.atradio.ACTION_NEXT"
        const val ACTION_NEXT_PANEL  = "com.example.atradio.ACTION_NEXT_PANEL"
        const val ACTION_ERROR = "com.example.atradio.ERROR" // для отправки ошибок из сервиса
        const val ACTION_INFO = "com.example.atradio.INFO" // для отправки информации из сервиса
        const val EXTRA_STATION = "com.example.atradio.EXTRA_STATION"
        const val NOTIFICATION_ID = 1
    }
}