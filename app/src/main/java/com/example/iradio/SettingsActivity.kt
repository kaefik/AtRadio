package com.example.iradio

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var autoPlaySwitch: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        autoPlaySwitch = findViewById(R.id.autoPlaySwitch)

        // Загрузка сохраненной настройки
        val sharedPreferences = getSharedPreferences("RadioPreferences", MODE_PRIVATE)
        val isAutoPlayEnabled = sharedPreferences.getBoolean("AutoPlayEnabled", false)
        autoPlaySwitch.isChecked = isAutoPlayEnabled

        // Сохранение настройки при изменении
        autoPlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPreferences.edit()
            editor.putBoolean("AutoPlayEnabled", isChecked)
            editor.apply()
        }
    }
}
