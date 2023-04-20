package com.example.divx_demo.media

import android.content.Intent
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService: MediaSessionService(), MediaSession.Callback {
    private var mediaSession: MediaSession? = null


     companion object Running {
         var isRunning: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        val player: Player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).setCallback(this).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("DIVX-PlaybackService", "Attempt to start service")
        isRunning = true
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }



    override fun onDestroy() {
        Log.d("DIVX", "Destroying Service")
        Running.isRunning = false
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }


    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        val updatedMediaItems = mediaItems.map {it.buildUpon().setUri(it.mediaId).build()}.toMutableList()
        return Futures.immediateFuture(updatedMediaItems)
//        return super.onAddMediaItems(mediaSession, controller, mediaItems)
    }
}