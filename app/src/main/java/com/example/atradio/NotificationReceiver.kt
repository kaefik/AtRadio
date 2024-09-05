package com.example.atradio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action

        // Взаимодействие с сервисом через intent
        val mediaPlaybackServiceIntent = Intent(context, MediaPlaybackService::class.java)
        mediaPlaybackServiceIntent.action = action

        when (action) {
            "ACTION_PLAY" -> {
                Toast.makeText(context, "Play pressed", Toast.LENGTH_SHORT).show()
                context.startService(mediaPlaybackServiceIntent)
            }
            "ACTION_PAUSE" -> {
                Toast.makeText(context, "Pause pressed", Toast.LENGTH_SHORT).show()
                context.startService(mediaPlaybackServiceIntent)
            }
            "ACTION_NEXT" -> {
                Toast.makeText(context, "Next pressed", Toast.LENGTH_SHORT).show()
                context.startService(mediaPlaybackServiceIntent)
            }
            "ACTION_PREV" -> {
                Toast.makeText(context, "Previous pressed", Toast.LENGTH_SHORT).show()
                context.startService(mediaPlaybackServiceIntent)
            }
        }
    }
}
