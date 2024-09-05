package com.example.atradio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        val activity = context as MainActivity
        val mediaSession = activity.getMediaSession()

        when (action) {
            "ACTION_PLAY" -> {
                Toast.makeText(context, "Play pressed", Toast.LENGTH_SHORT).show()
                mediaSession.controller.transportControls.play()
            }
            "ACTION_PAUSE" -> {
                Toast.makeText(context, "Pause pressed", Toast.LENGTH_SHORT).show()
                mediaSession.controller.transportControls.pause()
            }
            "ACTION_NEXT" -> {
                Toast.makeText(context, "Next pressed", Toast.LENGTH_SHORT).show()
                mediaSession.controller.transportControls.skipToNext()
            }
            "ACTION_PREV" -> {
                Toast.makeText(context, "Previous pressed", Toast.LENGTH_SHORT).show()
                mediaSession.controller.transportControls.skipToPrevious()
            }
        }
    }
}

