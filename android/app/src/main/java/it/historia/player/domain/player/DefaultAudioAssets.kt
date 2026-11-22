package it.historia.player.domain.player

/**
 * Audio inclusi nell'APK (cartella `assets/`). Non vengono mai copiati negli archivi di progetto:
 * l'archivio li cita come dipendenze e l'app che importa li risolve dai propri asset.
 */
object DefaultAudioAssets {
    const val IDLE = "audio/idle/default/idle.mp3"
    const val BACKGROUND = "audio/background/default/santelena.mp3"
    const val EXPLORATION_INTRO = "audio/exploration/default/m.wav"
    const val EXPLORATION_SIGNAL = "audio/exploration/default/s.wav"
}
