package it.historia.player.data.project

import it.historia.player.data.preferences.BackgroundMusicSettings
import it.historia.player.data.preferences.ExplorationSettings
import it.historia.player.data.preferences.ExplorationTrackSettings
import it.historia.player.data.preferences.IdleSettings
import it.historia.player.data.preferences.IdleTrackSettings
import it.historia.player.data.preferences.PresentationSettings
import it.historia.player.data.preferences.ProjectSettings
import it.historia.player.domain.model.PlaybackContext

/**
 * Rappresentazione completa e portabile di un progetto: è ciò che viene serializzato in
 * `project.json` e ciò che viene riscritto negli store durante l'importazione.
 */
data class ProjectSnapshot(
    val deviceId: String,
    val settings: ProjectSettings,
    val idle: IdleSettings,
    val exploration: ExplorationSettings,
    val backgroundMusic: BackgroundMusicSettings,
    /** Tutte le serie (tipologia × lingua) del progetto, comprese quelle disattivate. */
    val series: List<SeriesSnapshot>
) {
    fun seriesFor(profileId: String, language: String): SeriesSnapshot? {
        return series.firstOrNull { it.profileId == profileId && it.language == language }
    }
}

data class SeriesSnapshot(
    val profileId: String,
    val language: String,
    /** Associazioni segnale → chiave traccia memorizzate esplicitamente. */
    val trackMappings: Map<Int, String>,
    /** Nomi degli attivatori. */
    val labels: Map<Int, String>,
    /** Volumi in percentuale (10..100); i segnali assenti sono al 100%. */
    val volumes: Map<Int, Int>,
    /** Tracce standard dell'APK nascoste dall'utente in questa serie. */
    val hiddenDefaultTracks: Set<String>,
    val idleTrack: IdleTrackSettings,
    val explorationTrack: ExplorationTrackSettings,
    val presentation: PresentationSettings
) {
    fun context(deviceId: String) = PlaybackContext(deviceId = deviceId, profile = profileId, language = language)
}
