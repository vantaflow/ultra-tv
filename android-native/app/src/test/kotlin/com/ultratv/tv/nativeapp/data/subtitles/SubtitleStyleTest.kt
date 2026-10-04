package com.ultratv.tv.nativeapp.data.subtitles

import androidx.media3.ui.CaptionStyleCompat
import com.ultratv.tv.nativeapp.data.prefs.SubtitlePrefsStore
import com.ultratv.tv.nativeapp.ui.player.subtitles.SubtitleLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleStyleTest {
    @Test fun media3_taille_couleur_fond_contour() {
        val c = SubtitleStyleMapper.toCaption(SubtitleStyle(SubSize.LARGE, SubColor.YELLOW, SubBackground.SEMI, SubOutline.THICK, SubPosition.RAISED))
        assertEquals(48f / 1080f, c.textSizeFraction, 1e-6f)
        assertEquals(0xFFFFEB3B.toInt(), c.foregroundArgb)
        assertEquals(0x8C, (c.backgroundArgb ushr 24))              // 0,55 * 255
        assertEquals(CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, c.edgeType)
        assertEquals(0.14f, c.bottomPaddingFraction, 1e-6f)
    }

    @Test fun media3_sans_fond_ni_contour() {
        val c = SubtitleStyleMapper.toCaption(SubtitleStyle(background = SubBackground.NONE, outline = SubOutline.NONE))
        assertEquals(0, c.backgroundArgb)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_NONE, c.edgeType)
    }

    @Test fun vlc_options_equivalentes() {
        val o = SubtitleStyleMapper.toVlcOptions(SubtitleStyle(SubSize.SMALL, SubColor.CYAN, SubBackground.OPAQUE, SubOutline.THICK, SubPosition.HIGH))
        assertTrue("--freetype-color=${0x4DD0E1}" in o)
        assertTrue("--freetype-background-opacity=255" in o)
        assertTrue("--freetype-outline-thickness=3" in o)
        assertTrue(o.any { it == "--sub-margin=${(0.28f * 1080).toInt() - 65}" })
        assertEquals(0, SubtitleStyleMapper.toVlcOptions(SubtitleStyle(outline = SubOutline.NONE)).count { it == "--freetype-outline-thickness=1" })
    }

    @Test fun vlc_marge_basse_jamais_negative_et_taille_plus_grande_donne_valeur_plus_petite() {
        assertTrue(SubtitleStyleMapper.toVlcOptions(SubtitleStyle(position = SubPosition.BOTTOM)).contains("--sub-margin=0"))
        fun rel(s: SubSize) = SubtitleStyleMapper.toVlcOptions(SubtitleStyle(size = s)).first { it.startsWith("--freetype-rel-fontsize=") }.substringAfter('=').toInt()
        assertTrue(rel(SubSize.XLARGE) < rel(SubSize.SMALL))
    }

    @Test fun decalage_borne_et_converti_en_microsecondes() {
        assertEquals(10_000, SubtitleStyle().withDelay(99_000).delayMs)
        assertEquals(-10_000, SubtitleStyle().withDelay(-99_000).delayMs)
        assertEquals(200_000L, SubtitleStyleMapper.vlcDelayUs(SubtitleStyle(delayMs = 200)))
        assertEquals("+0,2 s", SubtitleLogic.delayLabel(200)); assertEquals("−1,5 s", SubtitleLogic.delayLabel(-1500))
        assertEquals(200, SubtitleLogic.stepDelay(100, 1)); assertEquals(10_000, SubtitleLogic.stepDelay(10_000, 1))
    }

    @Test fun rotation_des_valeurs() {
        assertEquals(SubSize.SMALL, SubtitleLogic.cycle(SubSize.entries, SubSize.XLARGE, 1))
        assertEquals(SubSize.XLARGE, SubtitleLogic.cycle(SubSize.entries, SubSize.SMALL, -1))
    }

    @Test fun langues_liste_ordonnee_ajout_et_retrait() {
        val l = SubtitleLogic.toggleLanguage(SubtitleLogic.toggleLanguage(emptyList(), "fr"), "en")
        assertEquals(listOf("fr", "en"), l)
        assertEquals(listOf("en"), SubtitleLogic.toggleLanguage(l, "fr"))
    }

    @Test fun choix_automatique_selon_la_premiere_langue_disponible() {
        val tracks = listOf("0" to "English · AC3 · 6ch", "1" to "Français · AAC", "2" to "Deutsch")
        assertEquals("1", TrackLanguagePicker.pick(tracks, listOf("fr", "en")))
        assertEquals("0", TrackLanguagePicker.pick(tracks, listOf("es", "en")))        // es absent : on passe à la suivante
        assertEquals("2", TrackLanguagePicker.pick(listOf("0" to "Track 1", "2" to "ger"), listOf("de")))
        assertNull(TrackLanguagePicker.pick(tracks, listOf("ar")))
        assertNull(TrackLanguagePicker.pick(tracks, emptyList()))
    }

    @Test fun preferences_valeurs_inconnues_et_langues_invalides() {
        assertEquals(SubSize.MEDIUM, SubtitlePrefsStore.enumOr("nimporte", SubSize.MEDIUM))
        assertEquals(SubSize.LARGE, SubtitlePrefsStore.enumOr("LARGE", SubSize.MEDIUM))
        assertEquals(listOf("fr", "en"), SubtitlePrefsStore.parseLangs("FR, en,x,fr,"))
    }

    @Test fun client_en_ligne_parse_les_resultats_tries_par_telechargements() {
        val hits = OpenSubtitlesClient.parseResults("""{"results":[{"id":"a","language":"fr","release":"R1","downloads":5},{"id":"b","language":"en","downloads":50},{"language":"x"}]}""")
        assertEquals(listOf("b", "a"), hits.map { it.id })
        assertTrue(OpenSubtitlesClient.parseResults("pas du json").isEmpty())
    }
}
