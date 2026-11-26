package com.beeze.music.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** 前台播放服务：负责后台播放、媒体通知、播放错误广播与睡眠定时。 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var sleepTimerEndAtMs = 0L
    private var consecutivePlaybackErrors = 0
    /** 瞬态音频错误已原地重试过的媒体项，避免对同一项反复重试 */
    private var retriedMediaId: String? = null

    private val sleepTimerRunnable = Runnable {
        sleepTimerEndAtMs = 0L
        mediaSession?.player?.pause()
        publishSleepTimer()
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SET_SLEEP_TIMER_COMMAND)
                .add(PLAYBACK_ERROR_COMMAND)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setSessionExtras(sleepTimerExtras())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != COMMAND_SET_SLEEP_TIMER) {
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            setSleepTimer(args.getLong(EXTRA_SLEEP_TIMER_END_AT, 0L))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val title = player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
                android.util.Log.w("PlaybackService", "播放失败: $title, code=${error.errorCodeName}", error)
                val mediaId = player.currentMediaItem?.mediaId
                // 音频管线瞬态错误（如 ROM 音效引擎重建输出链路）重试几乎必成功，
                // 原地静默重试一次，仍失败再走提示+跳下一首
                if (isTransientAudioError(error) && mediaId != null && retriedMediaId != mediaId) {
                    retriedMediaId = mediaId
                    player.prepare()
                    player.play()
                    return
                }
                retriedMediaId = null
                mediaSession?.broadcastCustomCommand(
                    PLAYBACK_ERROR_COMMAND,
                    Bundle().apply { putString(EXTRA_SONG_TITLE, title) },
                )
                skipToNextAfterError(player)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    consecutivePlaybackErrors = 0
                    retriedMediaId = null
                }
            }
        })
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .setSessionExtras(sleepTimerExtras())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /** 音频输出管线的瞬态错误：设备音效引擎/路由切换触发，重试一般即可恢复 */
    private fun isTransientAudioError(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED -> true
        else -> false
    }

    /**
     * 播放失败自动顺延下一首。出错后播放器处于 IDLE，需要重新 prepare。
     * 单曲循环下 seekToNextMediaItem 会回到当前失败项，临时切列表循环完成跳转；
     * 连续失败次数达到队列长度说明整列都播不了，停止避免死循环。
     */
    private fun skipToNextAfterError(player: ExoPlayer) {
        val count = player.mediaItemCount
        if (count == 0) return
        consecutivePlaybackErrors++
        if (consecutivePlaybackErrors >= count) {
            player.stop()
            return
        }
        val repeatMode = player.repeatMode
        if (repeatMode == Player.REPEAT_MODE_ONE) player.repeatMode = Player.REPEAT_MODE_ALL
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        } else {
            player.stop()
        }
        if (repeatMode == Player.REPEAT_MODE_ONE) player.repeatMode = Player.REPEAT_MODE_ONE
    }

    private fun setSleepTimer(endAtMs: Long) {
        handler.removeCallbacks(sleepTimerRunnable)
        sleepTimerEndAtMs = endAtMs.coerceAtLeast(0L)
        if (sleepTimerEndAtMs > 0L) {
            val delayMs = (sleepTimerEndAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            handler.postDelayed(sleepTimerRunnable, delayMs)
        }
        publishSleepTimer()
    }

    private fun sleepTimerExtras(): Bundle = Bundle().apply {
        putLong(EXTRA_SLEEP_TIMER_END_AT, sleepTimerEndAtMs)
    }

    private fun publishSleepTimer() {
        mediaSession?.setSessionExtras(sleepTimerExtras())
    }

    override fun onDestroy() {
        handler.removeCallbacks(sleepTimerRunnable)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val COMMAND_SET_SLEEP_TIMER = "com.beeze.music.SET_SLEEP_TIMER"
        const val EVENT_PLAYBACK_ERROR = "com.beeze.music.PLAYBACK_ERROR"
        const val EXTRA_SLEEP_TIMER_END_AT = "sleep_timer_end_at"
        const val EXTRA_SONG_TITLE = "song_title"
        val SET_SLEEP_TIMER_COMMAND = SessionCommand(COMMAND_SET_SLEEP_TIMER, Bundle.EMPTY)
        val PLAYBACK_ERROR_COMMAND = SessionCommand(EVENT_PLAYBACK_ERROR, Bundle.EMPTY)
    }
}
