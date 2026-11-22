package it.historia.player.data.project

import it.historia.player.data.preferences.BackgroundMusicSettings
import it.historia.player.data.preferences.ExplorationTrackSettings
import it.historia.player.data.preferences.IdleTrackSettings
import it.historia.player.data.preferences.PresentationSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectTransferRoundTripTest {
    private lateinit var fixture: ProjectTestFixture

    @Before
    fun setUp() {
        fixture = ProjectTestFixture()
        fixture.buildRichProject()
    }

    @Test
    fun exportThenImportOnAnotherPhoneRestoresAnEquivalentProject() {
        val source = fixture.projectState()
        val archive = fixture.exportBytes()

        // Smartphone di destinazione con un altro progetto già configurato.
        fixture.buildOtherProject()
        val prepared = fixture.prepare(archive)
        fixture.manager.applyImport(prepared)

        val imported = fixture.projectState()
        assertEquals(source.snapshot, imported.snapshot)
        assertEquals(source.files - "adult/fr/tracks/orfano.mp3", imported.files)
        assertEquals(emptyList<String>(), fixture.leftoverTransferFiles())
    }

    @Test
    fun importRemovesEveryResidueOfThePreviousProject() {
        val archive = fixture.exportBytes()
        fixture.buildOtherProject()

        fixture.manager.applyImport(fixture.prepare(archive))

        val files = fixture.audioFiles()
        assertFalse(files.containsKey("adult/it/tracks/vecchio.mp3"))
        assertFalse(files.containsKey("adult/es/idle/espera.mp3"))
        assertFalse(files.containsKey("custom_profile_2/it/tracks/esperti.mp3"))
        assertFalse(files.containsKey("background/vecchia-musica.mp3"))

        val preferenceKeys = fixture.storage.preferenceSections(fixture.deviceId).flatMap { it.entries().keys }
        assertTrue(preferenceKeys.none { it.contains("custom_profile_2") })
        assertTrue(preferenceKeys.none { it.contains(":es") || it.endsWith(".es") })
        val settings = fixture.projectSettingsStore.load(fixture.device)
        assertEquals(setOf("custom_profile_1"), settings.customProfiles)
        assertEquals("Bambini", settings.profileLabels["custom_profile_1"])
    }

    @Test
    fun multipleProfilesLanguagesAndDisabledSeriesSurvive() {
        val archive = fixture.exportBytes()
        fixture.buildOtherProject()
        val prepared = fixture.prepare(archive)

        val summary = prepared.summary
        assertEquals("Castello di Santa Severa", summary.projectName)
        assertEquals("Liceo Galilei", summary.schoolName)
        assertEquals(4, summary.seriesCount)
        assertEquals(3, summary.enabledSeriesCount)
        val adult = summary.profiles.first { it.label == "Adulti" }
        assertEquals(listOf(LanguageSummary("Italiano", true), LanguageSummary("English", false)), adult.languages)
        val kids = summary.profiles.first { it.label == "Bambini" }
        assertEquals(
            listOf(LanguageSummary("Napoletano", true), LanguageSummary("Italiano", true)),
            kids.languages
        )
        assertEquals(emptyList<String>(), summary.warnings)

        fixture.manager.applyImport(prepared)
        val settings = fixture.projectSettingsStore.load(fixture.device)
        assertEquals(setOf("adult:it", "custom_profile_1:it", "custom_profile_1:custom_lang_1"), settings.enabledSeries)
        assertEquals(mapOf("adult" to setOf("en"), "custom_profile_1" to setOf("it", "custom_lang_1")), settings.customLanguagesByProfile)
        assertEquals(6, settings.trackCount)
        // La serie disattivata mantiene associazioni, presentazione e audio.
        val adultEn = fixture.ctx("adult", "en")
        assertEquals(mapOf(1 to "01-entrance.mp3"), fixture.signalMappingStore.loadStoredMappings(adultEn))
        assertEquals(PresentationSettings("welcome.mp3"), fixture.presentationSettingsStore.load(adultEn))
        assertEquals("en-entrance", fixture.audioFiles()["adult/en/tracks/01-entrance.mp3"])
        assertEquals("en-welcome", fixture.audioFiles()["adult/en/presentation/welcome.mp3"])
    }

    @Test
    fun mappingsActivatorNamesAndVolumesArePreserved() {
        val archive = fixture.exportBytes()
        fixture.buildOtherProject()
        fixture.manager.applyImport(fixture.prepare(archive))

        val adultIt = fixture.ctx("adult", "it")
        assertEquals(
            mapOf(1 to "01-ingresso.mp3", 2 to "02.mp3", 3 to "00.mp3"),
            fixture.signalMappingStore.loadStoredMappings(adultIt)
        )
        assertEquals(mapOf(1 to "Ingresso", 2 to "Torre: piano alto"), fixture.signalMappingStore.loadLabels(adultIt, 6))
        assertEquals(mapOf(1 to 80, 2 to 35), fixture.signalMappingStore.loadVolumes(adultIt, 6))
        val kidsIt = fixture.ctx("custom_profile_1", "it")
        assertEquals(mapOf(1 to "fiaba.mp3", 4 to "01-ingresso.mp3"), fixture.signalMappingStore.loadStoredMappings(kidsIt))
        assertEquals(mapOf(4 to "Ingresso bimbi"), fixture.signalMappingStore.loadLabels(kidsIt, 6))
        assertEquals(
            setOf("00.mp3"),
            fixture.userAudioStore.loadHiddenAssetTracks(fixture.ctx("custom_profile_1", "custom_lang_1"))
        )
    }

    @Test
    fun idleExplorationPresentationAndBackgroundTracksArePreserved() {
        val archive = fixture.exportBytes()
        fixture.buildOtherProject()
        fixture.manager.applyImport(fixture.prepare(archive))

        val adultIt = fixture.ctx("adult", "it")
        val kidsIt = fixture.ctx("custom_profile_1", "it")
        assertEquals(12, fixture.idleSettingsStore.load(fixture.deviceId).timeoutMinutes)
        assertEquals(IdleTrackSettings(false, "attesa.mp3"), fixture.idleSettingsStore.loadTrackSettings(adultIt))
        assertEquals(IdleTrackSettings(true, null), fixture.idleSettingsStore.loadTrackSettings(kidsIt))
        assertEquals(7, fixture.explorationSettingsStore.load(fixture.deviceId).timeoutMinutes)
        assertEquals(
            ExplorationTrackSettings(false, "intro.wav"),
            fixture.explorationSettingsStore.loadTrackSettings(kidsIt)
        )
        assertEquals(
            BackgroundMusicSettings(
                enabled = true,
                useDefaultTrack = false,
                customTrackKey = "musica.mp3",
                onlyDuringTracks = true,
                fullVolumePercent = 75f,
                duckedVolumePercent = 12.5f
            ),
            fixture.backgroundMusicSettingsStore.load(fixture.deviceId)
        )
        val files = fixture.audioFiles()
        assertEquals("it-attesa", files["adult/it/idle/attesa.mp3"])
        assertEquals("kids-intro", files["custom_profile_1/it/exploration/intro.wav"])
        assertEquals("musica", files["background/musica.mp3"])
    }

    @Test
    fun archiveHasExplicitVersionedLayoutWithoutApkAssets() {
        val entries = ProjectTestFixture.readZip(fixture.exportBytes())
        val names = entries.map { it.first }

        assertEquals(ProjectArchiveFormat.MANIFEST_ENTRY, names.first())
        assertTrue(ProjectArchiveFormat.PROJECT_ENTRY in names)
        assertTrue(names.none { it.startsWith("audio/default/") || it.contains("santelena") || it.endsWith("00.mp3") })
        assertTrue("audio/series/adult/en/tracks/01-entrance.mp3" in names)
        assertTrue("audio/series/custom_profile_1/custom_lang_1/tracks/storia.ogg" in names)
        assertTrue("audio/series/adult/it/tracks/non-associato.m4a" in names)
        // File fuori dalle serie del progetto ma referenziato tramite ripiego.
        assertTrue("audio/series/adult/custom_lang_1/tracks/eco.mp3" in names)
        assertTrue("audio/background/musica.mp3" in names)
        assertFalse(names.any { it.contains("orfano") })

        val manifest = JSONObject(String(entries.first().second))
        assertEquals(ProjectArchiveFormat.FORMAT_ID, manifest.getString("format"))
        assertEquals(ProjectArchiveFormat.SCHEMA_VERSION, manifest.getInt("schemaVersion"))
        assertEquals("default_project", manifest.getJSONObject("project").getString("deviceId"))
        assertEquals("Castello di Santa Severa", manifest.getJSONObject("project").getString("name"))
        val dependencies = manifest.getJSONArray("assetDependencies").let { array ->
            (0 until array.length()).map { array.getString(it) }.toSet()
        }
        assertTrue("audio/default/tracks/00.mp3" in dependencies)
        assertEquals(names.count { it.startsWith("audio/") }, manifest.getJSONArray("files").length())
        // Nessun dato estraneo al progetto (diagnostica, soglie hardware, selezione corrente).
        val allText = entries.filter { !it.first.startsWith("audio/") }.joinToString { String(it.second) }
        assertFalse(allText.contains("threshold", ignoreCase = true))
        assertFalse(allText.contains("selected_", ignoreCase = true))
    }

    @Test
    fun missingReferencesAndAssetsAreReportedAsWarnings() {
        fixture.presentationSettingsStore.save(fixture.ctx("adult", "it"), PresentationSettings("sparita.mp3"))
        fixture.signalMappingStore.save(fixture.ctx("adult", "it"), mapOf(5 to "inesistente.mp3"), 6)
        val archive = fixture.exportBytes()
        fixture.apkAssets.remove("audio/default/tracks/00.mp3")

        val warnings = fixture.prepare(archive).summary.warnings

        assertTrue(warnings.any { it.contains("sparita.mp3") })
        assertTrue(warnings.any { it.startsWith("Adulti / Italiano") && it.contains("T5") })
        assertTrue(warnings.any { it.contains("00.mp3") })
    }

    @Test
    fun discardedPreviewLeavesNoTemporaryFiles() {
        val archive = fixture.exportBytes()
        val before = fixture.projectState()

        val prepared = fixture.prepare(archive)
        assertTrue(fixture.leftoverTransferFiles().isNotEmpty())
        fixture.manager.discardImport(prepared)

        assertEquals(emptyList<String>(), fixture.leftoverTransferFiles())
        assertEquals(before, fixture.projectState())
    }

    @Test
    fun stagingLeftByAKilledProcessIsRemovedAtStartup() {
        fixture.prepare(fixture.exportBytes())
        assertTrue(fixture.leftoverTransferFiles().isNotEmpty())

        assertFalse(fixture.manager.recoverInterruptedImport())
        assertEquals(emptyList<String>(), fixture.leftoverTransferFiles())
    }

    @Test
    fun userAudioPathsMatchArchiveLocations() {
        val context = fixture.ctx("custom_profile_1", "custom_lang_1")
        val prefix = "audio/${fixture.deviceId}/"
        val store = fixture.userAudioStore
        assertEquals(
            store.idleRelativePathFor(context, "a.mp3"),
            prefix + ProjectAudioLocation.series(ProjectAudioKind.IDLE, "custom_profile_1", "custom_lang_1", "a.mp3").projectRelativePath
        )
        assertEquals(
            store.explorationRelativePathFor(context, "a.mp3"),
            prefix + ProjectAudioLocation.series(ProjectAudioKind.EXPLORATION, "custom_profile_1", "custom_lang_1", "a.mp3").projectRelativePath
        )
        assertEquals(
            store.presentationRelativePathFor(context, "a.mp3"),
            prefix + ProjectAudioLocation.series(ProjectAudioKind.PRESENTATION, "custom_profile_1", "custom_lang_1", "a.mp3").projectRelativePath
        )
        assertEquals(
            it.historia.player.data.audio.UserAudioStore.relativePathFor(context, "a.mp3"),
            prefix + ProjectAudioLocation.series(ProjectAudioKind.TRACKS, "custom_profile_1", "custom_lang_1", "a.mp3").projectRelativePath
        )
        assertEquals(
            store.backgroundRelativePathFor(fixture.deviceId, "a.mp3"),
            prefix + ProjectAudioLocation.background("a.mp3").projectRelativePath
        )
    }
}
