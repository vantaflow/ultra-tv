package com.ultratv.tv.nativeapp.ui.player

import com.ultratv.tv.nativeapp.ui.player.StreamDiagnostic.Cause
import com.ultratv.tv.nativeapp.ui.player.StreamDiagnostic.ErrorEvent
import com.ultratv.tv.nativeapp.ui.player.StreamDiagnostic.Grade
import com.ultratv.tv.nativeapp.ui.player.StreamDiagnostic.Input
import com.ultratv.tv.nativeapp.ui.player.StreamDiagnostic.Variant
import com.ultratv.tv.nativeapp.ui.player.engine.PlayErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamDiagnosticTest {
    private val variants = listOf(Variant(360, 800), Variant(720, 2500), Variant(1080, 5000))
    private fun ok() = Input(engine = "ExoPlayer/auto", playing = true, firstFrameMs = 900, resolution = "1920×1080", currentKbps = 5000, bandwidthKbps = 20000, variants = variants)

    @Test fun flux_sain_est_bon() {
        assertEquals(Grade.GOOD, StreamDiagnostic.grade(ok()))
        assertEquals(Cause.NONE, StreamDiagnostic.cause(ok()))
    }

    @Test fun sans_mesure_ni_erreur_inconcluant() {
        val i = Input(engine = "ExoPlayer/auto")
        assertEquals(Grade.UNKNOWN, StreamDiagnostic.grade(i))
        assertEquals(Cause.UNKNOWN, StreamDiagnostic.cause(i))
    }

    @Test fun quelques_mises_en_tampon_donnent_moyen() {
        assertEquals(Grade.FAIR, StreamDiagnostic.grade(ok().copy(rebufferCount = 2)))
        assertEquals(Grade.FAIR, StreamDiagnostic.grade(ok().copy(firstFrameMs = 6_000)))
        assertEquals(Grade.FAIR, StreamDiagnostic.grade(ok().copy(fellBack = true)))
    }

    @Test fun beaucoup_de_mises_en_tampon_donnent_mauvais() {
        assertEquals(Grade.POOR, StreamDiagnostic.grade(ok().copy(rebufferCount = 5)))
        assertEquals(Grade.POOR, StreamDiagnostic.grade(ok().copy(rebufferCount = 1, rebufferTotalMs = 20_000)))
    }

    @Test fun erreur_sans_lecture_est_mauvais() {
        val i = Input(engine = "VLC/auto", errors = listOf(ErrorEvent(PlayErrorKind.NETWORK)))
        assertEquals(Grade.POOR, StreamDiagnostic.grade(i))
    }

    @Test fun reseau_lent_quand_le_debit_recu_suffit_a_peine() {
        val i = ok().copy(rebufferCount = 3, bandwidthKbps = 5500)
        assertEquals(Cause.SLOW_NETWORK, StreamDiagnostic.cause(i))
    }

    @Test fun fournisseur_limite_quand_le_reseau_est_large_mais_le_flux_saccade() {
        assertEquals(Cause.PROVIDER_LIMITED, StreamDiagnostic.cause(ok().copy(rebufferCount = 3)))
    }

    @Test fun variante_bridee_quand_une_variante_basse_est_lue_malgre_un_bon_reseau() {
        val i = ok().copy(currentKbps = 800, resolution = "640×360", firstFrameMs = 7_000)
        assertEquals(Cause.VARIANT_CAPPED, StreamDiagnostic.cause(i))
    }

    @Test fun refus_introuvable_et_format_ne_sont_pas_pris_pour_le_fournisseur() {
        fun c(k: PlayErrorKind, h: Int? = null) = StreamDiagnostic.cause(Input(engine = "e", errors = listOf(ErrorEvent(k, h))))
        assertEquals(Cause.REFUSED, c(PlayErrorKind.REFUSED, 403))
        assertEquals(Cause.NOT_FOUND, c(PlayErrorKind.NOT_FOUND, 404))
        assertEquals(Cause.FORMAT, c(PlayErrorKind.FORMAT))
        assertEquals(Cause.FORMAT, c(PlayErrorKind.DECODER))
    }

    @Test fun images_perdues_nombreuses_designent_l_appareil() {
        val i = ok().copy(droppedFrames = 300, renderedFrames = 1000)
        assertEquals(Grade.FAIR, StreamDiagnostic.grade(i))
        assertEquals(Cause.DEVICE, StreamDiagnostic.cause(i))
    }

    @Test fun cas_ambigu_reste_inconcluant_pas_fournisseur() {
        // Moyen sans débit mesuré : on ne l'impute pas au fournisseur.
        val i = ok().copy(bandwidthKbps = null, rebufferCount = 2)
        assertEquals(Cause.UNKNOWN, StreamDiagnostic.cause(i))
    }

    @Test fun adresse_masquee_ne_garde_que_le_schema() {
        assertEquals("http://***", StreamDiagnostic.redactUrl("http://host.tv:8080/live/user1/secret/123.ts?token=abc"))
        assertEquals("https://***", StreamDiagnostic.redactUrl("HTTPS://x.y/z"))
    }

    @Test fun texte_libre_masque_adresses_parametres_et_mac() {
        val t = StreamDiagnostic.redactText("get http://h/get.php?username=bob&password=pw&token=t1 mac=00:1A:79:AB:CD:EF user: bob")
        assertFalse(t.contains("bob")); assertFalse(t.contains("pw")); assertFalse(t.contains("t1"))
        assertFalse(t.contains("00:1A")); assertFalse(t.contains("h/get"))
        assertTrue(t.contains("http://***"))
    }

    @Test fun rapport_sans_identifiants_et_complet() {
        val r = StreamDiagnostic.report(ok().copy(engine = "ExoPlayer/auto password=secret", errors = listOf(ErrorEvent(PlayErrorKind.NETWORK, 503))), "1.0")
        assertFalse(r.contains("secret"))
        assertTrue(r.contains("Result: "))
        assertTrue(r.contains("Variants: 360p"))
        assertTrue(r.contains("NETWORK (HTTP 503)"))
        assertTrue(r.contains("Time to first frame: 900 ms"))
    }
}
