package com.example.iradio

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import android.media.MediaPlayer
import android.widget.ProgressBar
import android.view.View

// Определение радиостанций
data class RadioStation(val name: String, val url: String)

class MainActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var volumeControl: VolumeControl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        volumeControl = VolumeControl(this)

        // Создание списка радиостанций
        val radioStations = mutableListOf<RadioStation>()

        val statusRadio = findViewById<TextView>(R.id.statusRadio)
        val buttonPlay: Button = findViewById(R.id.buttonPlay)
        val buttonVolUp: Button = findViewById(R.id.buttonVolumeUp)
        val buttonVolDown: Button = findViewById(R.id.buttonVolumeDown)
        val buttonForward: Button = findViewById(R.id.buttonForward)
        val buttonPrev: Button = findViewById(R.id.buttonPrev)
        var statusPlay: Boolean = false // статус проигрывания текущей станции
        val progressBar: ProgressBar = findViewById(R.id.progressBar)

        var currentRadioStation: Int = loadLastRadioStation()

        // Добавление новых радиостанций
        radioStations.add(RadioStation(name = "Классик ФМ", url = "http://cfm.jazzandclassic.ru:14536/rcstream.mp3"))
        radioStations.add(RadioStation(name = "Bolgar Radiosi", url = "http://stream.tatarradio.ru:2068/;stream/1"))
        radioStations.add(RadioStation(name = "Детское радио (Дети ФМ)", url = "http://ic5.101.ru:8000/v14_1"))
        radioStations.add(RadioStation(name = "Монте Карло", url = "https://montecarlo.hostingradio.ru/montecarlo128.mp3"))
        radioStations.add(RadioStation(name = "Saf Radiosi", url = "https://c7.radioboss.fm:18335/stream"))

        buttonForward.setOnClickListener{
            currentRadioStation += 1
            if (radioStations.size<=currentRadioStation)
                currentRadioStation = 0
            stopMusic()
            saveCurrentRadioStation(currentRadioStation)
            statusRadio.text = radioStations[currentRadioStation].name
            startMusic(radioStations[currentRadioStation], progressBar)
        }

        buttonPrev.setOnClickListener{
            currentRadioStation -= 1
            if (currentRadioStation<0)
                currentRadioStation = radioStations.size-1
            stopMusic()
            saveCurrentRadioStation(currentRadioStation)
            statusRadio.text = radioStations[currentRadioStation].name
            startMusic(radioStations[currentRadioStation], progressBar)
        }


        buttonVolUp.setOnClickListener{
//            statusRadio.text = "VOLUME UP"
            volumeControl.increaseVolume()
        }

        buttonVolDown.setOnClickListener{
//            statusRadio.text = "VOLUME DOWN"
            volumeControl.decreaseVolume()
        }

        buttonPlay.setOnClickListener{
            if (statusPlay) {
                statusRadio.text = "STOP PLAY RADIO"
                buttonPlay.text = "Play"
                statusPlay = false
                stopMusic()
            }
            else {
                buttonPlay.text = "Stop"
                statusPlay = true

                statusRadio.text = radioStations[currentRadioStation].name
                startMusic(radioStations[currentRadioStation], progressBar)
            }

        }
    }

    private fun startMusic(radioStation: RadioStation, progressBar: ProgressBar) {
        mediaPlayer = MediaPlayer().apply {
            setDataSource(radioStation.url) // URL на поток

            setOnPreparedListener {
                progressBar.visibility = View.GONE
                start()
            }

            setOnBufferingUpdateListener { _, percent ->
                if (percent < 100) {
                    progressBar.visibility = View.VISIBLE
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            setOnErrorListener { _, _, _ ->
                progressBar.visibility = View.GONE
                true
            }
            prepareAsync() // Асинхронная подготовка MediaPlayer
        }
    }

    private fun stopMusic() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
                it.reset()
                it.release()
                mediaPlayer = null
            }
        }
    }

    // сохранение текущей радиостанции
    private fun saveCurrentRadioStation(index: Int) {
        val sharedPreferences = getSharedPreferences("RadioPreferences", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("LastRadioStationIndex", index)
        editor.commit() // если необходимо сразу же сохранить данные
    }

    //  получение из настроек текущую радиостанцию
    private fun loadLastRadioStation(): Int {
        val sharedPreferences = getSharedPreferences("RadioPreferences", MODE_PRIVATE)
        return sharedPreferences.getInt("LastRadioStationIndex", 0) // 0 - значение по умолчанию, если данных нет
    }


    override fun onDestroy() {
        super.onDestroy()
        // Освобождение ресурсов MediaPlayer при завершении активности
        mediaPlayer?.release()
        mediaPlayer = null
    }
}