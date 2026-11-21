package com.ultratv.tv.nativeapp.i18n

import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic
import com.ultratv.tv.nativeapp.data.mediaserver.MediaServer
import com.ultratv.tv.nativeapp.data.mediaserver.Plex
import com.ultratv.tv.nativeapp.data.replay.CatchupFormats
import com.ultratv.tv.nativeapp.data.repo.TitleCleaner
import com.ultratv.tv.nativeapp.data.trakt.TraktCard
import com.ultratv.tv.nativeapp.data.trakt.TraktResume
import com.ultratv.tv.nativeapp.data.trakt.TraktUpNextCard
import com.ultratv.tv.nativeapp.ui.home.runtimeLabel
import com.ultratv.tv.nativeapp.ui.home.traktUpNextPosterOf
import com.ultratv.tv.nativeapp.ui.live.durationText
import com.ultratv.tv.nativeapp.ui.movies.movieDuration
import com.ultratv.tv.nativeapp.ui.player.ProgrammeInfo
import com.ultratv.tv.nativeapp.ui.player.subtitles.SubtitleLogic
import com.ultratv.tv.nativeapp.ui.player.timeshift.behindLabel
import com.ultratv.tv.nativeapp.ui.simple.SimpleText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Seconde passe arabe : le framework remet `Locale.getDefault()` sur la langue du SYSTÈME (sans nu-latn) à chaque
 * changement de configuration (rotation, mode nuit, manette, HDMI). On simule ici cette remise à zéro avec ar-EG, dont
 * les chiffres par défaut sont arabo-indiens sur JDK 17 ET sur les JDK récents (et sur ICU) : aucun site ne doit en
 * dépendre, ni à l'écran, ni dans une URL, ni en base.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArabicDigitsConfigResetTest {
    private lateinit var saved: Locale

    @Before fun setUp() {
        saved = Locale.getDefault()
        I18n.init(ApplicationProvider.getApplicationContext())
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
    }
    @After fun restore() { Locale.setDefault(saved) }

    private fun bad(s: String?) = s.orEmpty().filter { it.isDigit() && it !in '0'..'9' }
    private fun assertLatin(label: String, s: String?) {
        assertEquals("$label → « $s »", "", bad(s))
    }

    @Test fun laLocaleDeTestProduitBienDesChiffresArabes() {
        // Garde-fou du test lui-même : sans cela, il ne prouverait rien (JDK récent + arabe nu = chiffres latins).
        assertEquals("٢٢", String.format("%d", 22))
    }

    @Test fun affichage_enChiffresLatins_memeApresRemiseAZeroDeLaLocale() {
        val D = DesignStrings(AppLang.Arabic)
        assertLatin("durée Direct", durationText(D, 65 * 60_000L))
        assertLatin("durée hero", runtimeLabel(101, D.hourShort, D.minShort))
        assertLatin("À suivre Trakt", traktUpNextPosterOf(TraktUpNextCard(TraktCard(true, 1, "Lost", null, null, null), 2, 5)).item.title)
        assertEquals("Lost · S02E05", traktUpNextPosterOf(TraktUpNextCard(TraktCard(true, 1, "Lost", null, null, null), 2, 5)).item.title)
        assertLatin("différé", behindLabel(3_725))
        assertLatin("programme", ProgrammeInfo.durationLabel(95 * 60_000L))
        assertLatin("sous-titres", SubtitleLogic.delayLabel(1_500))
        assertLatin("durée film", movieDuration("95"))
        assertLatin("durée mode simple", SimpleText.duration("95"))
        assertLatin("code Plex", MediaLibraryLogic.countdown(125_000))
    }

    @Test fun urlDeReplayEtValeursEnBase_enAscii() {
        val start = 1_763_800_000_000L
        val url = CatchupFormats.fill("http://h/{Y}-{m}-{d}/{H}-{M}-{S}/x.ts", start, start + 3_600_000, start + 600_000)
        assertEquals("URL de replay : $url", url, url.filter { it.code < 128 })
        assertLatin("durée Plex", Plex.durationText(5_520_000))
        assertEquals("01:32:00", Plex.durationText(5_520_000))
        assertEquals("01:32:00", MediaServer.durationText(5_520_000L * 10_000))
    }

    @Test fun valeursDejaEcritesEnChiffresArabes_toleresALaLecture() {
        // Écrites par d'anciennes versions (synchro ou lecture après une remise à zéro de la locale).
        assertEquals(5_520_000L, TraktResume.parseDurationMs("٠١:٣٢:٠٠"))
        assertEquals("1 h 32", movieDuration("٠١:٣٢:٠٠"))
        assertEquals("Lost · S02E05 · Pilot", TitleCleaner.historyTitle("Lost · S٠٢E٠٥ · Pilot"))
    }

    @Test fun localeDAffichage_latineMemeAvecUnNuExplicite_etPourLesLanguesOubliees() {
        assertEquals("latn", DisplayLocale.latin(Locale.forLanguageTag("ar-EG-u-nu-arab")).getUnicodeLocaleType("nu"))
        for (tag in listOf("as", "mni", "sat", "sa", "uz-Arab", "pa-Arab", "ar-EG", "fa-IR")) {
            assertEquals(tag, "12", String.format(DisplayLocale.of(tag), "%d", 12))
        }
        // Langues à chiffres latins : locale rendue telle quelle (pas d'extension ajoutée).
        assertEquals(Locale.FRENCH, DisplayLocale.latin(Locale.FRENCH))
        // Mise en cache : même objet d'un appel à l'autre.
        assertEquals(true, DisplayLocale.of("ar") === DisplayLocale.of("ar"))
    }
}
