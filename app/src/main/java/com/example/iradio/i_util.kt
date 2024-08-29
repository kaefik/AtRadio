package com.example.iradio

import android.util.Patterns

// Функция для проверки, является ли текст корректным URL
public fun isValidUrl(url: String): Boolean {
    return Patterns.WEB_URL.matcher(url).matches()
}
