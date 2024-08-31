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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class RadioStationListActivity : AppCompatActivity() {

    private lateinit var radioStationAdapter: RadioStationAdapter

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio_station_list)

        val buttonAddStation: Button = findViewById(R.id.buttonAddStation)
        val buttonBack: Button = findViewById(R.id.buttonBack)


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

    // импорт радиостанций из файла в приложение
    private fun loadRadioStationsFromFile(context: Context, fileName: String): MutableList<RadioStation> {
        val radioStations = mutableListOf<RadioStation>()

        try {
            val inputStream = context.assets.open(fileName)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))

            bufferedReader.useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotEmpty() && line != "{\"Name\":\"\",\"URL\":\"\",\"File\":\"\",\"Port\":\"0\",\"ovol\":\"0\"}") {
                        try {
                            val jsonObject = JSONObject(line)
                            val name = jsonObject.optString("Name", "").trim()
                            val url = jsonObject.optString("URL", "").trim()
                            val file = jsonObject.optString("File", "").trim()
                            val port = jsonObject.optString("Port", "").trim()
                            val ovol = jsonObject.optString("ovol", "").trim()

                            // Формирование URL с учетом файла и порта
                            val fullUrl = "http://$url:$port$file"

                            if (name.isNotEmpty() && fullUrl.isNotEmpty()) {
                                radioStations.add(RadioStation(name, fullUrl))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // Игнорируем строки, которые не удалось разобрать
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Обработка ошибок при открытии файла
        }

        return radioStations
    }

    // сохранение списка радиостанций в файл 
    fun saveRadioStationsToFile(context: Context, fileName: String, radioStations: MutableList<RadioStation>) {
        try {
            val outputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE)
            val writer = OutputStreamWriter(outputStream)

            radioStations.forEach { station ->
                val url = URL(station.url)
                val host = url.host
                val port = url.port.takeIf { it != -1 }?.toString() ?: "0"
                val file = url.path

                val jsonObject = JSONObject().apply {
                    put("Name", station.name)
                    put("URL", host)
                    put("File", file)
                    put("Port", port)
                    put("ovol", "0") // Пустое значение, если оно не используется
                }
                writer.write(jsonObject.toString() + "\n")
            }

            writer.close()
        } catch (e: Exception) {
            e.printStackTrace()
            // Обработка ошибок при записи в файл
        }
    }


}
