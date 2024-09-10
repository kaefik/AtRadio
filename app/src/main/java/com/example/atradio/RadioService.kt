package com.example.atradio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.IOException

class RadioService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val binder = LocalBinder()

    // Binder class для возможного будущего bind-соединения (если потребуется)
    inner class LocalBinder : Binder() {
        fun getService(): RadioService = this@RadioService
    }

//    override fun onBind(intent: Intent?): IBinder? = binder
    override fun onBind(intent: Intent?): IBinder? {
        // Не используем привязку в этом варианте, возвращаем null
        return null
    }

//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        // Обработка запуска службы
//        startForeground(1, createNotification())
//        return START_NOT_STICKY
//    }
//
//    private fun createNotification(): Notification {
//        val notificationIntent = Intent(this, MainActivity::class.java)
//        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
//
//        return NotificationCompat.Builder(this, "radio_channel")
//            .setContentTitle("Online Radio")
//            .setContentText("Слушаете радио")
//            .setSmallIcon(R.drawable.logo)
//            .setContentIntent(pendingIntent)
//            .setPriority(NotificationCompat.PRIORITY_LOW)
//            .build()
//    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val radioStationUrl = intent?.getStringExtra("RADIO_STATION_URL")

        if (radioStationUrl != null) {
            startMusic(radioStationUrl)
        }

        // Если сервис завершится самостоятельно, он будет перезапущен (при необходимости)
        return START_STICKY
    }



//    fun startMusic(url: String): Boolean {
//        stopMusic()
//                return try {
//            mediaPlayer = MediaPlayer().apply {
//                setDataSource(url)
//                setOnPreparedListener {
//                start()
//                }
//
//                setOnErrorListener { _, what, extra ->
//                    stopMusic()
//                    handleError(what, extra)
//                    stopMusic()
//                    false
//                }
//                prepareAsync()
//            }
//            true
//        } catch (e: Exception) {
//            Toast.makeText(this, getString(R.string.error_message) + e.message, Toast.LENGTH_LONG).show()
//            false // Возвращаем false, если произошло исключение
//        }
//
//    }
    fun startMusic(radioStationUrl: String): Boolean {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(radioStationUrl)
                prepareAsync()
                setOnPreparedListener {
                    start()
                }
            }
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            // Отправляем информацию об ошибке в активити через BroadcastReceiver
            sendBroadcast(Intent("com.example.atradio.ERROR").apply {
                putExtra("ERROR_MESSAGE", e.message)
            })
            return false
        }
    }


    fun stopMusic() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
    }

    private fun handleError(what: Int, extra: Int) {
        val errorMessage = when (what) {
            MediaPlayer.MEDIA_ERROR_IO -> "Input/Output error"
            MediaPlayer.MEDIA_ERROR_MALFORMED -> "Malformed media"
            MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "Unsupported media"
            MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "Timed out"
            else -> "Unknown error"
        }

        // Логирование ошибки (можно использовать Logcat для этого)
        Log.e("RadioService", "MediaPlayer error: $errorMessage, extra: $extra")

        // Отправка уведомления пользователю (если это применимо)
        // sendNotification("Error", errorMessage)
    }

//    override fun onDestroy() {
//        super.onDestroy()
//        stopMusic()
//    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}

