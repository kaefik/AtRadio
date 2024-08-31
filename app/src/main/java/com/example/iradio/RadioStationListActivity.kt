package com.example.iradio

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import android.app.AlertDialog
import android.content.Intent
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStreamWriter
import android.net.Uri


class RadioStationListActivity : AppCompatActivity() {

    private lateinit var radioStationAdapter: RadioStationAdapter

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio_station_list)

        val buttonAddStation: Button = findViewById(R.id.buttonAddStation)
        val buttonBack: Button = findViewById(R.id.buttonBack)
        val buttonSaveShareStations: Button = findViewById(R.id.buttonSaveShareStations)


        // Получение списка радиостанций из Intent
        val radioStations = intent.getParcelableArrayListExtra<RadioStation>("radioStations")?.toMutableList()
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
                    saveRadioStations(radioStations)
                    radioStationAdapter.notifyDataSetChanged()  // Уведомляем адаптер об изменении данных
                    Toast.makeText(this, "Радиостанция добавлена", Toast.LENGTH_SHORT).show()
                }
            }
        }

        buttonBack.setOnClickListener {
            // Завершить активность с возвратом результата
            setResult(Activity.RESULT_OK)
            finish()
        }

        buttonSaveShareStations.setOnClickListener {
            saveAndShareRadioStations(this, "radio_stations.csv", radioStations)
        }

        // кнопка добавления радиостанции
        buttonAddStation.setOnClickListener{
            val intent = Intent(this, AddRadioStationActivity::class.java)
            addRadioStationLauncher.launch(intent)
        }
    }

    private fun showDeleteConfirmationDialog(position: Int, radioStations: MutableList<RadioStation>) {
        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Удаление радиостанции")
            .setMessage("Вы уверены, что хотите удалить эту радиостанцию?")
            .setPositiveButton("Удалить") { dialog, which ->
                // Удаляем радиостанцию и уведомляем адаптер об изменении
                radioStations.removeAt(position)
                radioStationAdapter.notifyItemRemoved(position)
                saveRadioStations(radioStations)
            }
            .setNegativeButton("Отмена") { dialog, which ->
                // Отменяем удаление
                dialog.dismiss()
            }
            .create()

        alertDialog.show()
    }

    private fun saveRadioStations(radioStations: List<RadioStation>) {
        val sharedPreferences = getSharedPreferences("RadioPreferences", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(radioStations)
        editor.putString("RadioStations", json)
        editor.apply()
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


}
