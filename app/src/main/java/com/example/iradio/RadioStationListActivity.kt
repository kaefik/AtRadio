package com.example.iradio

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.app.Activity

class RadioStationListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio_station_list)

        // Получение списка радиостанций из Intent
        val radioStations: List<RadioStation>? = intent.getParcelableArrayListExtra("radioStations")

        // Преобразование списка радиостанций в список имен для отображения
        val stationNames = radioStations?.map { it.name } ?: listOf()

        // Получение ListView и установка адаптера для отображения списка радиостанций
        val listView: ListView = findViewById(R.id.listViewRadioStations)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, stationNames)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            // Действие при выборе радиостанции из списка
            val selectedStation = radioStations?.get(position)
            if (selectedStation != null) {
                val resultIntent = Intent()
                resultIntent.putExtra("selectedStation", selectedStation)
                setResult(Activity.RESULT_OK, resultIntent)
                finish() // Закрытие активности после выбора станции
            }
        }

    }
}

