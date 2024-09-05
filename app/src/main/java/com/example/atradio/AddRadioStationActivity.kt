package com.example.atradio

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AddRadioStationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_radio_station)

        // Инициализация виджетов
        val editTextRadioName: EditText = findViewById(R.id.editTextRadioName)
        val editTextRadioUrl: EditText = findViewById(R.id.editTextRadioUrl)
        val buttonSave: Button = findViewById(R.id.buttonSaveRadioStation)
        val buttonCancel: Button = findViewById(R.id.buttonCancel)

        // Установка слушателя на кнопку "Сохранить"
        buttonSave.setOnClickListener {
            val name = editTextRadioName.text.toString().trim()
            val url = editTextRadioUrl.text.toString().trim()

            // Проверка, что поля не пустые
            if (name.isNotEmpty() && url.isNotEmpty()) {
                // Подготовка данных для возврата в MainActivity
                if (isValidUrl(url)) {
                    // Подготовка данных для возврата в MainActivity
                    val resultIntent = Intent()
                    resultIntent.putExtra("radioName", name)
                    resultIntent.putExtra("radioUrl", url)
                    // Установка результата и завершение активности
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                } else {
                    Toast.makeText(this, "Please enter the correct URL", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Отображение сообщения об ошибке
                Toast.makeText(this, "Enter the name and URL of the radio station", Toast.LENGTH_SHORT).show()
            }
        }

        buttonCancel.setOnClickListener {
            // Завершить активность без возврата результата
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

    }
}
