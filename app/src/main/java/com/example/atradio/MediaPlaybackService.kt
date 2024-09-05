package com.example.atradio

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat

class MediaPlaybackService : Service() {

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(this, "MediaSession")
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession.isActive = true
    }

    fun getMediaSession(): MediaSessionCompat {
        return mediaSession
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            "ACTION_PLAY" -> {
                mediaSession.controller.transportControls.play()
            }
            "ACTION_PAUSE" -> {
                mediaSession.controller.transportControls.pause()
            }
            "ACTION_NEXT" -> {
                mediaSession.controller.transportControls.skipToNext()
            }
            "ACTION_PREV" -> {
                mediaSession.controller.transportControls.skipToPrevious()
            }
        }

        return START_STICKY
    }

}
