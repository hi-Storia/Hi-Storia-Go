package it.historia.player.data.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectArchiveFormatTest {
    @Test
    fun suggestedFileNameIsASafeSlug() {
        assertEquals("castello-di-santa-severa.historia.zip", ProjectArchiveFormat.suggestedFileName("Castello di Santa Sevèra!"))
        assertEquals("progetto.historia.zip", ProjectArchiveFormat.suggestedFileName("  ***  "))
        assertEquals("l-abbazia-2024.historia.zip", ProjectArchiveFormat.suggestedFileName("L'Abbazia / 2024"))
    }

    @Test
    fun archivePathsRoundTrip() {
        val series = ProjectAudioLocation.series(ProjectAudioKind.IDLE, "custom_profile_1", "custom_lang_1", "attesa 2.mp3")
        assertEquals("audio/series/custom_profile_1/custom_lang_1/idle/attesa 2.mp3", series.archivePath)
        assertEquals(series, ProjectArchiveFormat.parseArchivePath(series.archivePath))
        assertEquals(series, ProjectArchiveFormat.parseProjectRelativePath(series.projectRelativePath))

        val background = ProjectAudioLocation.background("musica.ogg")
        assertEquals("audio/background/musica.ogg", background.archivePath)
        assertEquals(background, ProjectArchiveFormat.parseArchivePath(background.archivePath))
    }

    @Test
    fun unsafeOrUnexpectedPathsAreNotParsed() {
        listOf(
            "audio/background/../x.mp3",
            "audio/background/.hidden.mp3",
            "audio/background/x.exe",
            "audio/series/adult/it/tracks/a/b.mp3",
            "audio/series/adult/it/other/a.mp3",
            "audio/series/ad..ult/it/tracks/a.mp3",
            "audio\\background\\x.mp3",
            "/audio/background/x.mp3",
            "other/background/x.mp3"
        ).forEach { path -> assertNull(path, ProjectArchiveFormat.parseArchivePath(path)) }
    }

    @Test
    fun entryNameSafety() {
        assertTrue(ProjectArchiveFormat.isSafeEntryName("audio/series/"))
        assertTrue(ProjectArchiveFormat.isSafeEntryName("manifest.json"))
        assertFalse(ProjectArchiveFormat.isSafeEntryName("../manifest.json"))
        assertFalse(ProjectArchiveFormat.isSafeEntryName("audio//x.mp3"))
        assertFalse(ProjectArchiveFormat.isSafeEntryName("audio/./x.mp3"))
        assertFalse(ProjectArchiveFormat.isSafeEntryName("C:\\x.mp3"))
    }

    @Test
    fun audioFileNamesFollowTheAppSanitizer() {
        assertTrue(ProjectArchiveFormat.isValidAudioFileName("01-ingresso.mp3"))
        assertTrue(ProjectArchiveFormat.isValidAudioFileName("-traccia_2 finale.m4a"))
        assertTrue(ProjectArchiveFormat.isValidAudioFileName("a..b.wav"))
        assertFalse(ProjectArchiveFormat.isValidAudioFileName(".."))
        assertFalse(ProjectArchiveFormat.isValidAudioFileName("a/b.mp3"))
        assertFalse(ProjectArchiveFormat.isValidAudioFileName("brano.txt"))
        assertFalse(ProjectArchiveFormat.isValidAudioFileName("brano,2.mp3"))
    }
}
