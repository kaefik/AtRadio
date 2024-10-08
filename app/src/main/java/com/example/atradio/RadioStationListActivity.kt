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

        radioStationAdapter = RadioStationAdapter(
            this,
            radioStations,
            { position -> showDeleteConfirmationDialog(position, radioStations) },
            { selectedStation ->
                val resultIntent = Intent()
                resultIntent.putExtra("selectedStation", selectedStation)
                resultIntent.putParcelableArrayListExtra("radioStations", ArrayList(radioStations))
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        )

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
                    radioStationAdapter.notifyDataSetChanged()
                    Toast.makeText(this, getString(R.string.radio_station_added), Toast.LENGTH_SHORT).show()
                }
            }
        }

        val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                val newStations = importRadioStationsFromFileUri(this, it)

                if (newStations.isEmpty()) {
                    Toast.makeText(this, getString(R.string.file_no_new_stations), Toast.LENGTH_SHORT).show()
                } else {
                    radioStations.clear()
                    radioStations.addAll(newStations)
                    radioStationAdapter.notifyDataSetChanged()
                    Toast.makeText(this, getString(R.string.radio_stations_imported), Toast.LENGTH_SHORT).show()
                }
            }
        }

        buttonBack.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putParcelableArrayListExtra("radioStations", ArrayList(radioStations))
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        buttonSaveShareStations.setOnClickListener {
            saveAndShareRadioStations(this, "radio_stations.csv", radioStations)
        }

        buttonImportStationsFromFile.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("*/*"))
        }

        buttonAddStation.setOnClickListener {
            val intent = Intent(this, AddRadioStationActivity::class.java)
            addRadioStationLauncher.launch(intent)
        }
    }

    private fun showDeleteConfirmationDialog(position: Int, radioStations: MutableList<RadioStation>) {
        val alertDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.radio_deletion_title))
            .setMessage(getString(R.string.radio_deletion_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                radioStations.removeAt(position)
                radioStationAdapter.notifyItemRemoved(position)
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        alertDialog.show()
    }

    private fun saveAndShareRadioStations(context: Context, fileName: String, radioStations: MutableList<RadioStation>) {
        try {
            val file = File(context.filesDir, fileName)
            val writer = OutputStreamWriter(file.outputStream())

            writer.write("Name;URL\n")

            radioStations.forEach { station ->
                val csvLine = "${station.name};${station.url}\n"
                writer.write(csvLine)
            }

            writer.close()

            val fileUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, getString(R.string.share_radio_stations)))

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun importRadioStationsFromFileUri(context: Context, uri: Uri): List<RadioStation> {
        val radioStations = mutableListOf<RadioStation>()
        var isValidFile = true

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))

                val headerLine = reader.readLine()
                if (headerLine == null || !headerLine.equals("Name;URL", ignoreCase = true)) {
                    isValidFile = false
                    Toast.makeText(context, getString(R.string.invalid_file_format), Toast.LENGTH_SHORT).show()
                    return emptyList()
                }

                reader.forEachLine { line ->
                    if (line.isNotBlank()) {
                        val columns = line.split(";")
                        if (columns.size == 2) {
                            val name = columns[0].trim()
                            val url = columns[1].trim()

                            if (name.isNotEmpty() && url.isNotEmpty()) {
                                radioStations.add(RadioStation(name, url))
                            } else {
//                                isValidFile = false
                                Toast.makeText(context, getString(R.string.incorrect_data, line), Toast.LENGTH_SHORT).show()
                            }
                        } else {
//                            isValidFile = false
                            Toast.makeText(context, getString(R.string.incorrect_line, line), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, getString(R.string.error_reading_file), Toast.LENGTH_SHORT).show()
        }

        return if (isValidFile) radioStations else emptyList()
    }
}
