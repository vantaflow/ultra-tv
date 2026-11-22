package com.ultratv.tv.nativeapp.data.kairo

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KairoNowPlanTest {
    private val secrets = KairoNowPlan.Secrets.of("http://fournisseur.invalid:8080", "ZZUSERZZ", "ZZPASSZZ")

    private fun ch(id: Long, name: String = "Chaîne $id", cat: String? = "c1", logo: String? = "https://logos.example/$id.png", num: Int = id.toInt()) =
        ChannelEntity(id = id, providerId = 3, remoteId = "r$id", name = name, logo = logo, categoryId = cat,
            streamUrl = "http://fournisseur.invalid:8080/live/ZZUSERZZ/ZZPASSZZ/$id.ts", num = num)

    private fun epg(chId: Long, title: String, start: Long, end: Long) = EpgEntity(channelId = chId, title = title, description = "résumé secret", startMs = start, endMs = end)

    @Test fun colonnes_du_contrat() {
        assertEquals(listOf("provider_id", "remote_id", "name", "number", "logo", "now_title", "now_start_ms", "now_end_ms", "now_image",
            "next_title", "next_start_ms", "next_end_ms", "deep_link", "locked"), KairoNowPlan.COLUMNS.toList())
    }

    @Test fun favoris_d_abord_puis_recentes_sans_doublon_au_plus_20() {
        val favs = (1L..15L).map { ch(it) }
        val recent = listOf(ch(2)) + (100L..120L).map { ch(it) }
        val sel = KairoNowPlan.select(favs, recent, emptySet())
        assertEquals(20, sel.size)
        assertEquals((1L..15L).toList() + (100L..104L).toList(), sel.map { it.id })
    }

    @Test fun chaines_masquees_par_le_profil_ecartees() {
        // Profil enfant : les catégories adultes sont dans les catégories masquées du profil.
        val sel = KairoNowPlan.select(listOf(ch(1, cat = "adulte"), ch(2)), listOf(ch(3, cat = "adulte")), setOf("LIVE:3:adulte"))
        assertEquals(listOf(2L), sel.map { it.id })
    }

    @Test fun separateurs_et_chaines_poubelle_ecartes() {
        val sel = KairoNowPlan.select(listOf(ch(1, name = "##### FRANCE #####"), ch(2, name = "")), listOf(ch(3)), emptySet())
        assertEquals(listOf(3L), sel.map { it.id })
    }

    @Test fun ligne_complete() {
        val c = ch(7)
        val r = KairoNowPlan.rows(listOf(c), emptySet(), emptySet(), mapOf(7L to (epg(7, "Le journal", 1_000, 2_000) to epg(7, "Météo", 2_000, 2_500))), secrets).single()
        assertEquals(3L, r.providerId); assertEquals("r7", r.remoteId); assertEquals(c.title, r.name); assertEquals(7, r.number)
        assertEquals("https://logos.example/7.png", r.logo)
        assertEquals("Le journal", r.nowTitle); assertEquals(1_000L, r.nowStartMs); assertEquals(2_000L, r.nowEndMs); assertEquals("", r.nowImage)
        assertEquals("Météo", r.nextTitle); assertEquals(2_000L, r.nextStartMs); assertEquals(2_500L, r.nextEndMs)
        assertEquals("ultratv://live/3/r7", r.deepLink); assertEquals(0, r.locked)
        assertEquals(KairoNowPlan.COLUMNS.size, r.values().size)
        assertFalse(r.values().any { it is String && it.contains("résumé") })
    }

    @Test fun guide_vide_et_numero_inconnu() {
        val r = KairoNowPlan.rows(listOf(ch(8, num = 0)), emptySet(), emptySet(), emptyMap(), secrets).single()
        assertEquals(null, r.number); assertEquals("", r.nowTitle); assertEquals(0L, r.nowStartMs); assertEquals(0L, r.nextEndMs)
    }

    @Test fun chaine_verrouillee_sans_titres() {
        val e = mapOf(1L to (epg(1, "Programme", 1, 2) to epg(1, "Suivant", 2, 3)), 2L to (epg(2, "Programme", 1, 2) to epg(2, "Suivant", 2, 3)))
        val rows = KairoNowPlan.rows(listOf(ch(1), ch(2, cat = "verr")), setOf("3:r1"), setOf("verr"), e, secrets)
        rows.forEach { r ->
            assertEquals(1, r.locked); assertEquals("", r.nowTitle); assertEquals("", r.nextTitle)
            assertEquals(0L, r.nowStartMs); assertEquals(0L, r.nextStartMs)
        }
    }

    @Test fun logo_porteur_d_identifiants_vide() {
        assertEquals("", KairoNowPlan.safeLogo("http://fournisseur.invalid:8080/images/1.png", secrets))
        assertEquals("", KairoNowPlan.safeLogo("http://cdn.example/ZZUSERZZ/1.png", secrets))
        assertEquals("", KairoNowPlan.safeLogo("https://cdn.example/l.png?token=abc", secrets))
        assertEquals("", KairoNowPlan.safeLogo("https://u:p@cdn.example/l.png", secrets))
        assertEquals("", KairoNowPlan.safeLogo("file:///sdcard/l.png", secrets))
        assertEquals("", KairoNowPlan.safeLogo(null, secrets))
        assertEquals("https://cdn.example/logos/tf1%20hd.png", KairoNowPlan.safeLogo("https://cdn.example/logos/tf1%20hd.png", secrets))
    }

    @Test fun titres_ordinaires_gardes() {
        listOf("Journal de 20h", "Match : PSG - OM", "Les 4400", "Film: L'été 85", "20:30 Le JT", "beIN Sports 1 FHD")
            .forEach { assertEquals(it, KairoNowPlan.safeText(it, secrets)) }
    }

    /** Vecteurs partagés avec le Worker : aucune colonne ne fuit d'adresse de flux ni d'identifiant, même avec des données hostiles. */
    @Test fun aucune_colonne_ne_fuit_d_identifiant() {
        val file = File("../../cloudflare-config/test/fixtures/sanitize-vectors.json")
        check(file.isFile) { "Vecteurs partagés introuvables : ${file.absolutePath}" }
        val vectors = JSONArray(file.readText())
        assertTrue(vectors.length() > 100)
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val input = v.getString("input")
            val forbidden = v.getJSONArray("mustNotContain").let { a -> List(a.length()) { a.getString(it) } }.filter { it.isNotEmpty() }
            val c = ChannelEntity(id = 1, providerId = 3, remoteId = "42", name = input, logo = input, categoryId = "c", streamUrl = input, num = 1)
            val r = KairoNowPlan.rows(listOf(c), emptySet(), emptySet(), mapOf(1L to (epg(1, input, 1, 2) to epg(1, input, 2, 3))), secrets).single()
            val text = r.values().filterIsInstance<String>().filter { it != r.deepLink && it != r.remoteId }
            for (s in text) for (f in forbidden) assertFalse("${v.getString("name")} : « $f » dans « $s »", s.contains(f))
        }
    }

    @Test fun adresse_de_flux_jamais_exposee() {
        val r = KairoNowPlan.rows(listOf(ch(5)), emptySet(), emptySet(), emptyMap(), secrets).single()
        assertFalse(r.values().any { it is String && (it.contains("ZZUSERZZ") || it.contains("fournisseur.invalid") || it.contains(".ts")) })
    }
}
