package com.ultratv.tv.nativeapp.ui.downloads

import com.ultratv.tv.nativeapp.data.downloads.DownloadEntity
import com.ultratv.tv.nativeapp.data.downloads.DownloadError
import com.ultratv.tv.nativeapp.data.downloads.DownloadKind
import com.ultratv.tv.nativeapp.data.downloads.DownloadState
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.Strings
import org.junit.Assert.assertEquals
import org.junit.Test

/** Libellés : langue reconnue depuis la table du rail TV, bouton selon l'état, messages d'erreur sans texte brut. */
class DownloadStringsTest {
    private fun table(name: String): Strings =
        Class.forName("com.ultratv.tv.nativeapp.i18n.StringsKt").getDeclaredField(name).apply { isAccessible = true }.get(null) as Strings

    @Test fun of_chaqueTableDuRail_donneSaLangue() {
        assertEquals(AppLang.English, DownloadStrings.of(table("EN")).lang)
        assertEquals(AppLang.French, DownloadStrings.of(table("FR")).lang)
        assertEquals(AppLang.Spanish, DownloadStrings.of(table("ES")).lang)
        assertEquals(AppLang.Arabic, DownloadStrings.of(table("AR")).lang)
    }

    private fun d(state: String, done: Long = 0, total: Long = 0) =
        DownloadEntity(providerId = 1, kind = DownloadKind.MOVIE, remoteId = "1", title = "T", urlHash = "h", filePath = "/f", state = state, doneBytes = done, totalBytes = total)

    @Test fun libelle_selonEtat() {
        val s = DownloadStrings(AppLang.French)
        assertEquals("Télécharger", downloadLabel(null, s))
        assertEquals("42 %", downloadLabel(d(DownloadState.RUNNING, 42, 100), s))
        assertEquals("En cours", downloadLabel(d(DownloadState.RUNNING), s))   // taille encore inconnue
        assertEquals("En attente", downloadLabel(d(DownloadState.QUEUED), s))
        assertEquals("En pause", downloadLabel(d(DownloadState.PAUSED), s))
        assertEquals("Réessayer", downloadLabel(d(DownloadState.FAILED), s))
        assertEquals("Téléchargé", downloadLabel(d(DownloadState.COMPLETED), s))
    }

    @Test fun erreur_codeConnu_messageTraduit_inconnu_reseau() {
        val s = DownloadStrings(AppLang.French)
        assertEquals(s.noSpace, s.error(DownloadError.NO_SPACE))
        assertEquals(s.notDownloadable, s.error(DownloadError.NOT_DOWNLOADABLE))
        assertEquals(s.networkError, s.error(null))
    }

    @Test fun octetsUtilises_terminesEtPartiels() {
        assertEquals(150L, usedBytes(listOf(d(DownloadState.COMPLETED, 100, 100), d(DownloadState.RUNNING, 50, 1000))))
    }
}
