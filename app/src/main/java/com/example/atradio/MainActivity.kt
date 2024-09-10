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
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.util.TypedValue
import android.view.ViewGroup

import android.content.Context
import android.content.res.Configuration
import java.util.*

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private var kol = 0

    // для сервиса плеера
    private var radioService: RadioService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RadioService.LocalBinder
            radioService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            radioService = null
            isBound = false
        }
    }

    // END для сервиса плеера

    private lateinit var volumeControl: VolumeControl
    private lateinit var appSettings: AppSettings
    private val gson = Gson()

    private lateinit var buttonPlay: ImageButton
    private lateinit var statusRadio: TextView
    private lateinit var progressBar: ProgressBar
    private var statusPlay: Boolean = false // статус проигрывания текущей станции

    //заставка - сринсейвер
    private lateinit var dimView: View
    private lateinit var blackView: View
    private lateinit var radioText: TextView

    private var directionX = 1 // Направление по оси X (1 - вправо, -1 - влево)
    private var directionY = 1 // Направление по оси Y (1 - вниз, -1 - вверх)
    private var velocity = 5f // Скорость перемещения текста

    private val dimDelay = 30_000L // 30 секунд
    private val blackDelay = 10_000L // 10 секунд
    private val moveDelay = 15_000L // 15 секунд

    private val handler = Handler(Looper.getMainLooper())
    private val dimRunnable = Runnable {
        dimView.visibility = View.VISIBLE
    }

    private val blackRunnable = Runnable {
        disableUIElements()
        dimView.visibility = View.GONE
        blackView.visibility = View.VISIBLE
        radioText.visibility = View.VISIBLE
        var newText = getString(R.string.at_radio)
        if (appSettings.radioStations.isNotEmpty()){
            newText = appSettings.radioStations[appSettings.lastRadioStationIndex].name
            val newSizeText = 15
            if (newText.length > newSizeText) {
                newText = newText.substring(0, newSizeText) + "..."
            }
        }
        radioText.text = newText
        radioText.setTextSize(TypedValue.COMPLEX_UNIT_PX, 100f)
        radioText.setTextColor(ContextCompat.getColor(this, R.color.gray))
        startMovingText()
    }

    private val moveTextRunnable = object : Runnable {
        override fun run() {
            moveText()
            handler.postDelayed(this, 50) // Обновляем позицию каждые 16 мс (~60 fps)
        }
    }
    // END заставка - сринсейвер

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("SetTextI18n", "SuspiciousIndentation")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Загружаем настройки при старте
        appSettings = loadAppSettings()

        // Привязка к сервису плееера
        Intent(this, RadioService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }


        // локализация приложения
        // Проверяем, был ли уже выбран язык ранее

        if (appSettings.language.isNullOrEmpty()) {
            // Если язык не был выбран, показываем диалог для выбора языка
            showLanguageDialog()
        } else {
            // Если язык был выбран ранее, устанавливаем его
            setLocale(appSettings.language)
            setContentView(R.layout.activity_main)
        }
        // END локализация приложения

        // заставка - сринсейвер
        dimView = findViewById(R.id.dim_view)
        blackView = findViewById(R.id.black_view)
        radioText = findViewById(R.id.radio_text)

        resetTimers()

        // END заставка - сринсейвер

        // Скрытие строки статуса
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
            window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }

        volumeControl = VolumeControl(this)

        statusRadio = findViewById(R.id.statusRadio)
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

        if (appSettings.radioStations.size <= appSettings.lastRadioStationIndex) {
            appSettings.lastRadioStationIndex = 0
        }

        if (appSettings.radioStations.isEmpty()) {
            // Загрузка радиостанций из CSV
            appSettings.radioStations.addAll(loadRadioStationsFromRaw(this, R.raw.radio_stations_default))

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
                radioService?.stopMusic()
                val radioStationUrl = appSettings.radioStations[appSettings.lastRadioStationIndex].url
                val isNotErrorPlay = radioService?.startMusic(radioStationUrl)
                if (!isNotErrorPlay!!) {
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
//                    stopMusic()
                    radioService?.stopMusic()
                    statusPlay = false
                    buttonPlay.setImageResource(R.drawable.play_64)
                    statusRadio.text = getString(R.string.empty_list_stations)
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
                    radioService?.stopMusic()
                    val radioStationUrl = appSettings.radioStations[appSettings.lastRadioStationIndex].url
                    val isNotErrorPlay = radioService?.startMusic(radioStationUrl)
                    if (!isNotErrorPlay!!) {
                        onErrorPlay()
                    }
                }

            }
        }

        val settingAppLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                appSettings = loadAppSettings() // Перезагружаем все настройки
                setLocale(appSettings.language)
                recreate()
            }
            resetTimers()
        }

        buttonSettings.setOnClickListenerWithScreenSaverReset {
            val intent = Intent(this, SettingsActivity::class.java)
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

        buttonFav1.setOnClickListenerWithScreenSaverReset {
            // сначала выполняется код из View.setOnClickListenerWithScreenSaverReset
            handleFavoriteButtonClick(0)
        }

        buttonFav2.setOnClickListenerWithScreenSaverReset {
            handleFavoriteButtonClick(1)
        }

        buttonFav3.setOnClickListenerWithScreenSaverReset {
            handleFavoriteButtonClick(2)
        }

        buttonListRadioStations.setOnClickListenerWithScreenSaverReset {
            val intent = Intent(this, RadioStationListActivity::class.java)
            intent.putParcelableArrayListExtra("radioStations", ArrayList(appSettings.radioStations))
            listRadioStationLauncher.launch(intent)
        }

        buttonForward.setOnClickListenerWithScreenSaverReset {
            if (appSettings.radioStations.isEmpty()) {
                appSettings.lastRadioStationIndex = 0
                radioService?.stopMusic()
                statusRadio.text = "Empty list stations"
            } else {
                appSettings.lastRadioStationIndex += 1
                if (appSettings.radioStations.size <= appSettings.lastRadioStationIndex)
                    appSettings.lastRadioStationIndex = 0
                saveAppSettings(appSettings)
                statusRadio.text = appSettings.radioStations[appSettings.lastRadioStationIndex].name
                if (statusPlay) {
                    radioService?.stopMusic()

                    statusRadio.setTextColor(ContextCompat.getColor(this, R.color.play))
                    val radioStationUrl = appSettings.radioStations[appSettings.lastRadioStationIndex].url
                    val isNotErrorPlay = radioService?.startMusic(radioStationUrl)
                    if (!isNotErrorPlay!!) {
                        onErrorPlay()
                    }
                } else {
                    radioService?.stopMusic()
                    statusRadio.setTextColor(ContextCompat.getColor(this, R.color.stop))
                }
            }
        }

        buttonPrev.setOnClickListenerWithScreenSaverReset {
            if (appSettings.radioStations.isEmpty()) {
                appSettings.lastRadioStationIndex = 0
//                stopMusic()
                radioService?.stopMusic()
                statusRadio.text = "Empty list stations"
            } else {
                appSettings.lastRadioStationIndex -= 1
                if (appSettings.lastRadioStationIndex < 0)
                    appSettings.lastRadioStationIndex = appSettings.radioStations.size - 1

                saveAppSettings(appSettings)
                statusRadio.text = appSettings.radioStations[appSettings.lastRadioStationIndex].name

                if (statusPlay) {
                    radioService?.stopMusic()
                    statusRadio.setTextColor(ContextCompat.getColor(this, R.color.play))
                    val radioStationUrl = appSettings.radioStations[appSettings.lastRadioStationIndex].url
                    val isNotErrorPlay = radioService?.startMusic(radioStationUrl)
                    if (!isNotErrorPlay!!) {
                        onErrorPlay()
                    }
                } else {
                    radioService?.stopMusic()
                    statusRadio.setTextColor(ContextCompat.getColor(this, R.color.stop))
                }
            }
        }

        buttonVolUp.setOnClickListenerWithScreenSaverReset {
            volumeControl.increaseVolume()
        }

        buttonVolDown.setOnClickListenerWithScreenSaverReset {
            volumeControl.decreaseVolume()
        }

        buttonPlay.setOnClickListenerWithScreenSaverReset {
            if (appSettings.radioStations.isEmpty()) {
                appSettings.lastRadioStationIndex = 0
                radioService?.stopMusic()
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
                    radioService?.stopMusic()
                } else {
                    buttonPlay.setImageResource(R.drawable.stop_64)
                    statusPlay = true
                    radioService?.stopMusic()
                    statusRadio.text = appSettings.radioStations[appSettings.lastRadioStationIndex].name
                    statusRadio.setTextColor(ContextCompat.getColor(this, R.color.play))
                    val radioStationUrl = appSettings.radioStations[appSettings.lastRadioStationIndex].url
                    val isNotErrorPlay = radioService?.startMusic(radioStationUrl)
                    if (!isNotErrorPlay!!) {
                        onErrorPlay()
                    }
                }
            }
        }
    }

    private fun updateUIForPlaying() {
        statusRadio.setTextColor(ContextCompat.getColor(this, R.color.play))
        buttonPlay.setImageResource(R.drawable.stop_64)
    }

    private fun updateUIForStopped() {
        statusRadio.setTextColor(ContextCompat.getColor(this, R.color.stop))
        buttonPlay.setImageResource(R.drawable.play_64)
    }


    private fun showSaveDialog(favIndex: Int) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.save_favorite_message) + (favIndex + 1) + "?")
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                appSettings.favoriteStations[favIndex] = appSettings.radioStations[appSettings.lastRadioStationIndex]
                saveAppSettings(appSettings)
                Toast.makeText(this, getString(R.string.save_station_favorite)+" ${favIndex + 1}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun handleFavoriteButtonClick(favIndex: Int) {
        appSettings.favoriteStations[favIndex]?.let {
            if (statusPlay) {
//                stopMusic()
                radioService?.stopMusic()
            }
            statusPlay = true
            appSettings.lastRadioStationIndex = appSettings.radioStations.indexOf(it)
            statusRadio.text = it.name
            val radioStationUrl = it.url
            val isNotErrorPlay = radioService?.startMusic(radioStationUrl)
            if (!isNotErrorPlay!!) {
                onErrorPlay()
            }

        } ?: Toast.makeText(this, getString(R.string.no_station_saved_to_favorite) + " ${favIndex + 1}", Toast.LENGTH_SHORT).show()
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
           initAppSettings(this)
        }
    }

    // END сохранение настроек приложения

    // заставка - сринсейвер
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            resetTimers()
            restoreBrightness()
        }
        return super.onTouchEvent(event)
    }

    // Добавляем обработку событий onPause и onResume
    override fun onPause() {
        super.onPause()
        stopScreenSaver() // Остановить скринсейвер
    }

    override fun onResume() {
        super.onResume()
        if (appSettings.isScreenSaverEnabled) {
            resetTimers() // Сбрасываем таймеры только если скринсейвер разрешен
        }
    }

    // Метод для остановки таймеров и скрытия скринсейвера
    private fun stopScreenSaver() {
        handler.removeCallbacksAndMessages(null)
        restoreBrightness() // Скрыть элементы скринсейвера
        enableUIElements() // Включить элементы интерфейса
    }

    internal fun resetTimers() {
        // Добавляем проверку, чтобы убедиться, что скринсейвер не запускается, если он отключен
        if (!appSettings.isScreenSaverEnabled) {
            return
        }

        handler.removeCallbacks(dimRunnable)
        handler.removeCallbacks(blackRunnable)
        handler.removeCallbacks(moveTextRunnable)
        handler.postDelayed(dimRunnable, dimDelay)
        handler.postDelayed(blackRunnable, dimDelay + blackDelay)
        handler.postDelayed(moveTextRunnable, dimDelay + blackDelay + moveDelay)
    }


    internal fun restoreBrightness() {
        dimView.visibility = View.GONE
        blackView.visibility = View.GONE
        radioText.visibility = View.GONE
        handler.removeCallbacks(moveTextRunnable)
        enableUIElements() // Включить элементы интерфейса
    }

    private fun startMovingText() {
        handler.post(moveTextRunnable)
    }

    private fun moveText() {
        val parentWidth = blackView.width
        val parentHeight = blackView.height
        val textWidth = radioText.width
        val textHeight = radioText.height

        // Текущая позиция текста
        var currentX = radioText.translationX
        var currentY = radioText.translationY

        // Обновляем позицию текста
        currentX += directionX * velocity
        currentY += directionY * velocity

        // Проверяем столкновение с границами экрана и изменяем направление
        if (currentX <= 0 || currentX + textWidth >= parentWidth) {
            directionX *= -1 // Изменяем направление по оси X
        }
        if (currentY <= 0 || currentY + textHeight >= parentHeight) {
            directionY *= -1 // Изменяем направление по оси Y
        }

        // Устанавливаем новую позицию текста
        radioText.translationX = currentX
        radioText.translationY = currentY
    }

    // Метод для отключения всех элементов интерфейса
    private fun disableUIElements() {
        findViewById<ViewGroup>(R.id.main).setChildrenEnabled(false)
    }

    // Метод для включения всех элементов интерфейса
    private fun enableUIElements() {
        findViewById<ViewGroup>(R.id.main).setChildrenEnabled(true)
    }

    // Функция-расширение для отключения или включения всех дочерних элементов ViewGroup
    private fun ViewGroup.setChildrenEnabled(enabled: Boolean) {
        for (i in 0 until childCount) {
            getChildAt(i).isEnabled = enabled
        }
    }

    // локализация приложения под различные языки

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Татарча", "Башҡортса", "Русский")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select the language app")
        builder.setSingleChoiceItems(languages, -1) { dialog, which ->
            when (which) {
                0 -> {
                    setLocale("en")
                }
                1 -> {
                    setLocale("tt")
                }
                2 -> {
                    setLocale("ba")
                }
                3 -> {
                    setLocale("ru")
                }
            }
            dialog.dismiss()
        }
        builder.setOnDismissListener {
            // Перезапуск MainActivity после выбора языка
            recreate()
        }
        builder.show()
    }

    private fun setLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        // Сохраняем выбранный язык
        appSettings.language=languageCode
        saveAppSettings(appSettings)
        resources.updateConfiguration(config, resources.displayMetrics)
    }


    override fun onDestroy() {
        super.onDestroy()
        // Освобождение ресурсов MediaPlayer при завершении активности
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }



}

// Описание функции-расширения должно быть вне класса MainActivity
// чтобы при нажатии на любой элемент активити прекращал работаь скринсейвер
fun View.setOnClickListenerWithScreenSaverReset(action: () -> Unit) {
    this.setOnClickListener {
        (context as MainActivity).resetTimers()      // Сбрасываем таймеры скринсейвера
        (context as MainActivity).restoreBrightness() // Прекращаем работу скринсейвера
        action() // Выполнение основной логики
    }
}

