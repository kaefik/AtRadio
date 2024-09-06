package com.example.atradio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var autoPlaySwitch: SwitchCompat
    private lateinit var screenSaverSwitch: SwitchCompat
    private lateinit var appSettings: AppSettings
    private lateinit var buttonResetAllSettings: Button
    private lateinit var buttonBack: ImageButton
    private lateinit var languageSpinner: Spinner
    private val gson = Gson()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        autoPlaySwitch = findViewById(R.id.autoPlaySwitch)
        screenSaverSwitch = findViewById(R.id.screenSaverSwitch)
        buttonResetAllSettings = findViewById(R.id.buttonResetAllSettings)
        buttonBack = findViewById(R.id.buttonBack)
        languageSpinner = findViewById(R.id.languageSpinner)

        // Загрузка настроек, если их нет, то использование настроек по умолчанию
        appSettings = loadAppSettings()

//        setLocale(appSettings.language)

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
            appSettings = initAppSettings(this)
            refreshSettings()
            showInfoDialogResetSettings()
            saveAppSettings(appSettings)
        }

        buttonBack.setOnClickListener {
            saveAppSettings(appSettings)
            val resultIntent = Intent().apply {
                // Можно добавить данные в Intent, если нужно
                putExtra("key", "value")
            }

            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        // Настройка спиннера выбора языка приложения
        val languages = arrayOf("en", "ru", "tt")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter

        // Обработка изменения выбора языка
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedLanguage = languages[position]
                appSettings.language = selectedLanguage
                saveAppSettings(appSettings)
                // Здесь вы можете добавить логику для применения изменения языка в приложении
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Ничего не делаем
            }
        }
        // END Настройка спиннера выбора языка приложения


        // Установка текущего выбранного языка
        languageSpinner.setSelection(getLanguageIndex(appSettings.language))

        // Обработка изменения состояния переключателя автозапуска
        autoPlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            appSettings.isAutoPlayEnabled = isChecked
            saveAppSettings(appSettings)
        }


    }

    // обновляет переключатели и другие параметры в соответствии с текущими настройками appSettings
    // добавить сюда когда добавляешь новый параметр
    private fun refreshSettings(){
        autoPlaySwitch.isChecked = appSettings.isAutoPlayEnabled
        screenSaverSwitch.isChecked = appSettings.isScreenSaverEnabled
        languageSpinner.setSelection(getLanguageIndex(appSettings.language))
    }

    // Получаем индекс языка для установки в Spinner
    private fun getLanguageIndex(language: String): Int {
        return when (language) {
            "en" -> 0
            "ru" -> 1
            "tt" -> 2
            else -> 0 // По умолчанию English
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
            // TODO: заменить здесь и в MainActivity данный блок единым . пока не придумал каким
            initAppSettings(this)
        }
    }


    private fun showInfoDialogResetSettings() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.dialog_title))
        builder.setMessage(getString(R.string.dialog_message))

        // Настройка кнопки "ОК"
        builder.setPositiveButton(getString(R.string.dialog_button_ok)) { dialog, _ ->
            dialog.dismiss()
        }
        // Создание и отображение диалога
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }
}
