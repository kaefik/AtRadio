package com.example.atradio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

class RadioService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): RadioService = this@RadioService
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Обработка запуска службы
        startForeground(1, createNotification())
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "radio_channel")
            .setContentTitle("Online Radio")
            .setContentText("Слушаете радио")
            .setSmallIcon(R.drawable.logo)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun startMusic(url: String): Boolean {
        stopMusic()
//        mediaPlayer = MediaPlayer().apply {
//            setDataSource(url)
//            prepareAsync()
//            setOnPreparedListener { start() }
//            setOnErrorListener { _, what, extra ->
//                handleError(what, extra)
//                stopMusic()
//                false // Возвращаем false, чтобы не воспринимать ошибку как успешное воспроизведение
//            }
//        }
                return try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)

                setOnPreparedListener {
//                    progressBar.visibility = View.GONE
                    start()
                }

                setOnErrorListener { _, what, extra ->
                    stopMusic()
//                    progressBar.visibility = View.GONE
                    handleError(what, extra)
                    stopMusic()
                    false
                }

//                progressBar.visibility = View.VISIBLE
                prepareAsync()
            }
            true
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_message) + e.message, Toast.LENGTH_LONG).show()
//            onErrorPlay()
            false // Возвращаем false, если произошло исключение
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

    override fun onDestroy() {
        super.onDestroy()
        stopMusic()
    }
}

