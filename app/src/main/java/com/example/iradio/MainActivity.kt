package com.example.iradio

import android.annotation.SuppressLint
import android.app.Activity
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import android.os.Parcel
import android.os.Parcelable

// Определение радиостанций
data class RadioStation(val name: String, val url: String) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(url)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<RadioStation> {
        override fun createFromParcel(parcel: Parcel): RadioStation {
            return RadioStation(parcel)
        }

        override fun newArray(size: Int): Array<RadioStation?> {
            return arrayOfNulls(size)
        }
    }
}

class MainActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var volumeControl: VolumeControl
    private var radioStations: MutableList<RadioStation> = mutableListOf()

    @SuppressLint("SetTextI18n")
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

        val statusRadio = findViewById<TextView>(R.id.statusRadio)
        val buttonPlay: Button = findViewById(R.id.buttonPlay)
        val buttonVolUp: Button = findViewById(R.id.buttonVolumeUp)
        val buttonVolDown: Button = findViewById(R.id.buttonVolumeDown)
        val buttonForward: Button = findViewById(R.id.buttonForward)
        val buttonPrev: Button = findViewById(R.id.buttonPrev)
        val buttonListRadioStations: Button = findViewById(R.id.buttonListRadioStations)

        var statusPlay: Boolean = false // статус проигрывания текущей станции
        val progressBar: ProgressBar = findViewById(R.id.progressBar)

        var currentRadioStation: Int = loadLastRadioStation()

        radioStations = loadRadioStations() // Загружаем список радиостанций

        if (radioStations.size<=currentRadioStation){
            currentRadioStation = 0
        }

        val listRadioStationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                radioStations = loadRadioStations() // Перезагружаем список радиостанций

                // Обновляем интерфейс, если радиостанций не осталось или выбранная станция была удалена
                if (radioStations.isEmpty()) {
                    stopMusic()
                    statusRadio.text = "Нет доступных радиостанций"
                    buttonPlay.isEnabled = false
                    buttonForward.isEnabled = false
                    buttonPrev.isEnabled = false
                } else {
                    if (currentRadioStation >= radioStations.size) {
                        currentRadioStation = 0
                    }
                    statusRadio.text = radioStations[currentRadioStation].name
                    buttonPlay.isEnabled = true
                    buttonForward.isEnabled = true
                    buttonPrev.isEnabled = true

                    // Если радиостанция играет, запускаем воспроизведение текущей станции
//                    if (statusPlay) {
//                        stopMusic()
//                        startMusic(radioStations[currentRadioStation], progressBar)
//                    }
                }
            }
        }



        buttonListRadioStations.setOnClickListener {
            val intent = Intent(this, RadioStationListActivity::class.java)
            intent.putParcelableArrayListExtra("radioStations", ArrayList(radioStations))
            listRadioStationLauncher.launch(intent)
        }

        // Если список пустой, добавляем радиостанции по умолчанию
        if (radioStations.isEmpty()) {
            radioStations.addAll(listOf(
                RadioStation(name = "Классик ФМ", url = "http://cfm.jazzandclassic.ru:14536/rcstream.mp3"),
                RadioStation(name = "Bolgar Radiosi", url = "http://stream.tatarradio.ru:2068/;stream/1"),
                RadioStation(name = "Детское радио (Дети ФМ)", url = "http://ic5.101.ru:8000/v14_1"),
                RadioStation(name = "Монте Карло", url = "https://montecarlo.hostingradio.ru/montecarlo128.mp3"),
                RadioStation(name = "Saf Radiosi", url = "https://c7.radioboss.fm:18335/stream")
            ))
            // Сохраняем новый список
            saveRadioStations(radioStations)
        }

        statusRadio.text = radioStations[currentRadioStation].name

        buttonForward.setOnClickListener{
            currentRadioStation += 1
            if (radioStations.size<=currentRadioStation)
                currentRadioStation = 0
            saveCurrentRadioStation(currentRadioStation)
            statusRadio.text = radioStations[currentRadioStation].name
            if (statusPlay) {
                stopMusic()
                startMusic(radioStations[currentRadioStation], progressBar)
            }
            else{
                stopMusic()
            }
        }

        buttonPrev.setOnClickListener{
            currentRadioStation -= 1
            if (currentRadioStation<0)
                currentRadioStation = radioStations.size-1

            saveCurrentRadioStation(currentRadioStation)
            statusRadio.text = radioStations[currentRadioStation].name

            if (statusPlay) {
                stopMusic()
                startMusic(radioStations[currentRadioStation], progressBar)
            }
            else{
                stopMusic()
            }
        }


        buttonVolUp.setOnClickListener{
            volumeControl.increaseVolume()
        }

        buttonVolDown.setOnClickListener{
            volumeControl.decreaseVolume()
        }

        buttonPlay.setOnClickListener{
            if (statusPlay) {
                statusRadio.text = radioStations[currentRadioStation].name + " выкл"
                buttonPlay.text = "Play"
                statusPlay = false
                stopMusic()
            }
            else {
                buttonPlay.text = "Stop"
                statusPlay = true

                stopMusic()
                statusRadio.text = radioStations[currentRadioStation].name + " в эфире"
                startMusic(radioStations[currentRadioStation], progressBar)
            }

        }

        buttonListRadioStations.setOnClickListener{
            val intent = Intent(this, RadioStationListActivity::class.java)
            intent.putExtra("radioStations", ArrayList(radioStations))
            startActivity(intent)
        }
    }

    private fun startMusic(radioStation: RadioStation, progressBar: ProgressBar) {

        try {
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

                // Логика обработки ошибок во время воспроизведения
                setOnErrorListener { _, _, _ ->
                    progressBar.visibility = View.GONE
                    true
                }
                prepareAsync() // Асинхронная подготовка MediaPlayer
            }
        } catch (e: Exception) {
            // Логирование ошибки для отладки
            e.printStackTrace()
            // Показ сообщения пользователю о том, что произошла ошибка
            Toast.makeText(this, "Ошибка: не удалось воспроизвести радио. Проверьте URL.", Toast.LENGTH_LONG).show()
            // Дополнительная логика, например, сброс состояния или выключение проигрывателя
            stopMusic()
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


    private fun saveRadioStations(radioStations: List<RadioStation>) {
        val sharedPreferences = getSharedPreferences("RadioPreferences", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(radioStations)
        editor.putString("RadioStations", json)
        editor.apply()
    }

    private fun loadRadioStations(): MutableList<RadioStation> {
        val sharedPreferences = getSharedPreferences("RadioPreferences", MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("RadioStations", null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<RadioStation>>() {}.type
            gson.fromJson(json, type)
        } else {
            mutableListOf()
        }
    }

    override fun onResume() {
        super.onResume()
        radioStations = loadRadioStations()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Освобождение ресурсов MediaPlayer при завершении активности
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

