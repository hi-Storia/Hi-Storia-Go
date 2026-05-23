package it.historia.player.data.audio

interface AudioEngine {
    fun playAsset(assetPath: String)
    fun setMainVolume(volume: Float)
    fun playEffectAsset(assetPath: String)
    fun playBackgroundAsset(assetPath: String)
    fun setBackgroundVolumeLevels(fullVolume: Float, duckedVolume: Float)
    fun setBackgroundDucked(ducked: Boolean)
    fun stopBackground()
    fun setOnPlaybackEnded(listener: (() -> Unit)?)
    fun stop()
    fun release()
}
