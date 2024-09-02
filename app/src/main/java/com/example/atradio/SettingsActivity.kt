package com.example.iradio

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SettingsActivity : AppCompatActivity() {

    private lateinit var autoPlaySwitch: SwitchCompat
    private lateinit var appSettings: AppSettings
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        autoPlaySwitch = findViewById(R.id.autoPlaySwitch)

        // Загрузка настроек, если их нет, то использование настроек по умолчанию
        appSettings = loadAppSettings()

        // Установка состояния переключателя автозапуска на основе загруженных настроек
        autoPlaySwitch.isChecked = appSettings.isAutoPlayEnabled

        // Обработка изменения состояния переключателя
        autoPlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            appSettings.isAutoPlayEnabled = isChecked
            saveAppSettings(appSettings)
        }
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
            AppSettings(
                favoriteStations = mutableListOf(null, null, null), // Пустые избранные станции
                isAutoPlayEnabled = false, // Значение по умолчанию
                lastRadioStationIndex = 0, // Первая радиостанция в списке
                radioStations = mutableListOf() // Пустой список радиостанций
            )
        }
    }

}
