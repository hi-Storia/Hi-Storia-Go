package it.historia.player.data.project

import it.historia.player.data.project.ProjectArchiveException.Reason
import it.historia.player.data.project.ProjectTestFixture.Companion.replaceText
import it.historia.player.data.project.ProjectTestFixture.Companion.rewrite
import it.historia.player.data.project.ProjectTestFixture.Companion.writeZip
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectImportFailureTest {
    private lateinit var fixture: ProjectTestFixture
    private lateinit var validArchive: ByteArray
    private lateinit var existing: ProjectTestFixture.ProjectState

    @Before
    fun setUp() {
        fixture = ProjectTestFixture()
        fixture.buildRichProject()
        validArchive = fixture.exportBytes()
        // Lo smartphone di destinazione ha già un proprio progetto, che non deve mai essere alterato.
        fixture.buildOtherProject()
        existing = fixture.projectState()
    }

    // ------------------------------------------------------------ corrupted / incomplete

    @Test
    fun randomBytesAreRejected() {
        assertRejected(Reason.CORRUPTED_ARCHIVE, "questo non è uno zip".toByteArray())
    }

    @Test
    fun emptyFileIsRejected() {
        assertRejected(Reason.CORRUPTED_ARCHIVE, ByteArray(0))
    }

    @Test
    fun truncatedArchiveIsRejected() {
        val truncated = validArchive.copyOf(validArchive.size / 2)
        assertRejected(setOf(Reason.CORRUPTED_ARCHIVE, Reason.MISSING_FILE), truncated)
    }

    @Test
    fun archiveWithMissingAudioFileIsRejected() {
        val archive = rewrite(validArchive) { entries ->
            entries.removeAll { it.first == "audio/series/adult/it/tracks/02.mp3" }
        }
        val error = assertRejected(Reason.MISSING_FILE, archive)
        assertTrue(error.message!!.contains("02.mp3"))
    }

    @Test
    fun tamperedAudioFileIsRejected() {
        val archive = rewrite(validArchive) { entries ->
            val index = entries.indexOfFirst { it.first == "audio/series/adult/it/tracks/02.mp3" }
            entries[index] = entries[index].first to "it-tor!e".toByteArray()
        }
        assertRejected(Reason.MISSING_FILE, archive)
    }

    @Test
    fun undeclaredExtraFileIsRejected() {
        val archive = rewrite(validArchive) { entries ->
            entries += "audio/series/adult/it/tracks/intruso.mp3" to "x".toByteArray()
        }
        assertRejected(Reason.INVALID_MANIFEST, archive)
    }

    @Test
    fun archiveWithoutProjectJsonIsRejected() {
        val archive = rewrite(validArchive) { entries -> entries.removeAll { it.first == "project.json" } }
        assertRejected(Reason.INVALID_MANIFEST, archive)
    }

    // ------------------------------------------------------------ invalid manifest

    @Test
    fun manifestMustBeTheFirstEntry() {
        val archive = rewrite(validArchive) { entries ->
            val manifest = entries.removeAt(0)
            entries += manifest
        }
        assertRejected(Reason.INVALID_MANIFEST, archive)
    }

    @Test
    fun malformedManifestJsonIsRejected() {
        val archive = rewrite(validArchive) { entries ->
            entries.replaceText("manifest.json") { it.substring(0, it.length / 2) }
        }
        assertRejected(Reason.INVALID_MANIFEST, archive)
    }

    @Test
    fun foreignFormatIsRejected() {
        val archive = editManifest { it.put("format", "com.example.other") }
        assertRejected(Reason.INVALID_MANIFEST, archive)
    }

    @Test
    fun manifestWithWrongTypesIsRejected() {
        val archive = editManifest { it.put("schemaVersion", "1") }
        assertRejected(Reason.INVALID_MANIFEST, archive)
    }

    @Test
    fun invalidProjectValuesAreRejected() {
        val outOfRange = editProject { it.put("trackCount", 500) }
        assertRejected(Reason.INVALID_MANIFEST, outOfRange)

        val badId = editProject { project ->
            project.getJSONArray("profiles").getJSONObject(1).put("id", "../../etc")
        }
        assertRejected(Reason.INVALID_MANIFEST, badId)

        val badTrack = editProject { project ->
            project.getJSONArray("series").getJSONObject(0).getJSONArray("signals").getJSONObject(0)
                .put("track", "../../../shared_prefs/x.xml")
        }
        assertRejected(Reason.INVALID_MANIFEST, badTrack)

        val noEnabledSeries = editProject { project ->
            val series = project.getJSONArray("series")
            (0 until series.length()).forEach { series.getJSONObject(it).put("enabled", false) }
        }
        assertRejected(Reason.INVALID_MANIFEST, noEnabledSeries)
    }

    @Test
    fun unknownDeviceIsIncompatible() {
        val archive = rewrite(validArchive) { entries ->
            entries.replaceText("manifest.json") { text ->
                JSONObject(text).apply { getJSONObject("project").put("deviceId", "altro_totem") }.toString()
            }
            entries.replaceText("project.json") { text ->
                JSONObject(text).put("deviceId", "altro_totem").toString()
            }
        }
        assertRejected(Reason.INCOMPATIBLE_PROJECT, archive)
    }

    // ------------------------------------------------------------ versions

    @Test
    fun newerSchemaVersionIsRejectedWithUpdateHint() {
        val archive = editManifest { it.put("schemaVersion", ProjectArchiveFormat.SCHEMA_VERSION + 1) }
        val error = assertRejected(Reason.UNSUPPORTED_VERSION, archive)
        assertTrue(error.message!!.contains("Aggiorna"))
    }

    @Test
    fun invalidSchemaVersionIsRejected() {
        assertRejected(Reason.INVALID_MANIFEST, editManifest { it.put("schemaVersion", 0) })
    }

    // ------------------------------------------------------------ malicious paths & limits

    @Test
    fun maliciousZipPathsAreRejectedWithoutWritingOutside() {
        val maliciousNames = listOf(
            "../evil.mp3",
            "../../files/evil.mp3",
            "audio/../../evil.mp3",
            "audio/series/adult/it/tracks/../../../../../evil.mp3",
            "/data/data/it.historia.go/files/evil.mp3",
            "audio\\..\\..\\evil.mp3",
            "C:/evil.mp3",
            "audio/series/adult/it/tracks/./evil.mp3",
            "audio/series/../it/x/tracks/evil.mp3",
            "audio/series/adult/it/scripts/evil.mp3",
            "audio/series/adult/it/tracks/evil.sh",
            "shared_prefs/historia_project_settings_prefs.xml",
            "__MACOSX/._manifest.json"
        )
        maliciousNames.forEach { name ->
            val archive = rewrite(validArchive) { entries -> entries.add(2, name to "evil".toByteArray()) }
            assertRejected(Reason.UNSAFE_PATH, archive, label = name)
        }
        val parent = fixture.context.filesDir.parentFile!!
        assertTrue(parent.walkTopDown().none { it.name.startsWith("evil") })
    }

    @Test
    fun maliciousPathDeclaredInManifestIsRejected() {
        val archive = editManifest { manifest ->
            manifest.getJSONArray("files").getJSONObject(0).put("path", "audio/../../evil.mp3")
        }
        assertRejected(Reason.UNSAFE_PATH, archive)
    }

    @Test
    fun decompressionBombIsStoppedAtTheDeclaredSize() {
        val bomb = ByteArray(20 * 1024 * 1024) // 20 MB di zeri: ~20 KB compressi.
        val archive = rewrite(validArchive) { entries ->
            val index = entries.indexOfFirst { it.first == "audio/series/adult/it/tracks/02.mp3" }
            entries[index] = entries[index].first to bomb
        }
        assertTrue(archive.size < 1024 * 1024)
        assertRejected(Reason.MISSING_FILE, archive)
    }

    @Test
    fun declaredSizesBeyondLimitsAreRejected() {
        val archive = editManifest { manifest ->
            manifest.getJSONArray("files").getJSONObject(0).put("size", ProjectArchiveFormat.MAX_AUDIO_FILE_BYTES + 1)
        }
        assertRejected(Reason.LIMIT_EXCEEDED, archive)
    }

    @Test
    fun tooManyEntriesAreRejected() {
        val manifest = JSONObject(String(ProjectTestFixture.readZip(validArchive).first().second))
        manifest.put("files", org.json.JSONArray())
        val entries = mutableListOf("manifest.json" to manifest.toString().toByteArray())
        repeat(ProjectArchiveFormat.MAX_ENTRY_COUNT + 1) { index -> entries += "audio/dir$index/" to ByteArray(0) }
        assertRejected(Reason.LIMIT_EXCEEDED, writeZip(entries))
    }

    @Test
    fun insufficientSpaceIsDetectedBeforeExtracting() {
        fixture.availableSpace = 1024
        assertRejected(Reason.INSUFFICIENT_SPACE, validArchive)
    }

    // ------------------------------------------------------------ rollback

    @Test
    fun failureAtEveryApplyStageRestoresTheExistingProject() {
        ImportStage.values().forEach { stage ->
            val prepared = fixture.prepare(validArchive)
            fixture.manager.faultInjector = { current ->
                if (current == stage) throw IOException("guasto simulato in $stage")
            }
            try {
                fixture.manager.applyImport(prepared)
                fail("L'importazione doveva fallire in $stage")
            } catch (error: ProjectArchiveException) {
                assertEquals(stage.name, Reason.IMPORT_ROLLED_BACK, error.reason)
            } finally {
                fixture.manager.faultInjector = null
            }
            assertEquals(stage.name, existing, fixture.projectState())
            assertEquals(stage.name, emptyList<String>(), fixture.leftoverTransferFiles())
        }
    }

    @Test
    fun noSpaceDuringApplyIsReportedAndRolledBack() {
        val prepared = fixture.prepare(validArchive)
        fixture.manager.faultInjector = { stage ->
            if (stage == ImportStage.AUDIO_INSTALLED) throw IOException("write failed: ENOSPC (No space left on device)")
        }
        try {
            fixture.manager.applyImport(prepared)
            fail("L'importazione doveva fallire")
        } catch (error: ProjectArchiveException) {
            assertEquals(Reason.IMPORT_ROLLED_BACK, error.reason)
            assertTrue(error.message!!.contains("Spazio"))
        } finally {
            fixture.manager.faultInjector = null
        }
        assertEquals(existing, fixture.projectState())
    }

    @Test
    fun processKilledDuringImportIsRolledBackAtNextStartup() {
        listOf(ImportStage.AUDIO_BACKED_UP, ImportStage.AUDIO_INSTALLED, ImportStage.PREFERENCES_WRITTEN)
            .forEach { stage ->
                val crash = CrashState()
                val prepared = fixture.prepare(validArchive)
                fixture.manager.faultInjector = { current ->
                    if (current == stage) {
                        crash.capture()
                        throw IOException("processo terminato")
                    }
                }
                runCatching { fixture.manager.applyImport(prepared) }
                fixture.manager.faultInjector = null

                // Riporta il disco esattamente allo stato del momento del "crash".
                crash.restore()
                assertTrue(stage.name, File(fixture.workDir, "import-journal.json").isFile)

                assertTrue(stage.name, fixture.manager.recoverInterruptedImport())
                assertEquals(stage.name, existing, fixture.projectState())
                assertEquals(stage.name, emptyList<String>(), fixture.leftoverTransferFiles())
            }
    }

    @Test
    fun successfulImportAfterAFailedOneStillWorks() {
        val prepared = fixture.prepare(validArchive)
        fixture.manager.faultInjector = { throw IOException("guasto") }
        runCatching { fixture.manager.applyImport(prepared) }
        fixture.manager.faultInjector = null

        fixture.manager.applyImport(fixture.prepare(validArchive))
        assertEquals("Castello di Santa Severa", fixture.projectSettingsStore.load(fixture.device).culturalAssetName)
    }

    // ------------------------------------------------------------ helpers

    private inner class CrashState {
        private val snapshotDir = File(fixture.context.cacheDir, "crash-${System.nanoTime()}")
        private var preferences: Map<String, Map<String, Any?>> = emptyMap()

        fun capture() {
            snapshotDir.mkdirs()
            if (fixture.workDir.exists()) fixture.workDir.copyRecursively(File(snapshotDir, "work"))
            if (fixture.projectAudioDir.exists()) fixture.projectAudioDir.copyRecursively(File(snapshotDir, "audio"))
            preferences = fixture.storage.preferenceSections(fixture.deviceId)
                .associate { it.preferencesName to it.entries() }
        }

        fun restore() {
            fixture.workDir.deleteRecursively()
            fixture.projectAudioDir.deleteRecursively()
            File(snapshotDir, "work").takeIf { it.exists() }?.copyRecursively(fixture.workDir)
            File(snapshotDir, "audio").takeIf { it.exists() }?.copyRecursively(fixture.projectAudioDir)
            fixture.storage.preferenceSections(fixture.deviceId).forEach { section ->
                section.replaceAll(preferences[section.preferencesName].orEmpty())
            }
            snapshotDir.deleteRecursively()
        }
    }

    private fun editManifest(edit: (JSONObject) -> Unit): ByteArray = rewrite(validArchive) { entries ->
        entries.replaceText("manifest.json") { text -> JSONObject(text).also(edit).toString() }
    }

    private fun editProject(edit: (JSONObject) -> Unit): ByteArray = rewrite(validArchive) { entries ->
        entries.replaceText("project.json") { text -> JSONObject(text).also(edit).toString() }
    }

    private fun assertRejected(reason: Reason, archive: ByteArray, label: String = ""): ProjectArchiveException {
        return assertRejected(setOf(reason), archive, label)
    }

    private fun assertRejected(
        reasons: Set<Reason>,
        archive: ByteArray,
        label: String = ""
    ): ProjectArchiveException {
        val error = try {
            fixture.prepare(archive)
            fail("Archivio accettato ma doveva essere rifiutato $label")
            throw AssertionError()
        } catch (error: ProjectArchiveException) {
            error
        }
        assertTrue("$label: motivo ${error.reason} (${error.message}) non in $reasons", error.reason in reasons)
        assertFalse(error.message.isNullOrBlank())
        assertEquals(label, existing, fixture.projectState())
        assertEquals(label, emptyList<String>(), fixture.leftoverTransferFiles())
        return error
    }
}
