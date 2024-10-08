package com.example.atradio

import android.util.Patterns

// статус воспроизведения музыки
enum class MusicStatus {
    STOPPED,
    LOADING,
    PLAYING
}

// Функция для проверки, является ли текст корректным URL
fun isValidUrl(url: String): Boolean {
    return Patterns.WEB_URL.matcher(url).matches()
}
