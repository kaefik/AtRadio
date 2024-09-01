package com.example.iradio
import android.os.Parcel
import android.os.Parcelable



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
    var favoriteStations: MutableList<RadioStation?>,
    var isAutoPlayEnabled: Boolean,
    var lastRadioStationIndex: Int,
    var radioStations: MutableList<RadioStation>
) : Parcelable {
    constructor(parcel: Parcel) : this(
        mutableListOf<RadioStation?>().apply {
            parcel.readList(this, RadioStation::class.java.classLoader)
        },
        parcel.readByte() != 0.toByte(),
        parcel.readInt(),
        mutableListOf<RadioStation>().apply {
            parcel.readList(this, RadioStation::class.java.classLoader)
        }
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeList(favoriteStations)
        parcel.writeByte(if (isAutoPlayEnabled) 1 else 0)
        parcel.writeInt(lastRadioStationIndex)
        parcel.writeList(radioStations)
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