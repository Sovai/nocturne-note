package com.example.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Standard Android media session/notification for play/pause/skip/stop while
 * screen is locked. Never stores text/audio in notification extras.
 */
class SpeechPlaybackService : Service() {
  companion object {
    private const val CHANNEL = "nocturne_read_aloud"
    private const val NOTIFICATION_ID = 3401
    const val ACTION_TOGGLE = "com.sovai.nocturnenote.TOGGLE_READING"
    const val ACTION_NEXT = "com.sovai.nocturnenote.NEXT_READING"
    const val ACTION_PREVIOUS = "com.sovai.nocturnenote.PREVIOUS_READING"
    const val ACTION_STOP = "com.sovai.nocturnenote.STOP_READING"
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var session: MediaSession
  private lateinit var manager: NotificationManager

  override fun onCreate() {
    super.onCreate()
    SpeechRuntime.initialize(applicationContext)
    manager = getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      manager.createNotificationChannel(NotificationChannel(CHANNEL, "Read Aloud",
        NotificationManager.IMPORTANCE_LOW))
    }
    session = MediaSession(this, "Nocturne Note reader").apply {
      setCallback(object : MediaSession.Callback() {
        override fun onPlay() = SpeechRuntime.toggle()
        override fun onPause() { SpeechRuntime.controller.pauseForFocus() }
        override fun onSkipToNext() = SpeechRuntime.skip(1)
        override fun onSkipToPrevious() = SpeechRuntime.skip(-1)
        override fun onStop() = SpeechRuntime.stop()
      })
      isActive = true
    }
    scope.launch {
      SpeechRuntime.controller.state.collectLatest { state ->
        if (!state.active) {
          SpeechRuntime.releaseFocus()
          stopForeground(STOP_FOREGROUND_REMOVE)
          stopSelf()
        } else {
          val code = if (state.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
          session.setPlaybackState(PlaybackState.Builder().setState(code,
            PlaybackState.PLAYBACK_POSITION_UNKNOWN, state.speed)
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
              PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
              PlaybackState.ACTION_STOP).build())
          manager.notify(NOTIFICATION_ID, notification(state))
        }
      }
    }
  }

  private fun action(name: String, id: Int): PendingIntent =
    PendingIntent.getService(this, id, Intent(this, SpeechPlaybackService::class.java).setAction(name),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

  private fun notification(state: ReadAloudState): Notification {
    val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      Notification.Builder(this, CHANNEL) else {
      @Suppress("DEPRECATION")
      Notification.Builder(this)
    }
    builder.setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Nocturne Note · Read Aloud")
      .setContentText(if (state.loading) "Preparing speech" else
        state.engine + " · " + (state.paragraph + 1) + "/" + state.totalParagraphs)
      .setContentIntent(open)
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .setOnlyAlertOnce(true)
      .setOngoing(state.playing)
      .addAction(android.R.drawable.ic_media_previous, "Previous", action(ACTION_PREVIOUS, 2))
      .addAction(if (state.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        if (state.playing) "Pause" else "Play", action(ACTION_TOGGLE, 3))
      .addAction(android.R.drawable.ic_media_next, "Next", action(ACTION_NEXT, 4))
      .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken)
        .setShowActionsInCompactView(0, 1, 2))
    return builder.build()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    SpeechRuntime.initialize(applicationContext)
    when (intent?.action) {
      ACTION_TOGGLE -> SpeechRuntime.toggle()
      ACTION_NEXT -> SpeechRuntime.skip(1)
      ACTION_PREVIOUS -> SpeechRuntime.skip(-1)
      ACTION_STOP -> { SpeechRuntime.stop(); return START_NOT_STICKY }
    }
    if (!SpeechRuntime.controller.state.value.active) {
      stopSelf()
      return START_NOT_STICKY
    }
    startForeground(NOTIFICATION_ID, notification(SpeechRuntime.controller.state.value))
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    scope.cancel()
    session.release()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
