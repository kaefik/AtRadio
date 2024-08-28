package com.example.iradio

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import android.media.MediaPlayer

class MainActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val statusRadio = findViewById<TextView>(R.id.statusRadio)
        val buttonPlay: Button = findViewById(R.id.buttonPlay)
        val buttonVolUp: Button = findViewById(R.id.buttonVolumeUp)
        val buttonVolDown: Button = findViewById(R.id.buttonVolumeDown)
        var statusPlay: Boolean = false // статус проигрывания текущей станции

        buttonVolUp.setOnClickListener{
            statusRadio.text = "VOLUME UP"
        }

        buttonVolDown.setOnClickListener{
            statusRadio.text = "VOLUME DOWN"
        }

        buttonPlay.setOnClickListener{
            if (statusPlay) {
                statusRadio.text = "STOP PLAY RADIO"
                buttonPlay.text = "Play"
                statusPlay = false

                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.stop()
                        it.reset() // Сброс MediaPlayer для повторного использования
                        it.release()
                        mediaPlayer = null // Освобождаем ссылку
                    }
                }
            }
            else {
                statusRadio.text = "PLAY RADIO"
                buttonPlay.text = "Stop"
                statusPlay = true

                // Инициализация MediaPlayer
                mediaPlayer = MediaPlayer().apply {
                    setDataSource("http://cfm.jazzandclassic.ru:14536/rcstream.mp3") // URL на поток
                    setOnPreparedListener {
                        start() // Начало воспроизведения после подготовки
                    }
                    prepareAsync() // Асинхронная подготовка MediaPlayer
                }
            }

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Освобождение ресурсов MediaPlayer при завершении активности
        mediaPlayer?.release()
        mediaPlayer = null
    }
}