package com.example.atradio

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
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
import android.widget.ImageButton
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var volumeControl: VolumeControl
    private lateinit var appSettings: AppSettings
    private val gson = Gson()

    private lateinit var buttonPlay: ImageButton
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
        val buttonVolUp: ImageButton = findViewById(R.id.buttonVolumeUp)
        val buttonVolDown: ImageButton = findViewById(R.id.buttonVolumeDown)
        val buttonForward: ImageButton = findViewById(R.id.buttonForward)
        val buttonPrev: ImageButton = findViewById(R.id.buttonPrev)
        val buttonListRadioStations: ImageButton = findViewById(R.id.buttonListRadioStations)
        val buttonFav1: ImageButton = findViewById(R.id.buttonFav1)
        val buttonFav2: ImageButton = findViewById(R.id.buttonFav2)
        val buttonFav3: ImageButton = findViewById(R.id.buttonFav3)
        val buttonSettings: ImageButton = findViewById(R.id.buttonSettings)

        progressBar = findViewById(R.id.progressBar)

        // Загружаем настройки при старте
        appSettings = loadAppSettings() ?: AppSettings(
            favoriteStations = mutableListOf(null, null, null),
            isAutoPlayEnabled = false,
            lastRadioStationIndex = 0,
            radioStations = mutableListOf()
        )

        if (appSettings.radioStations.size <= appSettings.lastRadioStationIndex) {
            appSettings.lastRadioStationIndex = 0
        }

        if (appSettings.radioStations.isEmpty()) {
            appSettings.radioStations.addAll(listOf(
                RadioStation(name = "Классик ФМ", url = "http://cfm.jazzandclassic.ru:14536/rcstream.mp3"),
                RadioStation(name = "Bolgar Radiosi", url = "http://stream.tatarradio.ru:2068/;stream/1"),
                RadioStation(name = "Детское радио (Дети ФМ)", url = "http://ic5.101.ru:8000/v14_1"),
                RadioStation(name = "Монте Карло", url = "https://montecarlo.hostingradio.ru/montecarlo128.mp3"),
                RadioStation(name = "Saf Radiosi", url = "https://c7.radioboss.fm:18335/stream")
            ))
            saveAppSettings(appSettings)
            appSettings.lastRadioStationIndex = 0
            statusRadio.text = appSettings.radioStations[appSettings.lastRadioStationIndex].name
            statusRadio.setTextColor(ContextCompat.getColor(this, R.color.stop))
        } else {
            if (appSettings.isAutoPlayEnabled ) {
                statusPlay = true
                statusRadio.text = appSettings.radioStations[appSettings.lastRadioStationIndex].name
                statusRadio.setTextColor(ContextCompat.getColor(this, R.color.play))
                buttonPlay.setImageResource(R.drawable.stop_64)
                stopMusic()
                val isNotErrorPlay = startMusic(appSettings.radioStations[appSettings.lastRadioStationIndex], progressBar)
                if (!isNotErrorPlay) {
                    onErrorPlay()
                }
            } else {
                statusRadio.text = appSettings.radioStations[appSettings.lastRadioStationIndex].name
                statusRadio.setTextColor(ContextCompat.getColor(this, R.color.stop))
                buttonPlay.setImageResource(R.drawable.play_64)
            }
        }

        val listRadioStationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val updatedRadioStations = result.data?.getParcelableArrayListExtra<RadioStation>("radioStations")?.toMutableList()
                if (updatedRadioStations != null) {
                    val mutableRadioStations = updatedRadioStations.toMutableList()
                    appSettings.radioStations.clear()
                    appSettings.radioStations.addAll(mutableRadioStations)
                    saveAppSettings(appSettings)
                }

                if (appSettings.radioStations.isEmpty()) {
                    stopMusic()
                    statusPlay = false
                    buttonPlay.setImageResource(R.drawable.play_64)
                    statusRadio.text = "Empty list stations"
                    statusRadio.setTextColor(ContextCompat.getColor(this, R.color.stop))
                } else {
                    if (appSettings.lastRadioStationIndex >= appSettings.radioStations.size) {
                        appSettings.lastRadioStationIndex = 0
                    }
                    statusRadio.text = appSettings.radioStations[appSettings.lastRadioStationIndex].name
                    statusRadio.setTextColor(ContextCompat.getColor(this, R.color.stop))
                }

                val selectedStation = result.data?.getParcelableExtra<RadioStation>("selectedStation")
                if (selectedStation != null) {
                    appSettings.lastRadioStationIndex = appSettings.radioStations.indexOf(selectedStation)
                    saveAppSettings(appSettings)
                    statusPlay = true
                    statusRadio.text = selectedStation.name
                    statusRadio.setTextColor(ContextCompat.getColor(this, R.color.play))
                    buttonPlay.setImageResource(R.drawable.stop_64)
                    stopMusic()
                    val isNotErrorPlay = startMusic(selectedStation, progressBar)
                    if (!isNotErrorPlay) {
                        onErrorPlay()
                    }
                }

            }
        }

        val settingAppLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//            if (result.resultCode == Activity.RESULT_OK) {
                appSettings= loadAppSettings() // Перезагружаем все настройки
