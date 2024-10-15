package com.example.atradio

import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    var statusPlay: MusicStatus = MusicStatus.STOPPED  // статус проигрывания текущей станции

    // Вы можете добавить другие переменные и методы, которые понадобятся для управления состоянием
}