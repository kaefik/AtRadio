package com.example.atradio

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStreamWriter
import android.net.Uri
import android.widget.ImageButton
import java.io.BufferedReader
import java.io.InputStreamReader

class RadioStationListActivity : AppCompatActivity() {

    private lateinit var radioStationAdapter: RadioStationAdapter
//    private lateinit var radioStations: MutableList<RadioStation>

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio_station_list)

        val buttonAddStation: ImageButton = findViewById(R.id.buttonAddStation)
        val buttonBack: ImageButton = findViewById(R.id.buttonBack)
        val buttonSaveShareStations: ImageButton = findViewById(R.id.buttonSaveShareStations)
        val buttonImportStationsFromFile: ImageButton = findViewById(R.id.buttonImportStationsFromFile)


        // Получение списка радиостанций из Intent
        val radioStations: MutableList<RadioStation> = intent.getParcelableArrayListExtra<RadioStation>("radioStations")?.toMutableList()
            ?: mutableListOf()

        radioStationAdapter = RadioStationAdapter(this, radioStations) { position ->
            showDeleteConfirmationDialog(position, radioStations)
        }

        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewRadioStations)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = radioStationAdapter

        // Регистрация callback для обработки результата из AddRadioStationActivity
        val addRadioStationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val name = result.data?.getStringExtra("radioName")
                val url = result.data?.getStringExtra("radioUrl")

                if (name != null && url != null) {
                    val newStation = RadioStation(name, url)
                    radioStations.add(newStation)
//                    saveRadioStations(radioStations)
                    radioStationAdapter.notifyDataSetChanged()  // Уведомляем адаптер об изменении данных
                    Toast.makeText(this, "Radio station added", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Запуск диалога выбора файла
        val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                val newStations = importRadioStationsFromFileUri(this, it)

                if (newStations.size == 0){
                    Toast.makeText(this, "File does not contain new stations", Toast.LENGTH_SHORT).show()
                }else {
                    radioStations.clear()
                    radioStations.addAll(newStations)
                    radioStationAdapter.notifyDataSetChanged()  // Уведомляем адаптер об изменении данных
//                    saveRadioStations(radioStations)
                    Toast.makeText(this, "Radio stations imported", Toast.LENGTH_SHORT).show()
                }
            }
        }

        buttonBack.setOnClickListener {
            // Создаем новый Intent для передачи данных обратно в MainActivity
            val resultIntent = Intent()
            resultIntent.putParcelableArrayListExtra("radioStations", ArrayList(radioStations))

            // Устанавливаем результат для MainActivity
            setResult(Activity.RESULT_OK, resultIntent)

            // Завершаем текущую активность
            finish()
        }


        buttonSaveShareStations.setOnClickListener {
            saveAndShareRadioStations(this, "radio_stations.csv", radioStations)
        }

        buttonImportStationsFromFile.setOnClickListener {

            // Вызываем диалог выбора файла
//            openDocumentLauncher.launch(arrayOf("text/csv"))
            openDocumentLauncher.launch(arrayOf("*/*"))
        }

        // кнопка добавления радиостанции
        buttonAddStation.setOnClickListener{
            val intent = Intent(this, AddRadioStationActivity::class.java)
            addRadioStationLauncher.launch(intent)
        }
    }

    private fun showDeleteConfirmationDialog(position: Int, radioStations: MutableList<RadioStation>) {
        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Radio deletion")
            .setMessage("Are you sure you want to delete this station?")
            .setPositiveButton("Delete") { dialog, which ->
                // Удаляем радиостанцию и уведомляем адаптер об изменении
                radioStations.removeAt(position)
                radioStationAdapter.notifyItemRemoved(position)
//                saveRadioStations(radioStations)
            }
            .setNegativeButton("Cancel") { dialog, which ->
                // Отменяем удаление
                dialog.dismiss()
            }
            .create()

        alertDialog.show()
    }

    // сохранение и отправки файла с помощью механизма поделиться
    fun saveAndShareRadioStations(context: Context, fileName: String, radioStations: MutableList<RadioStation>) {
        try {
            // Сохранение файла в формате CSV
            val file = File(context.filesDir, fileName)
            val writer = OutputStreamWriter(file.outputStream())

            // Заголовок CSV файла
            writer.write("Name;URL\n")

            radioStations.forEach { station ->
                // Формируем строку для CSV
                val csvLine = "${station.name};${station.url}\n"
                writer.write(csvLine)
            }

            writer.close()

            // Получение Uri файла через FileProvider
            val fileUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            // Создание Intent для отправки файла
            val shareIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Запуск активности "Поделиться"
            context.startActivity(Intent.createChooser(shareIntent, "Share Radio Stations"))

        } catch (e: Exception) {
            e.printStackTrace()
            // Обработка ошибок при записи в файл и отправке
        }
    }

    // импорт списка радиостанций из файла в приложение
    fun importRadioStationsFromFileUri(context: Context, uri: Uri): List<RadioStation> {
        val radioStations = mutableListOf<RadioStation>()
        var isValidFile = true

        try {
            // Открываем InputStream из URI
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))

                // Пропускаем первую строку с заголовком
                val headerLine = reader.readLine()
                if (headerLine == null || !headerLine.equals("Name;URL", ignoreCase = true)) {
                    // Проверка заголовка файла
                    isValidFile = false
                    Toast.makeText(context, "Invalid file format", Toast.LENGTH_SHORT).show()
                    return emptyList()
                }

                // Чтение данных из файла
                reader.forEachLine { line ->
                    if (line.isNotBlank()) {
                        val columns = line.split(";")
                        if (columns.size == 2) {
                            val name = columns[0].trim()
                            val url = columns[1].trim()

                            if (name.isNotEmpty() && url.isNotEmpty()) {
                                // Создаем объект RadioStation и добавляем его в список
                                radioStations.add(RadioStation(name, url))
                            } else {
                                isValidFile = false
                                Toast.makeText(context, "Incorrect data in line: $line", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            isValidFile = false
                            Toast.makeText(context, "Incorrect line: $line", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error while reading file", Toast.LENGTH_SHORT).show()
        }

        return if (isValidFile) radioStations else emptyList()
    }



}