//            }
        }

        buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
////            intent.putParcelableArrayListExtra("radioStations", ArrayList(appSettings.radioStations))
            settingAppLauncher.launch(intent)

        }

        buttonFav1.setOnLongClickListener {
            showSaveDialog(0)
            true
        }

        buttonFav2.setOnLongClickListener {
            showSaveDialog(1)
            true
        }

        buttonFav3.setOnLongClickListener {
            showSaveDialog(2)
            true
        }

        buttonFav1.setOnClickListener {
            handleFavoriteButtonClick(0)
        }

        buttonFav2.setOnClickListener {
            handleFavoriteButtonClick(1)
        }

        buttonFav3.setOnClickListener {
            handleFavoriteButtonClick(2)
        }

        buttonListRadioStations.setOnClickListener {
            val intent = Intent(this, RadioStationListActivity::class.java)
            intent.putParcelableArrayListExtra("radioStations", ArrayList(appSettings.radioStations))
            listRadioStationLauncher.launch(intent)
        }

        buttonForward.setOnClickListener {
            if (appSettings.radioStations.isEmpty()) {
                appSettings.lastRadioStationIndex = 0
                stopMusic()
                statusRadio.text = "Empty list stations"
            } else {
                appSettings.lastRadioStationIndex += 1
                if (appSettings.radioStations.size <= appSettings.lastRadioStationIndex)
                    appSettings.lastRadioStationIndex = 0
                saveAppSettings(appSettings)
                statusRadio.text = appSettings.radioStations[appSettings.lastRadioStationIndex].name
                if (statusPlay) {
                    stopMusic()
                    statusRadio.setTextColor(ContextCompat.getColor(this, R.color.play))
                    val isNotErrorPlay = startMusic(appSettings.radioStations[appSettings.lastRadioStationIndex], progressBar)
                    if (!isNotErrorPlay) {
                        onErrorPlay()
                    }
                } else {
                    stopMusic()
                    statusRadio.setTextColor(ContextCompat.getColor(this, R.color.stop))
                }
            }
        }

        buttonPrev.setOnClickListener {
            if (appSettings.radioStations.isEmpty()) {
                appSettings.lastRadioStationIndex = 0
                stopMusic()
                statusRadio.text = "Empty list stations"
            } else {
                appSettings.lastRadioStationIndex -= 1
                if (appSettings.lastRadioStationIndex < 0)
                    appSettings.lastRadioStationIndex = appSettings.radioStations.size - 1

                saveAppSettings(appSettings)
                statusRadio.text = appSettings.radioStations[appSettings.lastRadioStationIndex].name

                if (statusPlay) {
                    stopMusic()
                    statusRadio.setTextColor(ContextCompat.getColor(this, R.color.play))
                    val isNotErrorPlay = startMusic(appSettings.radioStations[appSettings.lastRadioStationIndex], progressBar)
                    if (!isNotErrorPlay) {
                        onErrorPlay()
                    }
                } else {
                    stopMusic()
                    statusRadio.setTextColor(ContextCompat.getColor(this, R.color.stop))
                }
            }
        }

        buttonVolUp.setOnClickListener {
            volumeControl.increaseVolume()
        }

        buttonVolDown.setOnClickListener {
            volumeControl.decreaseVolume()
        }

        buttonPlay.setOnClickListener {
            if (appSettings.radioStations.isEmpty()) {
                appSettings.lastRadioStationIndex = 0
                stopMusic()
                statusRadio.text = "Empty list stations"
                statusPlay = false
                statusRadio.setTextColor(ContextCompat.getColor(this, R.color.stop))
                buttonPlay.setImageResource(R.drawable.play_64)
            } else {
                if (statusPlay) {
                    statusRadio.text = appSettings.radioStations[appSettings.lastRadioStationIndex].name
                    statusRadio.setTextColor(ContextCompat.getColor(this, R.color.stop))
                    buttonPlay.setImageResource(R.drawable.play_64)
                    statusPlay = false
                    stopMusic()
                } else {
                    buttonPlay.setImageResource(R.drawable.stop_64)
                    statusPlay = true
                    stopMusic()
                    statusRadio.text = appSettings.radioStations[appSettings.lastRadioStationIndex].name
                    statusRadio.setTextColor(ContextCompat.getColor(this, R.color.play))
                    val isNotErrorPlay = startMusic(appSettings.radioStations[appSettings.lastRadioStationIndex], progressBar)
                    if (!isNotErrorPlay) {
                        onErrorPlay()
                    }
                }
            }
        }
    }

    // если все проходит без ошибок, то возвр-ся true, иначе false
    private fun startMusic(radioStation: RadioStation, progressBar: ProgressBar): Boolean {
        stopMusic()

        return try {
            var hasErrorOccurred = false
            mediaPlayer = MediaPlayer().apply {
                setDataSource(radioStation.url)

                setOnPreparedListener {
                    progressBar.visibility = View.GONE
                    start()
                }

                setOnErrorListener { _, _, _ ->
                    stopMusic()
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Error playing station", Toast.LENGTH_SHORT).show()
                    onErrorPlay()
                    false
                }

                progressBar.visibility = View.VISIBLE
                prepareAsync()

            }
            true
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            onErrorPlay()
            false // Возвращаем false, если произошло исключение
        }
    }

    private fun stopMusic() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
    }

    private fun showSaveDialog(favIndex: Int) {
        AlertDialog.Builder(this)
            .setMessage("Save current station as favorite ${favIndex + 1}?")
            .setPositiveButton("Save") { _, _ ->
                appSettings.favoriteStations[favIndex] = appSettings.radioStations[appSettings.lastRadioStationIndex]
                saveAppSettings(appSettings)
                Toast.makeText(this, "Station saved to favorite ${favIndex + 1}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleFavoriteButtonClick(favIndex: Int) {
        appSettings.favoriteStations[favIndex]?.let {
            if (statusPlay) {
                stopMusic()
            }
            statusPlay = true
            appSettings.lastRadioStationIndex = appSettings.radioStations.indexOf(it)
            statusRadio.text = it.name
            val isNotErrorPlay = startMusic(it, progressBar)
            if (isNotErrorPlay) {
                statusRadio.setTextColor(ContextCompat.getColor(this, R.color.play))
                buttonPlay.setImageResource(R.drawable.stop_64)
            } else {
               onErrorPlay()
            }

        } ?: Toast.makeText(this, "No station saved to favorite ${favIndex + 1}", Toast.LENGTH_SHORT).show()
    }

    // обработка ошибки проигрывания музыки
    private fun onErrorPlay(){
        statusRadio.setTextColor(ContextCompat.getColor(this, R.color.stop))
        buttonPlay.setImageResource(R.drawable.play_64)
        statusPlay = false

    }

    // сохранение настроек приложения

    private fun saveAppSettings(settings: AppSettings) {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val json = gson.toJson(settings)
        editor.putString("AppSettingsData", json)
        editor.apply()
    }

    private fun loadAppSettings(): AppSettings {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val json = sharedPreferences.getString("AppSettingsData", null)
        return if (json != null) {
            val type = object : TypeToken<AppSettings>() {}.type
            gson.fromJson(json, type)
        } else {
            // Возвращаем настройки по умолчанию, если они отсутствуют
            AppSettings(
                favoriteStations = mutableListOf(null, null, null), // Пустые избранные станции
                isAutoPlayEnabled = false, // Значение по умолчанию
                lastRadioStationIndex = 0, // Первая радиостанция в списке
                radioStations = mutableListOf() // Пустой список радиостанций
            )
        }
    }

    // END сохранение настроек приложения

}
