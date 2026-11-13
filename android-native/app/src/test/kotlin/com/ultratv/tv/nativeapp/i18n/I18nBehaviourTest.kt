package com.ultratv.tv.nativeapp.i18n

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comportement de la couche de textes sous Robolectric :
 *  - changement de langue à chaud (aucun redémarrage, hors Compose) et arabe en RTL ;
 *  - AppLang.System suit la langue de l'appareil (en/fr/es/ar), l'anglais pour les autres ;
 *  - ressources Android historiques (nom de l'appli, canaux de notification…) inchangées, lues par le contexte normal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class I18nBehaviourTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() { I18n.init(ctx) }

    @Test fun changementDeLangueAChaud_sansRedemarrage() {
        val seen = listOf(AppLang.English, AppLang.French, AppLang.Arabic, AppLang.Spanish, AppLang.French, AppLang.English)
            .map { stringsOf(it).navHome }
        assertEquals(listOf("Home", "Accueil", seen[2], "Inicio", "Accueil", "Home"), seen)
        assertTrue(seen[2].any { it in '؀'..'ۿ' })
        // Les porteurs DesignStrings suivent aussi, même instance recréée à la demande (accès hors Compose).
        assertEquals("Retry", DesignStrings(AppLang.English).retry)
        assertEquals("Réessayer", DesignStrings(AppLang.French).retry)
    }

    @Test fun arabe_estRtl_lesAutresLangues_ltr() {
        assertEquals(View.LAYOUT_DIRECTION_RTL, I18n.resources(AppLang.Arabic).configuration.layoutDirection)
        for (l in listOf(AppLang.English, AppLang.French, AppLang.Spanish)) {
            assertEquals(l.code, View.LAYOUT_DIRECTION_LTR, I18n.resources(l).configuration.layoutDirection)
        }
        assertEquals(listOf(AppLang.Arabic), AppLang.entries.filter { it.rtl })
    }

    private fun systemMatches(expected: AppLang) {
        assertEquals(expected, I18n.resolve(AppLang.System))
        assertEquals(stringsOf(expected).navHome, stringsOf(AppLang.System).navHome)
        assertEquals(DesignStrings(expected).retry, DesignStrings(AppLang.System).retry)
        assertEquals(DesignStrings(expected).seasonsCount(2), DesignStrings(AppLang.System).seasonsCount(2))
    }

    @Test @Config(qualifiers = "en") fun system_en() = systemMatches(AppLang.English)
    @Test @Config(qualifiers = "fr") fun system_fr() = systemMatches(AppLang.French)
    @Test @Config(qualifiers = "es") fun system_es() = systemMatches(AppLang.Spanish)
    @Test @Config(qualifiers = "ar") fun system_ar() = systemMatches(AppLang.Arabic)
    @Test @Config(qualifiers = "fr-rCA") fun system_frCanada_resteFrancais() = systemMatches(AppLang.French)
    @Test @Config(qualifiers = "es-rMX") fun system_esMexique_resteEspagnol() = systemMatches(AppLang.Spanish)
    @Test @Config(qualifiers = "de") fun system_langueNonPrise_enAnglais() = systemMatches(AppLang.English)
    @Test @Config(qualifiers = "pl") fun system_polonais() = systemMatches(AppLang.Polish)
    @Test @Config(qualifiers = "pt-rBR") fun system_portugaisBresil() = systemMatches(AppLang.PortugueseBr)
    @Test @Config(qualifiers = "pt-rPT") fun system_portugaisPortugal_versBresil() = systemMatches(AppLang.PortugueseBr)

    @Test fun langueExplicite_ignoreLaLangueDeLAppareil() {
        // Même appareil (anglais par défaut) : le choix de l'utilisateur gagne.
        assertEquals("Accueil", stringsOf(AppLang.French).navHome)
        assertNotEquals(stringsOf(AppLang.English).navHome, stringsOf(AppLang.Spanish).navHome)
    }

    @Test fun ressourcesAndroidHistoriques_inchangees() {
        assertEquals("UTV", ctx.getString(R.string.app_name))
        assertEquals("Catalog sync", ctx.getString(R.string.sync_channel))
        assertEquals("Updating your channels…", ctx.getString(R.string.sync_notification))
        assertEquals("Favorites", ctx.getString(R.string.tv_favorites_channel))
        assertEquals("Search channels, movies and series", ctx.getString(R.string.search_hint))
        assertEquals("Home", ctx.getString(R.string.nav_home))
        assertEquals("Live TV", ctx.getString(R.string.nav_live))
        assertEquals("Settings", ctx.getString(R.string.nav_settings))
    }

    @Test @Config(qualifiers = "fr") fun ressourcesAndroidHistoriques_francais() {
        assertEquals("UTV", ctx.getString(R.string.app_name))
        assertEquals("Synchronisation du catalogue", ctx.getString(R.string.sync_channel))
        assertEquals("Mise à jour de vos chaînes…", ctx.getString(R.string.sync_notification))
        assertEquals("Favoris", ctx.getString(R.string.tv_favorites_channel))
        assertEquals("Chercher chaînes, films et séries", ctx.getString(R.string.search_hint))
        // Pas de traduction française de ces trois-là avant la migration : repli anglais, comme avant.
        assertEquals("Home", ctx.getString(R.string.nav_home))
    }

    @Test fun pluriels_arabe_quatreFormes() {
        val ar = DesignStrings(AppLang.Arabic)
        assertEquals("قناة واحدة", ar.channelCount(1))
        assertEquals("قناتان", ar.channelCount(2))
        assertEquals("5 قنوات", ar.channelCount(5))
        assertEquals("22 قناة", ar.channelCount(22))
    }
}
