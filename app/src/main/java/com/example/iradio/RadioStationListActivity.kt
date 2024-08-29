package com.example.iradio

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RadioStationListActivity : AppCompatActivity() {

    private lateinit var radioStationAdapter: RadioStationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio_station_list)

        // Получение списка радиостанций из Intent
        val radioStations = intent.getParcelableArrayListExtra<RadioStation>("radioStations")?.toMutableList()
            ?: mutableListOf()

        radioStationAdapter = RadioStationAdapter(this, radioStations) { position ->
            radioStations.removeAt(position)
            radioStationAdapter.notifyItemRemoved(position)
            saveRadioStations(radioStations)
        }

        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewRadioStations)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = radioStationAdapter
    }

    private fun saveRadioStations(radioStations: List<RadioStation>) {
        val sharedPreferences = getSharedPreferences("RadioPreferences", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(radioStations)
        editor.putString("RadioStations", json)
        editor.apply()
    }
}
