package com.example.atradio
import android.os.Parcel
import android.os.Parcelable

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Определение радиостанций
data class RadioStation(val name: String, val url: String) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(url)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<RadioStation> {
        override fun createFromParcel(parcel: Parcel): RadioStation {
            return RadioStation(parcel)
        }

        override fun newArray(size: Int): Array<RadioStation?> {
            return arrayOfNulls(size)
        }
    }
}

// Класс для хранения настроек приложения
data class AppSettings(
    var favoriteStations: MutableList<RadioStation?>, // список избранных станций
    var isAutoPlayEnabled: Boolean,    // флаг автозапуска
    var isScreenSaverEnabled: Boolean, // флаг включения скринсейвера
    var lastRadioStationIndex: Int,     // номер последней проигранной станции
    var radioStations: MutableList<RadioStation>, // список станций
    var language: String, // язык интерфейса
    var currentStation: RadioStation, // текущая радиостанция
    var isHelpMain: Boolean, // флаг был ли инструктаж главного окна
    var isHelpList: Boolean, // флаг был ли инструктаж  окна списка станций
    var isFullScreenApp: Boolean, // флаг полноэкранного режима
) : Parcelable {
    constructor(parcel: Parcel) : this(
        mutableListOf<RadioStation?>().apply {
            parcel.readTypedList(this, RadioStation.CREATOR)  // Исправлено для чтения списка радиостанций
        },
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readInt(),
        mutableListOf<RadioStation>().apply {
            parcel.readTypedList(this, RadioStation.CREATOR) // Исправлено для чтения списка радиостанций
        },
        parcel.readString() ?: "en", // чтение языка интерфейса с дефолтным значением
        parcel.readParcelable(RadioStation::class.java.classLoader) ?: RadioStation("", "",), // Чтение текущей станции с дефолтным значением
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte())



    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeTypedList(favoriteStations) // Используем writeTypedList для записи списка
        parcel.writeByte(if (isAutoPlayEnabled) 1 else 0)
        parcel.writeByte(if (isScreenSaverEnabled) 1 else 0)
        parcel.writeInt(lastRadioStationIndex)
        parcel.writeTypedList(radioStations) // Используем writeTypedList для записи списка
        parcel.writeString(language) // Сохраняем язык интерфейса
        parcel.writeParcelable(currentStation, flags) // Запись текущей станции
        parcel.writeByte(if (isHelpMain) 1 else 0)
        parcel.writeByte(if (isHelpList) 1 else 0)
        parcel.writeByte(if (isHelpList) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AppSettings> {
        override fun createFromParcel(parcel: Parcel): AppSettings {
            return AppSettings(parcel)
        }

        override fun newArray(size: Int): Array<AppSettings?> {
            return arrayOfNulls(size)
        }
    }
}


// Функция для загрузки радиостанций из CSV
fun loadRadioStationsFromRaw(context: Context, resourceId: Int): MutableList<RadioStation> {
    val radioStations = mutableListOf<RadioStation>()

    // Открываем файл из папки raw
    context.resources.openRawResource(resourceId).use { inputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line: String?

            // Пропускаем заголовок, если есть
            reader.readLine()

            // Чтение строк из CSV
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    val tokens = it.split(";")
                    if (tokens.size >= 2) {
                        val name = tokens[0]
                        val url = tokens[1]
                        radioStations.add(RadioStation(name, url))
                    }
                }
            }
        }
    }

    return radioStations
}

// Ваша основная активность или место, где вы инициализируете AppSettings
fun initAppSettings(context: Context): AppSettings {
    val appSettings = AppSettings(
        favoriteStations = mutableListOf(null, null, null), // Пустые избранные станции
        isAutoPlayEnabled = false, // Значение по умолчанию
        isScreenSaverEnabled = true, // Значение по умолчанию
        lastRadioStationIndex = 0, // Первая радиостанция в списке
        radioStations = mutableListOf(), // Пустой список радиостанций
        language = "",
        currentStation = RadioStation("", ""), // По умолчанию пустая станция
        isHelpMain = false,
        isHelpList = false,
        isFullScreenApp = true
    )

    // Загрузка радиостанций из CSV
    appSettings.radioStations.clear()
    appSettings.radioStations.addAll(loadRadioStationsFromRaw(context, R.raw.radio_stations_default))

    // Если список радиостанций не пуст, устанавливаем первую станцию как текущую
//    if (appSettings.radioStations.isNotEmpty()) {
    appSettings.currentStation = appSettings.radioStations[appSettings.lastRadioStationIndex]
//    }

    return appSettings
}