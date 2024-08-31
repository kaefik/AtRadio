package com.example.iradio

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
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
    private var favoriteStations: MutableList<RadioStation?> = mutableListOf(null, null, null)  // Переменная для хранения трех избранных радиостанций
    private var currentRadioStation: Int = 0
    private lateinit var buttonPlay: Button
    private lateinit var statusRadio: TextView
    private lateinit var progressBar: ProgressBar
    private var statusPlay: Boolean = false // статус проигрывания текущей станции

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

        statusRadio = findViewById<TextView>(R.id.statusRadio)
        buttonPlay = findViewById(R.id.buttonPlay)
        val buttonVolUp: Button = findViewById(R.id.buttonVolumeUp)
        val buttonVolDown: Button = findViewById(R.id.buttonVolumeDown)
        val buttonForward: Button = findViewById(R.id.buttonForward)
        val buttonPrev: Button = findViewById(R.id.buttonPrev)
        val buttonListRadioStations: Button = findViewById(R.id.buttonListRadioStations)
        val buttonFav1: Button = findViewById(R.id.buttonFav1)
        val buttonFav2: Button = findViewById(R.id.buttonFav2)
        val buttonFav3: Button = findViewById(R.id.buttonFav3)


        progressBar = findViewById(R.id.progressBar)

        currentRadioStation = loadLastRadioStation()

        radioStations = loadRadioStations() // Загружаем список радиостанций
        favoriteStations = loadFavoriteStations()

        if (radioStations.size<=currentRadioStation){
            currentRadioStation = 0
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
            currentRadioStation = 0
            statusRadio.text = radioStations[currentRadioStation].name
        } else{
            statusRadio.text = radioStations[currentRadioStation].name
        }

        val listRadioStationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                radioStations = loadRadioStations() // Перезагружаем список радиостанций

                // Обновляем интерфейс, если радиостанций не осталось или выбранная станция была удалена
                if (radioStations.isEmpty()) {
                    stopMusic()
                    statusRadio.text = "Empty list stations"
                } else {
                    if (currentRadioStation >= radioStations.size) {
                        currentRadioStation = 0
                    }
                    statusRadio.text = radioStations[currentRadioStation].name
                }
            }
        }

        // сохранение избранных радиостанции

        // Обработчик для сохранения радиостанции в первый индекс
        buttonFav1.setOnLongClickListener {
            showSaveDialog(0)
            true
        }

        // Обработчик для сохранения радиостанции во второй индекс
        buttonFav2.setOnLongClickListener {
            showSaveDialog(1)
            true
        }

        // Обработчик для сохранения радиостанции в третий индекс
        buttonFav3.setOnLongClickListener {
            showSaveDialog(2)
            true
        }



        // Обработчики для одиночного нажатия для воспроизведения

        buttonFav1.setOnClickListener{
            handleFavoriteButtonClick(0) //, buttonFav1)
        }

        buttonFav2.setOnClickListener {
            handleFavoriteButtonClick(1) //, buttonFav2)
        }

        buttonFav3.setOnClickListener {
            handleFavoriteButtonClick(2) //, buttonFav3)
        }



        // END сохранение избранных радиостанции

        buttonListRadioStations.setOnClickListener {
            val intent = Intent(this, RadioStationListActivity::class.java)
            intent.putParcelableArrayListExtra("radioStations", ArrayList(radioStations))
            listRadioStationLauncher.launch(intent)
        }

        // кнопки управления воспроизведением радиостанции

        buttonForward.setOnClickListener{
            if (radioStations.isEmpty()) {
                currentRadioStation = 0
                stopMusic()
                statusRadio.text = "Empty list stations"
            }
            else {
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
        }

        buttonPrev.setOnClickListener{
            if (radioStations.isEmpty()) {
                currentRadioStation = 0
                stopMusic()
                statusRadio.text = "Empty list stations"
            } else {
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
        }


        buttonVolUp.setOnClickListener{
            volumeControl.increaseVolume()
        }

        buttonVolDown.setOnClickListener{
            volumeControl.decreaseVolume()
        }

        buttonPlay.setOnClickListener{

            if (radioStations.isEmpty()) {
                currentRadioStation = 0
                stopMusic()
                statusRadio.text = "Empty list stations"
            } else {
                if (statusPlay) {
                    statusRadio.text = radioStations[currentRadioStation].name
                    buttonPlay.text = "Play"
                    statusPlay = false
                    stopMusic()
                }
                else {
                    buttonPlay.text = "Stop"
                    statusPlay = true

                    stopMusic()
                    statusRadio.text = radioStations[currentRadioStation].name
                    startMusic(radioStations[currentRadioStation], progressBar)
                }
            }
        }


    }

    // END кнопки управления воспроизведением радиостанции

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
            Toast.makeText(this, "Error: Check URL station.", Toast.LENGTH_LONG).show()
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

    // сохранение настроек приложения

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

    private fun saveFavoriteStations() {
        val sharedPreferences = getSharedPreferences("RadioPreferences", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(favoriteStations)
        editor.putString("FavoriteStations", json)
        editor.apply()
    }

    private fun loadFavoriteStations(): MutableList<RadioStation?> {
        val sharedPreferences = getSharedPreferences("RadioPreferences", MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("FavoriteStations", null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<RadioStation>>() {}.type
            gson.fromJson(json, type)
        } else {
            mutableListOf(null, null, null)
        }
    }

    // END сохранение настроек приложения

    // Функция для отображения диалогового окна и сохранения станции
    private fun showSaveDialog(index: Int) {
            // Создаем диалоговое окно
            val builder = AlertDialog.Builder(this)
            builder.setMessage("Save current station?")
                .setPositiveButton("Yes") { dialog, id ->
                    println("favoriteStations -> $favoriteStations")
                    favoriteStations[index] = radioStations[currentRadioStation]
                    saveFavoriteStations()
                    Toast.makeText(this, "Favorite station $index saved", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("No") { dialog, id ->
                    // Ничего не делаем, просто закрываем диалог
                    dialog.dismiss()
                }
            // Показать диалоговое окно
            builder.create().show()
    }

    // Функция для обработки нажатия на кнопку избранной станции
    private fun handleFavoriteButtonClick(index: Int) { //, button: Button) {
        favoriteStations.getOrNull(index)?.let { station ->
            buttonPlay.text = "Stop"
            statusPlay = true
            stopMusic()
            statusRadio.text = station.name
            startMusic(station, progressBar)
            Toast.makeText(this, "Воспроизведение избранной станции ${index + 1}: ${station.name}", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(this, "Избранная станция ${index + 1} не сохранена", Toast.LENGTH_SHORT).show()
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

