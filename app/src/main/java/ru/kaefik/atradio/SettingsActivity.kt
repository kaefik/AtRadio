package ru.kaefik.atradio

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume

class SettingsActivity : AppCompatActivity() {

    private lateinit var autoPlaySwitch: SwitchCompat
    private lateinit var screenSaverSwitch: SwitchCompat
    private lateinit var fullScreenApp: SwitchCompat
    private lateinit var appSettings: ru.kaefik.atradio.AppSettings
//    private lateinit var buttonResetAllSettings: Button
    private lateinit var buttonChooseStations: Button
    private lateinit var buttonBack: ImageButton
    private lateinit var languageSpinner: Spinner
    private val gson = Gson()


    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        autoPlaySwitch = findViewById(R.id.autoPlaySwitch)
        fullScreenApp = findViewById(R.id.fullScreenApp)
        screenSaverSwitch = findViewById(R.id.screenSaverSwitch)
//        buttonResetAllSettings = findViewById(R.id.buttonResetAllSettings)
        buttonChooseStations = findViewById(R.id.buttonChooseStations)
        buttonBack = findViewById(R.id.buttonBack)
        languageSpinner = findViewById(R.id.languageSpinner)

        // Загрузка настроек, если их нет, то использование настроек по умолчанию
        appSettings = loadAppSettings()

//        setLocale(appSettings.language)

        // блок кнопок о проекте

        val githubButton: ImageButton = findViewById(R.id.buttonGitHub)
        githubButton.setOnClickListener {
            val githubUrl = getString(R.string.linkGitHub)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
            startActivity(intent)
        }

        val donateButton: ImageButton = findViewById(R.id.buttonDonate)
        donateButton.setOnClickListener {
            val donateUrl = getString(R.string.linkDonate)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(donateUrl))
            startActivity(intent)
        }

        val emailButton: ImageButton = findViewById(R.id.buttonEmail)
        emailButton.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${getString(R.string.emailAuthor)}")  // Ваш email
                putExtra(Intent.EXTRA_SUBJECT, "AtRadio")  // Тема письма
                putExtra(Intent.EXTRA_TEXT, "Hello! ")  // Текст письма
            }
            startActivity(Intent.createChooser(emailIntent, getString(R.string.choose_email_clients)))
        }

        val aboutButton: ImageButton = findViewById(R.id.buttonAbout)
        aboutButton.setOnClickListener {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.aboutTitle))
            builder.setMessage("${getString(R.string.aboutDialogAlert)} $versionName \n\n ${getString(R.string.aboutAuthor)} \n\n")

            // Кнопка OK для закрытия диалогового окна
            builder.setPositiveButton(getString(R.string.dialog_button_ok)) { dialog, _ ->
                dialog.dismiss()
            }

            // Показать диалог
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }


        // END блок кнопок о проекте


        // Установка состояния переключателя автозапуска на основе загруженных настроек
        refreshSettings()

        // Обработка изменения состояния переключателя
        autoPlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            appSettings.isAutoPlayEnabled = isChecked
            saveAppSettings(appSettings)
        }

        fullScreenApp.setOnCheckedChangeListener { _, isChecked ->
            appSettings.isFullScreenApp = isChecked
            saveAppSettings(appSettings)
        }

        screenSaverSwitch.setOnCheckedChangeListener { _, isChecked ->
            appSettings.isScreenSaverEnabled = isChecked
            saveAppSettings(appSettings)
        }

//        buttonResetAllSettings.setOnClickListener {
//            appSettings = initAppSettings(this)
//            refreshSettings()
//            showInfoDialogResetSettings()
//            GlobalScope.launch(Dispatchers.Main) {
//                appSettings.radioStations = chooseRadioStation(appSettings.language)
//            }
//            saveAppSettings(appSettings)
//        }

        buttonChooseStations.setOnClickListener {
            appSettings=loadAppSettings()
            GlobalScope.launch(Dispatchers.Main) {
                appSettings.radioStations = chooseRadioStation(appSettings.language)
            }
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

        // Настройка спиннера выбора языtка приложения
        val languages = arrayOf("en", "tt", "ba", "ru")
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
        fullScreenApp.isChecked = appSettings.isFullScreenApp
        screenSaverSwitch.isChecked = appSettings.isScreenSaverEnabled
        languageSpinner.setSelection(getLanguageIndex(appSettings.language))
    }

    // Получаем индекс языка для установки в Spinner
    private fun getLanguageIndex(language: String): Int {
        return when (language) {
            "en" -> 0
            "tt" -> 1
            "ba" -> 2
            "ru" -> 3
            else -> 0 // По умолчанию English
        }
    }

    private fun saveAppSettings(settings: ru.kaefik.atradio.AppSettings) {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val json = gson.toJson(settings)
        editor.putString("AppSettingsData", json)
        editor.apply()
    }

    private fun loadAppSettings(): ru.kaefik.atradio.AppSettings {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val json = sharedPreferences.getString("AppSettingsData", null)
        return if (json != null) {
            val type = object : TypeToken<ru.kaefik.atradio.AppSettings>() {}.type
            gson.fromJson(json, type)
        } else {
            // Возвращаем настройки по умолчанию, если они отсутствуют
            // TODO: заменить здесь и в MainActivity данный блок единым . пока не придумал каким
            ru.kaefik.atradio.initAppSettings(this)
        }
    }


//    private fun showInfoDialogResetSettings() {
//        val builder = AlertDialog.Builder(this)
//        builder.setTitle(getString(R.string.dialog_title))
//        builder.setMessage(getString(R.string.dialog_message))
//
//        // Настройка кнопки "ОК"
//        builder.setPositiveButton(getString(R.string.dialog_button_ok)) { dialog, _ ->
//            dialog.dismiss()
//        }
//        // Создание и отображение диалога
//        val dialog: AlertDialog = builder.create()
//        dialog.show()
//    }

    // мастер выбора радистанций при первом запуске программы
    private suspend fun chooseRadioStation(language: String): MutableList<ru.kaefik.atradio.RadioStation> = suspendCancellableCoroutine { continuation ->
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
    private fun combineSelectedFiles(selectedCategories: List<String>, categories: Map<String, String>): MutableList<ru.kaefik.atradio.RadioStation> {
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
    private fun loadDataToApp(data: String): MutableList<ru.kaefik.atradio.RadioStation> {
        val radioStations = mutableListOf<ru.kaefik.atradio.RadioStation>()

        // Разбиваем данные на строки (каждая строка — это радиостанция)
        val lines = data.split("\n").filter { it.isNotBlank() }

        // Обрабатываем каждую строку, предполагая, что данные разделены точкой с запятой
        for (line in lines) {
            val tokens = line.split(";")  // Используем ';' как разделитель
            if (tokens.size >= 2) {
                val name = tokens[0].trim()   // Первое поле - имя станции
                val url = tokens[1].trim()    // Второе поле - URL станции

                // Создаем объект RadioStation и добавляем его в список
                val station = ru.kaefik.atradio.RadioStation(name, url)
                radioStations.add(station)
            }
        }

        return radioStations
    }



}
