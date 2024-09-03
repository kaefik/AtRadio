package com.example.atradio

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SettingsActivity : AppCompatActivity() {

    private lateinit var autoPlaySwitch: SwitchCompat
    private lateinit var screenSaverSwitch: SwitchCompat
    private lateinit var appSettings: AppSettings
    private lateinit var buttonResetAllSettings: Button
    private lateinit var buttonBack: ImageButton
    private val gson = Gson()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        autoPlaySwitch = findViewById(R.id.autoPlaySwitch)
        screenSaverSwitch = findViewById(R.id.screenSaverSwitch)
        buttonResetAllSettings = findViewById(R.id.buttonResetAllSettings)
        buttonBack = findViewById(R.id.buttonBack)

        // Загрузка настроек, если их нет, то использование настроек по умолчанию
        appSettings = loadAppSettings()

        // Установка состояния переключателя автозапуска на основе загруженных настроек
        refreshSettings()

        // Обработка изменения состояния переключателя
        autoPlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            appSettings.isAutoPlayEnabled = isChecked
            saveAppSettings(appSettings)
        }

        screenSaverSwitch.setOnCheckedChangeListener { _, isChecked ->
            appSettings.isScreenSaverEnabled = isChecked
            saveAppSettings(appSettings)
        }

        buttonResetAllSettings.setOnClickListener {
            appSettings = defaultAppSettings()
            saveAppSettings(appSettings)
            refreshSettings()
        }

        buttonBack.setOnClickListener {
            saveAppSettings(appSettings)
            finish()
        }
    }

    // обновляет переключатели и другие параметры в соответствии с текущими настройками appSettings
    // добавить сюда когда добавляешь новый параметр
    private fun refreshSettings(){
        autoPlaySwitch.isChecked = appSettings.isAutoPlayEnabled
        screenSaverSwitch.isChecked = appSettings.isScreenSaverEnabled
    }

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
            // TODO: заменить здесь и в MainActivity данный блок единым . пока не придумал каким
            defaultAppSettings()
        }
    }

    // настройки по умолчанию
    private fun defaultAppSettings():AppSettings{
        return AppSettings(
            favoriteStations = mutableListOf(null, null, null), // Пустые избранные станции
            isAutoPlayEnabled = false, // Значение по умолчанию
            isScreenSaverEnabled = true, // Значение по умолчанию
            lastRadioStationIndex = 0, // Первая радиостанция в списке
            radioStations = mutableListOf() // Пустой список радиостанций
        )
    }

}
