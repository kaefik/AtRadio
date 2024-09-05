package com.example.atradio

import android.util.Patterns

// Функция для проверки, является ли текст корректным URL
fun isValidUrl(url: String): Boolean {
    return Patterns.WEB_URL.matcher(url).matches()
}
