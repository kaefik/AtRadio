package com.example.atradio

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RadioStationAdapter(
    private val context: Context,
    private val radioStations: MutableList<RadioStation>,
    private val onDeleteClick: (Int) -> Unit,
    private val onItemClick: (RadioStation) -> Unit // Новый параметр для клика по элементу
) : RecyclerView.Adapter<RadioStationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val radioStationName: TextView = view.findViewById(R.id.radioStationName)
        val buttonDelete: Button = view.findViewById(R.id.buttonDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.radio_station_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val radioStation = radioStations[position]
        holder.radioStationName.text = radioStation.name

        holder.itemView.setOnClickListener {
            onItemClick(radioStation) // Обработка клика на элемент
        }

        holder.buttonDelete.setOnClickListener {
            onDeleteClick(position)
        }
    }

    override fun getItemCount(): Int {
        return radioStations.size
    }
}
