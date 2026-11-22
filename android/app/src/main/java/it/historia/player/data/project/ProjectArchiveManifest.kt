package it.historia.player.data.project

/** Informazioni sull'app che ha generato l'archivio (solo informative). */
data class ArchiveGenerator(
    val appName: String,
    val applicationId: String,
    val versionName: String,
    val versionCode: Long
)

/** Un file audio contenuto nell'archivio, con dimensione e impronta SHA-256 attese. */
data class ArchiveFileEntry(
    val path: String,
    val size: Long,
    val sha256: String
)

data class ProjectArchiveManifest(
    val schemaVersion: Int,
    val createdAt: String,
    val generator: ArchiveGenerator,
    val deviceId: String,
    val projectName: String,
    val schoolName: String,
    val files: List<ArchiveFileEntry>,
    /** Audio standard dell'APK richiesti dal progetto: non sono inclusi nell'archivio. */
    val assetDependencies: List<String>
) {
    val totalAudioBytes: Long get() = files.sumOf { it.size }
}
