package it.historia.player.data.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class Media3AudioEngine(context: Context) : AudioEngine {
    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var effectPlayer: ExoPlayer? = null
    private var backgroundPlayer: ExoPlayer? = null
    private var backgroundFadeJob: Job? = null
    private var backgroundDucked = false
    private var backgroundFullVolume = DEFAULT_BACKGROUND_FULL_VOLUME
    private var backgroundDuckedVolume = DEFAULT_BACKGROUND_DUCKED_VOLUME
    private var mainVolume = DEFAULT_MAIN_VOLUME
    private var onPlaybackEnded: (() -> Unit)? = null

    override fun playAsset(assetPath: String) {
        val mediaUri = resolveUri(assetPath)
        if (mediaUri == null) {
            Log.w(TAG, "Audio not found: $assetPath")
            return
        }

        runCatching {
            val exoPlayer = getOrCreatePlayer()
            val item = MediaItem.fromUri(mediaUri)

            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.volume = mainVolume
            exoPlayer.setMediaItem(item)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }.onFailure { error ->
            Log.e(TAG, "Failed to play asset: $assetPath", error)
        }
    }

    override fun setMainVolume(volume: Float) {
        mainVolume = volume.coerceIn(0f, 1f)
        player?.volume = mainVolume
    }

    override fun playEffectAsset(assetPath: String) {
        val mediaUri = resolveUri(assetPath)
        if (mediaUri == null) {
            Log.w(TAG, "Effect audio not found: $assetPath")
            return
        }

        runCatching {
            val exoPlayer = getOrCreateEffectPlayer()
            val item = MediaItem.fromUri(mediaUri)

            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaItem(item)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }.onFailure { error ->
            Log.e(TAG, "Failed to play effect asset: $assetPath", error)
        }
    }

    override fun playBackgroundAsset(assetPath: String) {
        val mediaUri = resolveUri(assetPath)
        if (mediaUri == null) {
            Log.w(TAG, "Background audio not found: $assetPath")
            return
        }

        runCatching {
            val exoPlayer = getOrCreateBackgroundPlayer()
            val item = MediaItem.fromUri(mediaUri)

            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
            exoPlayer.volume = backgroundTargetVolume()
            exoPlayer.setMediaItem(item)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }.onFailure { error ->
            Log.e(TAG, "Failed to play background asset: $assetPath", error)
        }
    }

    override fun setBackgroundDucked(ducked: Boolean) {
        backgroundDucked = ducked
        fadeBackgroundTo(backgroundTargetVolume())
    }

    override fun setBackgroundVolumeLevels(fullVolume: Float, duckedVolume: Float) {
        val clampedFullVolume = fullVolume.coerceIn(0f, 1f)
        backgroundFullVolume = clampedFullVolume
        backgroundDuckedVolume = duckedVolume.coerceIn(0f, clampedFullVolume)
        fadeBackgroundTo(backgroundTargetVolume())
    }

    override fun stop() {
        runCatching {
            player?.stop()
            player?.clearMediaItems()
        }.onFailure { error ->
            Log.e(TAG, "Failed to stop playback", error)
        }
    }

    override fun stopBackground() {
        runCatching {
            backgroundFadeJob?.cancel()
            backgroundPlayer?.stop()
            backgroundPlayer?.clearMediaItems()
        }.onFailure { error ->
            Log.e(TAG, "Failed to stop background playback", error)
        }
    }

    override fun setOnPlaybackEnded(listener: (() -> Unit)?) {
        onPlaybackEnded = listener
    }

    override fun release() {
        runCatching {
            backgroundFadeJob?.cancel()
            player?.release()
            player = null
            effectPlayer?.release()
            effectPlayer = null
            backgroundPlayer?.release()
            backgroundPlayer = null
        }.onFailure { error ->
            Log.e(TAG, "Failed to release player", error)
        }
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        return player ?: ExoPlayer.Builder(appContext)
            .build()
            .also { exo ->
                exo.addListener(
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                onPlaybackEnded?.invoke()
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.e(TAG, "Player error", error)
                        }
                    }
                )
                player = exo
            }
    }

    private fun getOrCreateEffectPlayer(): ExoPlayer {
        return effectPlayer ?: ExoPlayer.Builder(appContext)
            .build()
            .also { exo ->
                exo.addListener(
                    object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            Log.e(TAG, "Effect player error", error)
                        }
                    }
                )
                effectPlayer = exo
            }
    }

    private fun getOrCreateBackgroundPlayer(): ExoPlayer {
        return backgroundPlayer ?: ExoPlayer.Builder(appContext)
            .build()
            .also { exo ->
                exo.addListener(
                    object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            Log.e(TAG, "Background player error", error)
                        }
                    }
                )
                backgroundPlayer = exo
            }
    }

    private fun backgroundTargetVolume(): Float {
        return if (backgroundDucked) backgroundDuckedVolume else backgroundFullVolume
    }

    private fun fadeBackgroundTo(targetVolume: Float) {
        val exoPlayer = backgroundPlayer ?: return
        backgroundFadeJob?.cancel()
        val startVolume = exoPlayer.volume
        if (kotlin.math.abs(startVolume - targetVolume) < 0.01f) {
            exoPlayer.volume = targetVolume
            return
        }

        backgroundFadeJob = scope.launch {
            repeat(BACKGROUND_FADE_STEPS) { index ->
                val progress = (index + 1).toFloat() / BACKGROUND_FADE_STEPS
                exoPlayer.volume = startVolume + ((targetVolume - startVolume) * progress)
                delay(BACKGROUND_FADE_STEP_MS)
            }
            exoPlayer.volume = targetVolume
        }
    }

    private fun resolveUri(assetPath: String): Uri? {
        val userFile = File(appContext.filesDir, assetPath)
        if (userFile.isFile) {
            return Uri.fromFile(userFile)
        }

        val assetExists = runCatching {
            appContext.assets.open(assetPath).use { }
            true
        }.getOrElse {
            false
        }

        return if (assetExists) Uri.parse("asset:///$assetPath") else null
    }

    companion object {
        private const val TAG = "Media3AudioEngine"
        private const val DEFAULT_MAIN_VOLUME = 1f
        private const val DEFAULT_BACKGROUND_FULL_VOLUME = 1f
        private const val DEFAULT_BACKGROUND_DUCKED_VOLUME = 0.08f
        private const val BACKGROUND_FADE_STEPS = 8
        private const val BACKGROUND_FADE_STEP_MS = 50L
    }
}
