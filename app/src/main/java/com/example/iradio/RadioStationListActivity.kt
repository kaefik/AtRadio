package com.example.iradio

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

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
    }
}


//
//import android.os.Bundle
//import android.widget.ArrayAdapter
//import android.widget.ListView
//import androidx.appcompat.app.AppCompatActivity
//
//class RadioStationListActivity : AppCompatActivity() {
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_radio_station_list)
//
//        // Получаем список радиостанций из intent
//        val radioStations = intent.getSerializableExtra("radioStations") as? ArrayList<RadioStation> ?: arrayListOf()
//
//        // Инициализируем ListView
//        val listView: ListView = findViewById(R.id.listViewRadioStations)
//
//        // Создаем адаптер для отображения списка
//        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, radioStations.map { it.name })
//
//        // Присваиваем адаптер ListView
//        listView.adapter = adapter
//    }
//}
