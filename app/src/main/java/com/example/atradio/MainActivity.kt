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

import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.provider.Settings
import android.Manifest
import android.view.KeyEvent
import android.widget.RemoteViews
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView
import kotlinx.coroutines.DelicateCoroutinesApi
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


class MainActivity : AppCompatActivity() {


    // для запроса
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, proceed with your app logic
            Log.d("iAtRadio", " MainActivity Notification permission granted")
        } else {
            // Permission denied, show a dialog explaining why the permission is needed
            showPermissionDeniedDialog()
        }
    }


    private val infoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isPlayed = intent?.getSerializableExtra("PLAY") as? MusicStatus // Принимаем MusicStatus
//            val stationFromService = intent?.getStringExtra("STATION")

            Log.e("iAtRadio", "MainActivity -> infoReceiver -> isPlayed = $isPlayed")


            if (isPlayed != null) {
                statusPlay = isPlayed
                appSettings = loadAppSettings()
                statusRadio.text = appSettings.currentStation.name
                when (isPlayed) {
                    MusicStatus.PLAYING -> {
                        updateUIForPlaying()
                    }
                    MusicStatus.STOPPED -> {
                        updateUIForStopped()
                    }
                    MusicStatus.LOADING -> {
                        updateUIForConnected()
                    }
                }
            }
        }
    }

    private val errorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val errorMessage = intent?.getStringExtra("ERROR_MESSAGE")
            errorMessage?.let {
                Log.e("iAtRadio", "MainActivity -> Error received: $it")
                Toast.makeText(this@MainActivity, "Ошибка: $it", Toast.LENGTH_SHORT).show()
                // Здесь можно выполнить любые действия на основе ошибки
                statusPlay = MusicStatus.STOPPED
                updateUIForStopped()
            }
        }
    }


    private val listRadioStationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val updatedRadioStations = result.data?.getParcelableArrayListExtra<RadioStation>("radioStations")?.toMutableList()

            appSettings = loadAppSettings()

            if (updatedRadioStations != null) {
                val mutableRadioStations = updatedRadioStations.toMutableList()
                appSettings.radioStations.clear()
                appSettings.radioStations.addAll(mutableRadioStations)
                saveAppSettings(appSettings)
            }

            if (appSettings.radioStations.isEmpty()) {
                stopPlayback()
                statusPlay = MusicStatus.STOPPED
                updateUIForStopped()
                statusRadio.text = getString(R.string.empty_list_stations)
                appSettings.currentStation = RadioStation("", "")

            } else {
                if (appSettings.lastRadioStationIndex >= appSettings.radioStations.size) {
                    appSettings.lastRadioStationIndex = 0
                }
                appSettings.currentStation = appSettings.radioStations[appSettings.lastRadioStationIndex]
                statusRadio.text = appSettings.currentStation.name
            }

            val selectedStation = result.data?.getParcelableExtra<RadioStation>("selectedStation")
            if (selectedStation != null) {
                appSettings.lastRadioStationIndex = appSettings.radioStations.indexOf(selectedStation)
                appSettings.currentStation = selectedStation
                statusRadio.text = appSettings.currentStation.name
                saveAppSettings(appSettings)
//                updateUIForPlaying()
                // запуск сервера
                pausePlayback()
                statusPlay = MusicStatus.LOADING
                updateUIForConnected()
                playStation(appSettings.currentStation)

                // END запуск сервера
            }
        }
    }

    private val settingAppLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            appSettings = loadAppSettings() // Перезагружаем все настройки
            setLocale(appSettings.language)
        }
        resetTimers()
    }

    // код запроса разрешения
    private val REQUEST_BLUETOOTH_PERMISSIONS = 1

    private lateinit var volumeControl: VolumeControl
    private lateinit var appSettings: AppSettings
    private val gson = Gson()

    private lateinit var buttonVolUp: ImageButton
    private lateinit var buttonVolDown: ImageButton
    private lateinit var buttonForward: ImageButton
    private lateinit var buttonPrev: ImageButton
    private lateinit var buttonListRadioStations: ImageButton
    private lateinit var buttonFav1: ImageButton
    private lateinit var buttonFav2: ImageButton
    private lateinit var buttonFav3: ImageButton
    private lateinit var buttonSettings: ImageButton

    private lateinit var buttonPlay: ImageButton
    private lateinit var statusRadio: TextView
    private lateinit var progressBar: ProgressBar
    private var statusPlay: MusicStatus = MusicStatus.STOPPED // статус проигрывания текущей станции

    //заставка - сринсейвер
    private lateinit var dimView: View
    private lateinit var blackView: View
    private lateinit var radioText: TextView

    private var directionX = 1 // Направление по оси X (1 - вправо, -1 - влево)
    private var directionY = 1 // Направление по оси Y (1 - вниз, -1 - вверх)
    private var velocity = 5f // Скорость перемещения текста

    private val dimDelay = 100_000L // 100 секунд
    private val blackDelay = 60_000L // 60 секунд
    private val moveDelay = 80_000L // 80 секунд

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
            newText = appSettings.currentStation.name
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

    @OptIn(DelicateCoroutinesApi::class)
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

        Log.d("iAtRadio", "MainActivity onCreate -> begin -> statusPlay")

        // Загружаем настройки при старте
        appSettings = loadAppSettings()
        Log.d("iAtRadio", "MainActivity onCreate -> appSettings = $appSettings")

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
        buttonVolUp  = findViewById(R.id.buttonVolumeUp)
        buttonVolDown  = findViewById(R.id.buttonVolumeDown)
        buttonForward  = findViewById(R.id.buttonForward)
        buttonPrev  = findViewById(R.id.buttonPrev)
        buttonListRadioStations  = findViewById(R.id.buttonListRadioStations)
        buttonFav1  = findViewById(R.id.buttonFav1)
        buttonFav2 = findViewById(R.id.buttonFav2)
        buttonFav3 = findViewById(R.id.buttonFav3)
        buttonSettings  = findViewById(R.id.buttonSettings)

        progressBar = findViewById(R.id.progressBar)


        // Запускаем корутину для инициализации приложения
        GlobalScope.launch(Dispatchers.Main) {
            Log.d("iAtRadio", "MainActivity onCreate -> into GlobalScope")
            initializeApp()
        }

        Log.d("iAtRadio", "MainActivity onCreate -> end")

    }


    private suspend fun initializeApp(){

        Log.d("iAtRadio", "MainActivity initializeApp -> begin")

        // Получаем данные из Intent
        val currentStation = intent.getParcelableExtra<RadioStation>("currentStation")
        val isPlaying = intent.getBooleanExtra("isPlaying", false)

        // Привязка к сервису плееера
        // Начальная инициализация мастера приложений
        // локализация приложения
        // Проверяем, был ли уже выбран язык ранее
        if (appSettings.language.isEmpty()) {
            Log.d("iAtRadio", "MainActivity начало выбора языка")
            // Если язык не был выбран, показываем диалог для выбора языка
            val selectedLanguage = showLanguageDialogSuspend()
            Log.d("iAtRadio", "MainActivity язык выбран $selectedLanguage")
            setLocale(selectedLanguage)
            appSettings.language=selectedLanguage
            Log.d("iAtRadio", "MainActivity выбран язык ${appSettings.language}")
            appSettings.radioStations = chooseRadioStation(appSettings.language)
            appSettings.currentStation=appSettings.radioStations[appSettings.lastRadioStationIndex]
            saveAppSettings(appSettings)
            statusRadio.text = appSettings.currentStation.name
            setStationNotification(appSettings.currentStation)
        } else {
            // Если язык был выбран ранее, устанавливаем его
            setLocale(appSettings.language)
        }
        // END локализация приложения



        Log.d("iAtRadio", "MainActivity initializeApp -> appSettings.radioStations  = $appSettings.radioStations ")

        // проверка есть права на уведомления
        checkNotificationPermission()

        // проверка есть права на bluetooth устройства
        // в частности для того чтобы при отключении блютус устройства останавливается воспроизведение
        // TODO: сделать более осмысленный запрос объясняющий для чего нужны эти права
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            if (!permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
                requestPermissions(permissions, REQUEST_BLUETOOTH_PERMISSIONS)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN
                    ),
                    REQUEST_BLUETOOTH_PERMISSIONS
                )
            }
        }

        if (!appSettings.isHelpMain){
            // при первом запуске показываем как пользоваться программой
            showHelpOverlay()
            appSettings.isHelpMain=true
            saveAppSettings(appSettings)
        }
        // END Начальная инициализация мастера приложений



        if (appSettings.radioStations.size <= appSettings.lastRadioStationIndex) {
            appSettings.lastRadioStationIndex = 0
        }



        if (appSettings.radioStations.isEmpty()) {
            // Загрузка радиостанций из CSV
            appSettings.radioStations.addAll(
                loadRadioStationsFromRaw(
                    this,
                    R.raw.radio_stations_default
                )
            )

            saveAppSettings(appSettings)
            appSettings.lastRadioStationIndex = 0
            appSettings.currentStation = appSettings.radioStations[0]
            statusRadio.text = appSettings.currentStation.name
            updateUIForStopped()
            stopPlayback()
        } else {

            if (appSettings.currentStation.url != "") {
                // найти в списке радиостанций корректный индекс радиостанции
                appSettings.lastRadioStationIndex =
                    appSettings.radioStations.indexOf(appSettings.currentStation)
                if (appSettings.lastRadioStationIndex == -1) {
                    appSettings.lastRadioStationIndex = 0
                }
            } else {
                appSettings.currentStation =
                    appSettings.radioStations[appSettings.lastRadioStationIndex]
            }

            if (appSettings.isAutoPlayEnabled) {
                statusPlay = MusicStatus.PLAYING
                statusRadio.text = appSettings.currentStation.name
                updateUIForConnected()
                // запуск сервиса
                playStation(appSettings.currentStation)
                // END запуск сервиса
            } else {
                statusRadio.text = appSettings.currentStation.name
                setStationNotification(appSettings.currentStation)

                if(currentStation!=null){
                    // Восстанавливаем состояние UI и логику если нажали на уведовление сервиса
                    updateUI(currentStation, isPlaying)
                } else {
                    updateUIForStopped()
                }
            }
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
            val intent = Intent(this, RadioStationListActivity::class.java).apply {
                val isHelp = appSettings.isHelpList
                putExtra(RadioStationListActivity.ACTION_HELP, isHelp)
            }
            Log.d("iAtRadio", "MainActivity setOnClickListenerWithScreenSaverReset -> $intent")
            intent.putParcelableArrayListExtra("radioStations", ArrayList(appSettings.radioStations))
            listRadioStationLauncher.launch(intent)
        }

        buttonForward.setOnClickListenerWithScreenSaverReset {
            if (appSettings.radioStations.isEmpty()) {
                appSettings.lastRadioStationIndex = 0
                statusRadio.text = getString(R.string.empty_list_stations)
            } else {
                statusPlay = MusicStatus.LOADING
                updateUIForConnected()
                nextPlayback(appSettings.currentStation)
            }

        }

        buttonPrev.setOnClickListenerWithScreenSaverReset {
            if (appSettings.radioStations.isEmpty()) {
                appSettings.lastRadioStationIndex = 0
                statusRadio.text = getString(R.string.empty_list_stations)
            } else {
                statusPlay = MusicStatus.LOADING
                updateUIForConnected()
                prevPlayback(appSettings.currentStation)
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
                statusRadio.text = getString(R.string.empty_list_stations)
                appSettings.currentStation = RadioStation("empty", "empty")
                statusPlay = MusicStatus.STOPPED
                updateUIForStopped()
                stopPlayback()
                Log.d("iAtRadio", "MainActivity buttonPlay -> empty $appSettings.currentStation")

            } else {

                when (statusPlay) {
                    MusicStatus.STOPPED -> {
                        // Логика для состояния "остановлена"
                        statusPlay = MusicStatus.PLAYING
                        statusRadio.text = appSettings.currentStation.name
                        Log.d("iAtRadio", "MainActivity buttonPlay -> press Play")
                        updateUIForConnected()
                        playStation(appSettings.currentStation)
                    }
                    MusicStatus.LOADING -> {
                        // Логика для состояния "загружается"
                        appSettings.currentStation = appSettings.radioStations[appSettings.lastRadioStationIndex]
                        statusRadio.text = appSettings.currentStation.name
                        updateUIForStopped()
                        stopPlayback()
                        statusPlay = MusicStatus.STOPPED
                        Log.d("iAtRadio", "MainActivity buttonPlay -> press Stop")
                    }
                    MusicStatus.PLAYING -> {
                        // Логика для состояния "воспроизводится"
                        appSettings.currentStation = appSettings.radioStations[appSettings.lastRadioStationIndex]
                        statusRadio.text = appSettings.currentStation.name
                        updateUIForStopped()
                        stopPlayback()
                        statusPlay = MusicStatus.STOPPED
                        Log.d("iAtRadio", "MainActivity buttonPlay -> press Stop")
                    }
                }
            }
        }

        if(isPlaying){
            updateUIForPlaying()
        } else {
            updateUIForStopped()
        }

        Log.d("iAtRadio", "MainActivity initializeApp -> end")
    }


    private fun updateUIForPlaying() {
        Log.d("iAtRadio", "MainActivity -> updateUIForPlaying")
        statusRadio.setTextColor(ContextCompat.getColor(this, R.color.play))
//        buttonPlay.setImageResource(R.drawable.stop_64)

        // Определяем активен ли ночной режим
        val isNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
//        val expandedView = RemoteViews(packageName, R.layout.notification_expanded)
        if (isNightMode) buttonPlay.setImageResource(R.drawable.stop_64_dark) else buttonPlay.setImageResource(R.drawable.stop_64)
//        expandedView.setImageViewResource(R.id.buttonPlay, buttonIcon)
    }

    private fun updateUIForStopped() {
        Log.d("iAtRadio", "MainActivity -> updateUIForStopped")
        statusRadio.setTextColor(ContextCompat.getColor(this, R.color.stop))
//        buttonPlay.setImageResource(R.drawable.play_64)

        // Определяем активен ли ночной режим
        val isNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
//        val expandedView = RemoteViews(packageName, R.layout.notification_expanded)
        if (isNightMode) buttonPlay.setImageResource(R.drawable.play_64_dark) else buttonPlay.setImageResource(R.drawable.play_64)
//        expandedView.setImageViewResource(R.id.buttonPlay, buttonIcon)

    }

    // статус загрузки музыки
    private fun updateUIForConnected() {
        Log.d("iAtRadio", "MainActivity -> updateUIForConnected")
        statusRadio.setTextColor(ContextCompat.getColor(this, R.color.play))
        buttonPlay.setImageResource(R.drawable.connect64)
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
            statusPlay = MusicStatus.PLAYING
            appSettings.lastRadioStationIndex = appSettings.radioStations.indexOf(it)
            statusRadio.text = it.name
            appSettings.currentStation= it
            saveAppSettings(appSettings)
            pausePlayback()
            updateUIForConnected()
            playStation(appSettings.currentStation)

        } ?: Toast.makeText(this, getString(R.string.no_station_saved_to_favorite) + " ${favIndex + 1}", Toast.LENGTH_SHORT).show()
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
        Log.d("iAtRadio", "MainActivity -> onPause -> statusPlay = $statusPlay")
        stopScreenSaver() // Остановить скринсейвер
    }

    override fun onResume() {
        super.onResume()
        Log.d("iAtRadio", "MainActivity -> onResume")
        if (appSettings.isScreenSaverEnabled) {
            resetTimers() // Сбрасываем таймеры только если скринсейвер разрешен
        }
        statusPlayFromService(appSettings.currentStation)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                // Обработка нажатия кнопки увеличения громкости
                volumeControl.increaseVolume()
                if (appSettings.isScreenSaverEnabled) {
                    stopScreenSaver()
                }
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                // Обработка нажатия кнопки уменьшения громкости
                volumeControl.decreaseVolume()
                if (appSettings.isScreenSaverEnabled) {
                    stopScreenSaver()
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
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

        // Если текст изначально выходит за границы, устанавливаем его в допустимые пределы
        var currentX = radioText.translationX
        var currentY = radioText.translationY

        // Проверяем начальное положение по оси X
        if (currentX < 0) {
            currentX = 0f // Ставим текст на левую границу
        } else if (currentX + textWidth > parentWidth) {
            currentX = (parentWidth - textWidth).toFloat() // Ставим текст на правую границу
        }

        // Проверяем начальное положение по оси Y
        if (currentY < 0) {
            currentY = 0f // Ставим текст на верхнюю границу
        } else if (currentY + textHeight > parentHeight) {
            currentY = (parentHeight - textHeight).toFloat() // Ставим текст на нижнюю границу
        }

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


    private suspend fun showLanguageDialogSuspend(): String = suspendCancellableCoroutine { continuation ->
        val languages = arrayOf("English", "Татарча", "Башҡортса", "Русский")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select the language app")
        builder.setSingleChoiceItems(languages, -1) { dialog, which ->
            val selectedLanguage = when (which) {
                0 -> "en"
                1 -> "tt"
                2 -> "ba"
                3 -> "ru"
                else -> "en" // По умолчанию английский
            }
            dialog.dismiss()
            continuation.resume(selectedLanguage)
        }
        builder.setCancelable(false) // Запрещаем закрытие диалога без выбора
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
        stopPlayback()
    }


    override fun onStart() {
        super.onStart()
        Log.d("iAtRadio", "MainActivity -> onStart")
        // Регистрируем BroadcastReceiver для получения ошибок от сервиса
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(errorReceiver, IntentFilter(RadioNotificationService.ACTION_ERROR))

        // Регистрируем BroadcastReceiver для получения информации от сервиса
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(infoReceiver, IntentFilter(RadioNotificationService.ACTION_INFO))
    }

    override fun onStop() {
        super.onStop()
        // Отмена регистрации BroadcastReceiver при уничтожении активности
        LocalBroadcastManager.getInstance(this).unregisterReceiver(errorReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(infoReceiver)
    }



    // упраление проигрыванием станций

    // передаем текущую радиостанцию в сервис
    private fun setStationNotification(station: RadioStation) {
        val intent = Intent(this, RadioNotificationService::class.java).apply {
            action = RadioNotificationService.ACTION_CURRENT_STATION
            putExtra(RadioNotificationService.EXTRA_STATION, station)
        }
        Log.d("iAtRadio", "MainActivity setStationNotification -> $intent")
        startService(intent)
    }


    private fun playStation(station: RadioStation) {
        val intent = Intent(this, RadioNotificationService::class.java).apply {
            action = RadioNotificationService.ACTION_PLAY
            putExtra(RadioNotificationService.EXTRA_STATION, station)
        }
        Log.d("iAtRadio", "MainActivity playStation -> $intent")
        startService(intent)
    }

    private fun stopPlayback() {
        val intent = Intent(this, RadioNotificationService::class.java).apply {
            action = RadioNotificationService.ACTION_STOP
        }
        Log.d("iAtRadio", "MainActivity stopPlayback -> $intent")
        startService(intent)
    }

    // вызывается когда надо остановить проигрывание текущей станции перед запуском следующей станции
    private fun pausePlayback() {
        val intent = Intent(this, RadioNotificationService::class.java).apply {
            action = RadioNotificationService.ACTION_PAUSE
        }
        Log.d("iAtRadio", "MainActivity pausePlayback -> $intent")
        startService(intent)
    }

    private fun statusPlayFromService(station: RadioStation) {
        val intent = Intent(this, RadioNotificationService::class.java).apply {
            action = RadioNotificationService.ACTION_INFO
            putExtra(RadioNotificationService.EXTRA_STATION, station)
        }
        Log.d("iAtRadio", "MainActivity statusPlayFromService -> $intent")
        startService(intent)
    }

    private fun nextPlayback(station: RadioStation) {
        val intent = Intent(this, RadioNotificationService::class.java).apply {
            action = RadioNotificationService.ACTION_NEXT
            putExtra(RadioNotificationService.EXTRA_STATION, station)
        }
        Log.d("iAtRadio", "MainActivity nextPlayback -> $intent")
        startService(intent)
    }

    private fun prevPlayback(station: RadioStation) {
        val intent = Intent(this, RadioNotificationService::class.java).apply {
            action = RadioNotificationService.ACTION_PREVIOUS
            putExtra(RadioNotificationService.EXTRA_STATION, station)
        }
        Log.d("iAtRadio", "MainActivity prevPlayback -> $intent")
        startService(intent)
    }


    // END упраление проигрыванием станций

    // запрос разрешения на уведомления от приложения
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission is already granted, proceed with your app logic
                    Log.d("iAtRadio", "MainActivity -> Notification permission already granted")
                }
                shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show an explanation to the user before requesting the permission
                    showPermissionRationaleDialog()
                }
                else -> {
                    // Request the permission
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // For Android versions below 13, notification permission is granted by default
            Log.d("iAtRadio", "MainActivity -> Notification permission not required for this Android version")
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_showPermissionRationaleDialog))
            .setMessage(getString(R.string.message_showPermissionRationaleDialog))
            .setPositiveButton(getString(R.string.positive_showPermissionRationaleDialog)) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_showPermissionDeniedDialog))
            .setMessage(getString(R.string.message_showPermissionDeniedDialog))
            .setPositiveButton(getString(R.string.positive_showPermissionDeniedDialog)) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(this)
        }
    }
    // END запрос разрешения на уведомления от приложения

    private fun updateUI(station: RadioStation?, isPlaying: Boolean) {
        Log.d("iAtRadio", "MainActivity ->  updateUI -> station = $station  ->  isPlaying = $isPlaying")
        // Обновите UI и логику на основе переданных данных
        if(station != null) {
            if(isPlaying){
                statusPlay = MusicStatus.PLAYING
                updateUIForPlaying()
            } else {
                statusPlay = MusicStatus.STOPPED
                updateUIForStopped()
            }
        }
    }

    // обработка действий после запроса на права блютус устройств
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d("iAtRadio", "MainActivity ->  onRequestPermissionsResult")

        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Разрешения предоставлены, можно выполнять необходимые действия
            } else {
                // Разрешения не предоставлены, уведомить пользователя
                Toast.makeText(this, "Bluetooth permissions are required for this feature", Toast.LENGTH_LONG).show()
            }
        }
    }

    // обработка смены ориентации активити
     override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Обработка смены ориентации на ландшафтную
            Log.d("iAtRadio", "MainActivity -> Ландшафтный режим")
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            // Обработка смены ориентации на портретную
            Log.d("iAtRadio", "MainActivity -> Портретный режим")
        }
    }

    // мастер выбора радистанций при первом запуске программы
    private suspend fun chooseRadioStation(language: String): MutableList<RadioStation> = suspendCancellableCoroutine { continuation ->
        val baseFolder = if (language == "en") "en" else "ru"

        val categories = mapOf(
            getString(R.string.category_tatar) to "$baseFolder/radio_stations_tatar.csv",
            getString(R.string.category_classic) to "$baseFolder/radio_stations_classic.csv",
            getString(R.string.category_retro) to "$baseFolder/radio_stations_retro.csv",
            getString(R.string.category_russian) to "$baseFolder/radio_stations_rus.csv",
            getString(R.string.category_other) to "$baseFolder/radio_stations_other.csv"
        )

        val categoryNames = categories.keys.toTypedArray()
        val checkedItems = BooleanArray(categoryNames.size) { true } // Все категории выбраны по умолчанию
        val selectedCategories = categoryNames.toMutableList() // Все категории в списке по умолчанию

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_category))
            .setMultiChoiceItems(categoryNames, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    selectedCategories.add(categoryNames[which])
                } else {
                    selectedCategories.remove(categoryNames[which])
                }
            }
            .setPositiveButton(getString(R.string.dialog_button_ok)) { _, _ ->
                val combinedStations = combineSelectedFiles(selectedCategories, categories)
                appSettings.radioStations = combinedStations
                continuation.resume(combinedStations)
            }
            .setOnCancelListener {
                continuation.resume(mutableListOf())
            }
            .show()
    }


    @SuppressLint("DiscouragedApi")
    private fun combineSelectedFiles(selectedCategories: List<String>, categories: Map<String, String>): MutableList<RadioStation> {
        val combinedData = StringBuilder()

        for (category in selectedCategories) {
            val fileName = categories[category]
            if (fileName != null) {
                try {
                    // Используем AssetManager для чтения файла
                    val inputStream = assets.open(fileName)
                    val lines = inputStream.bufferedReader().readLines()

                    // Исключаем первую строку
                    if (lines.size > 1) {
                        combinedData.append(lines.drop(1).joinToString("\n")).append("\n")
                    }
                } catch (e: IOException) {
                    Log.e("iAtRadio", "combineSelectedFiles -> Не удалось открыть файл: $fileName", e)
                }
            }
        }

        Log.d("iAtRadio", "MainActivity -> combineSelectedFiles -> $combinedData")

        // После объединения файлов можно загружать их в программу
        return loadDataToApp(combinedData.toString())
    }



    private fun loadDataToApp(data: String): MutableList<RadioStation> {
        val radioStations = mutableListOf<RadioStation>()

        // Разбиваем данные на строки (каждая строка — это радиостанция)
        val lines = data.split("\n").filter { it.isNotBlank() }

        // Обрабатываем каждую строку, предполагая, что данные разделены точкой с запятой
        for (line in lines) {
            val tokens = line.split(";")  // Используем ';' как разделитель
            if (tokens.size >= 2) {
                val name = tokens[0].trim()   // Первое поле - имя станции
                val url = tokens[1].trim()    // Второе поле - URL станции

                // Создаем объект RadioStation и добавляем его в список
                val station = RadioStation(name, url)
                radioStations.add(station)
            }
        }

        return radioStations
    }

    // помощь по работе с программой
    private fun showHelpOverlay() {
        TapTargetView.showFor(this,
            TapTarget.forView(findViewById(R.id.buttonPlay), getString(R.string.help_player_control_title), getString(R.string.help_player_control_description))
                .outerCircleColor(R.color.darkForHelp)
                .targetCircleColor(R.color.white)
                .textColor(R.color.white)
                .cancelable(true)
                .tintTarget(true),
            object : TapTargetView.Listener() {
                override fun onTargetClick(view: TapTargetView) {
                    super.onTargetClick(view)
                    showPrevControlsHelp()
                }
            })
    }

    private fun showPrevControlsHelp() {
        TapTargetView.showFor(this,
            TapTarget.forView(findViewById(R.id.buttonPrev), getString(R.string.help_prev_station_title), getString(R.string.help_prev_station_description))
                .outerCircleColor(R.color.darkForHelp)
                .targetCircleColor(R.color.white)
                .textColor(R.color.white)
                .cancelable(false)
                .tintTarget(true),
            object : TapTargetView.Listener() {
                override fun onTargetClick(view: TapTargetView) {
                    super.onTargetClick(view)
                    showNextControlsHelp()
                }
            })
    }

    private fun showNextControlsHelp() {
        TapTargetView.showFor(this,
            TapTarget.forView(findViewById(R.id.buttonForward), getString(R.string.help_next_station_title), getString(R.string.help_next_station_description))
                .outerCircleColor(R.color.darkForHelp)
                .targetCircleColor(R.color.white)
                .textColor(R.color.white)
                .cancelable(false)
                .tintTarget(true),
            object : TapTargetView.Listener() {
                override fun onTargetClick(view: TapTargetView) {
                    super.onTargetClick(view)
                    showVolumeControlsHelp()
                }
            })
    }

    private fun showVolumeControlsHelp() {
        TapTargetView.showFor(this,
            TapTarget.forView(findViewById(R.id.buttonVolumeUp), getString(R.string.help_volume_control_title), getString(R.string.help_volume_control_description))
                .outerCircleColor(R.color.darkForHelp)
                .targetCircleColor(R.color.white)
                .textColor(R.color.white)
                .cancelable(false)
                .tintTarget(true),
            object : TapTargetView.Listener() {
                override fun onTargetClick(view: TapTargetView) {
                    super.onTargetClick(view)
                    showNameStationControlsHelp()
                }
            })
    }

    private fun showNameStationControlsHelp() {
        TapTargetView.showFor(this,
            TapTarget.forView(findViewById(R.id.statusRadio), getString(R.string.help_station_name_title), getString(R.string.help_station_name_description))
                .outerCircleColor(R.color.darkForHelp)
                .targetCircleColor(R.color.white)
                .textColor(R.color.white)
                .cancelable(false)
                .tintTarget(true),
            object : TapTargetView.Listener() {
                override fun onTargetClick(view: TapTargetView) {
                    super.onTargetClick(view)
                    showFavoriteControlsHelp()
                }
            })
    }

    private fun showFavoriteControlsHelp() {
        TapTargetView.showFor(this,
            TapTarget.forView(findViewById(R.id.buttonFav1), getString(R.string.help_favorite_station_title), getString(R.string.help_favorite_station_description))
                .outerCircleColor(R.color.darkForHelp)
                .targetCircleColor(R.color.white)
                .textColor(R.color.white)
                .cancelable(false)
                .tintTarget(true),
            object : TapTargetView.Listener() {
                override fun onTargetClick(view: TapTargetView) {
                    super.onTargetClick(view)
                    showListStationControlsHelp()
                }
            })
    }

    private fun showListStationControlsHelp() {
        TapTargetView.showFor(this,
            TapTarget.forView(findViewById(R.id.buttonListRadioStations), getString(R.string.help_list_station_title), getString(R.string.help_list_station_description))
                .outerCircleColor(R.color.darkForHelp)
                .targetCircleColor(R.color.white)
                .textColor(R.color.white)
                .cancelable(false)
                .tintTarget(true),
            object : TapTargetView.Listener() {
                override fun onTargetClick(view: TapTargetView) {
                    super.onTargetClick(view)
                    showSettingsControlsHelp()
                }
            })
    }

    private fun showSettingsControlsHelp() {
        TapTargetView.showFor(this,
            TapTarget.forView(findViewById(R.id.buttonSettings), getString(R.string.help_settings_title), getString(R.string.help_settings_description))
                .outerCircleColor(R.color.darkForHelp)
                .targetCircleColor(R.color.white)
                .textColor(R.color.white)
                .cancelable(false)
                .tintTarget(true),
            object : TapTargetView.Listener() {
                override fun onTargetClick(view: TapTargetView) {
                    super.onTargetClick(view)
                }
            })
    }



// END помощь по работе с программой


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

