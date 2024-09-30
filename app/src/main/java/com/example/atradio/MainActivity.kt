package com.example.atradio

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
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
import androidx.core.app.ActivityCompat


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
            val isPlayed = intent?.getBooleanExtra("PLAY", false)
            val stationFromService = intent?.getStringExtra("STATION")
            if(isPlayed == true){
                statusPlay = true
                appSettings = loadAppSettings()
                statusRadio.text = appSettings.currentStation.name
                updateUIForPlaying()
            } else {
                statusPlay = false
                appSettings = loadAppSettings()
                statusRadio.text = appSettings.currentStation.name
                updateUIForStopped()
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
                statusPlay = false
                updateUIForStopped()
            }
        }
    }

    // код запроса разрешения
    private val REQUEST_BLUETOOTH_PERMISSIONS = 1

    private lateinit var volumeControl: VolumeControl
    private lateinit var appSettings: AppSettings
    private val gson = Gson()

    private lateinit var buttonPlay: ImageButton
    private lateinit var statusRadio: TextView
    private lateinit var progressBar: ProgressBar
    private var statusPlay: Boolean? = null // статус проигрывания текущей станции

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

        // Получаем данные из Intent
        val currentStation = intent.getParcelableExtra<RadioStation>("currentStation")
        val isPlaying = intent.getBooleanExtra("isPlaying", false)


        // Загружаем настройки при старте
        appSettings = loadAppSettings()

        // Привязка к сервису плееера


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
                statusPlay = true
                statusRadio.text = appSettings.currentStation.name
                updateUIForPlaying()
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
                    stopPlayback()
                    statusPlay = false
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
                    statusPlay = true
                    appSettings.currentStation = selectedStation
                    statusRadio.text = appSettings.currentStation.name
                    saveAppSettings(appSettings)
                    updateUIForPlaying()
                    // запуск сервера
                    stopPlayback()
                    playStation(appSettings.currentStation)
                    // END запуск сервера
                }
            }
        }

        val settingAppLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                appSettings = loadAppSettings() // Перезагружаем все настройки
                setLocale(appSettings.language)
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
                statusRadio.text = "Empty list stations"
            } else {
                statusPlay = true
                updateUIForPlaying()
//                appSettings.lastRadioStationIndex += 1
//                if (appSettings.radioStations.size <= appSettings.lastRadioStationIndex)
//                    appSettings.lastRadioStationIndex = 0
//                appSettings.currentStation = appSettings.radioStations[appSettings.lastRadioStationIndex]
//                statusRadio.text = appSettings.currentStation.name
//                saveAppSettings(appSettings)
                nextPlayback(appSettings.currentStation)
            }

        }

        buttonPrev.setOnClickListenerWithScreenSaverReset {
            if (appSettings.radioStations.isEmpty()) {
                appSettings.lastRadioStationIndex = 0
                statusRadio.text = "Empty list stations"
            } else {
                statusPlay = true
                updateUIForPlaying()
                appSettings.lastRadioStationIndex -= 1
                if (appSettings.lastRadioStationIndex < 0)
                    appSettings.lastRadioStationIndex = appSettings.radioStations.size - 1
                appSettings.currentStation = appSettings.radioStations[appSettings.lastRadioStationIndex]
                statusRadio.text = appSettings.currentStation.name
                saveAppSettings(appSettings)
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
//            stopPlayback()
            if (appSettings.radioStations.isEmpty()) {
                appSettings.lastRadioStationIndex = 0
                statusRadio.text = "Empty list stations"
                appSettings.currentStation = RadioStation("empty", "empty")
                statusPlay = false
                updateUIForStopped()
                stopPlayback()
                Log.d("iAtRadio", "MainActivity buttonPlay -> empty $appSettings.currentStation")

            } else {
                if (statusPlay == true) {
                    appSettings.currentStation = appSettings.radioStations[appSettings.lastRadioStationIndex]
                    statusRadio.text = appSettings.currentStation.name
                    updateUIForStopped()
                    stopPlayback()
                    statusPlay = false
                    Log.d("iAtRadio", "MainActivity buttonPlay -> press Stop")
                } else {
                    // возможно здесь appSettings.currentStation удалить
                    statusPlay = true
                    updateUIForPlaying()
                    statusRadio.text = appSettings.currentStation.name
                    Log.d("iAtRadio", "MainActivity buttonPlay -> press Play")
                    playStation(appSettings.currentStation)
                }
            }
        }

        Log.d("iAtRadio", "MainActivity onCreate -> end")

    }

    private fun updateUIForPlaying() {
        Log.d("iAtRadio", "MainActivity -> updateUIForPlaying")
        statusRadio.setTextColor(ContextCompat.getColor(this, R.color.play))
        buttonPlay.setImageResource(R.drawable.stop_64)
    }

    private fun updateUIForStopped() {
        Log.d("iAtRadio", "MainActivity -> updateUIForStopped")
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
            statusPlay = true
            appSettings.lastRadioStationIndex = appSettings.radioStations.indexOf(it)
            statusRadio.text = it.name
            appSettings.currentStation= it
            saveAppSettings(appSettings)
            updateUIForPlaying()
            stopPlayback()
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
                statusPlay = true
                updateUIForPlaying()
            } else {
                statusPlay = false
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
//                Toast.makeText(this, "Bluetooth permissions are required for this feature", Toast.LENGTH_LONG).show()
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

