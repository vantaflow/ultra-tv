package com.ultratv.tv.nativeapp.data.sport

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN = 60_000L
private const val T0 = 1_700_000_000_000L

class SportMatchingTest {
    private fun ch(id: Long, name: String = "Chaine $id", num: Int = 0, lang: String = "", country: String? = null): ChannelEntity {
        val base = ChannelEntity(id = id, providerId = 1, remoteId = "r$id", name = name, logo = null, categoryId = null, streamUrl = "u$id", num = num)
        return base.copy(lang = lang, country = country)
    }

    private fun e(c: ChannelEntity, title: String, start: Long = T0, sportChannel: Boolean = false) =
        SportEntry(c, EpgEntity(id = c.id * 100 + (start - T0) / MIN, channelId = c.id, title = title, description = null, startMs = start, endMs = start + 120 * MIN), sportChannel, true)

    private fun key(title: String) = SportMatching.parse(title).key
    private fun same(a: String, b: String, dMin: Long = 0) =
        SportMatching.sameEvent(SportMatching.parse(a), T0, SportMatching.parse(b), T0 + dMin * MIN)

    @Test fun francais_variantesDuMemeMatch() {
        val k = key("PSG - OM")
        assertEquals(k, key("Paris SG vs Marseille"))
        assertEquals(k, key("Ligue 1 : PSG / OM"))
        assertEquals(k, key("Ligue 1 : Paris Saint-Germain - Olympique de Marseille"))
        assertEquals(k, key("Direct : Paris SG contre OM (HD)"))
        assertEquals(k, key("OM - PSG"))   // l'ordre des équipes ne compte pas, l'horaire tranche
        assertEquals("ligue1", SportMatching.parse("Ligue 1 : PSG / OM").competition)
        assertEquals("ligue1", SportMatching.parse("FR | Ligue 1 Uber Eats - PSG - OM").competition)
    }

    @Test fun anglais_abreviationsCourantes() {
        assertEquals(key("Man Utd vs Liverpool"), key("Manchester United v Liverpool FC"))
        assertEquals(key("Man City vs Spurs"), key("Premier League: Manchester City - Tottenham Hotspur"))
        assertEquals(key("Real Madrid vs Barca"), key("Real Madrid CF - FC Barcelona"))
        assertEquals(key("Bayern Munich vs BVB"), key("FC Bayern München - Borussia Dortmund"))
        assertEquals(key("Inter vs Juve"), key("Inter Milan - Juventus FC 20:45"))
        assertEquals("epl", SportMatching.parse("Premier League: Man City v Spurs").competition)
    }

    @Test fun espagnol_et_portugais() {
        assertEquals(key("Atlético de Madrid vs Barcelona"), key("Atletico Madrid x FC Barcelona"))
        assertEquals("laliga", SportMatching.parse("LaLiga: Sevilla - Betis").competition)
    }

    @Test fun arabeTranslitere_et_arabe() {
        assertEquals(key("Al Ahly vs Zamalek"), key("الأهلي - الزمالك"))
        assertEquals(key("Al-Ahly SC vs Zamalek SC"), key("Ahly - Zamalek"))
        assertEquals(key("Wydad AC - Raja CA"), key("Wydad Casablanca vs Raja Casablanca"))
        assertEquals(key("Wydad AC - Raja CA"), key("الوداد - الرجاء"))
        assertEquals(key("Al Hilal - Al Nassr"), key("الهلال ضد النصر"))
        assertEquals("botola", SportMatching.parse("Botola Pro: Wydad - Raja").competition)
    }

    @Test fun horaire_toleranceDe15Minutes() {
        assertTrue(same("PSG - OM", "Paris SG vs Marseille", 0))
        assertTrue(same("PSG - OM", "Paris SG vs Marseille", 15))
        assertTrue(same("PSG - OM", "Paris SG vs Marseille", -15))
        assertFalse(same("PSG - OM", "Paris SG vs Marseille", 16))
        assertFalse(same("PSG - OM", "PSG - OM", 24 * 60))   // même affiche, autre jour (aller / retour)
    }

    @Test fun competitionsDifferentes_ontPasMelangees() {
        assertFalse(same("Ligue 1 : PSG - OM", "Coupe de France : PSG - OM"))
        assertTrue(same("Ligue 1 : PSG - OM", "PSG - OM"))   // compétition inconnue d'un côté : on garde
    }

    @Test fun equipesDifferentes_nonRapprochees() {
        assertFalse(same("PSG - OM", "PSG - Lyon"))
        assertFalse(same("Man City - Arsenal", "Man Utd - Arsenal"))
        assertFalse(same("Real Madrid - Barcelona", "Atletico Madrid - Barcelona"))
    }

    @Test fun sansEquipes_titreIdentiqueEtHoraireStrict() {
        assertTrue(same("UFC 300 : Main Card", "UFC 300 : Main Card (HD)", 0))
        assertTrue(same("Grand Prix de Monaco", "Grand Prix de Monaco - Live", 5))
        assertFalse(same("Grand Prix de Monaco", "Grand Prix de Monaco", 6))
        assertFalse(same("Football", "Basketball"))
        assertFalse(same("Magazine du sport", "Studio foot"))
        assertFalse(same("", "", 0))
    }

    @Test fun titreAvecTiretsDansUnNom_neSeCasseyPas() {
        val p = SportMatching.parse("Paris Saint-Germain - Marseille")
        assertTrue(p.isMatch)
        assertEquals(key("PSG - OM"), p.key)
        assertFalse(SportMatching.parse("PSG - PSG").isMatch)
        assertFalse(SportMatching.parse("Documentaire - Histoire - Culture").isMatch)
    }

    @Test fun competitionOf_reconnaitLesNomsCourants() {
        assertEquals("ucl", SportMatching.competitionOf("UEFA Champions League"))
        assertEquals("ucl", SportMatching.competitionOf("Ligue des Champions"))
        assertEquals("caf-cl", SportMatching.competitionOf("CAF Champions League"))
        assertEquals("laliga", SportMatching.competitionOf("La Liga EA Sports"))
        assertNull(SportMatching.competitionOf("Football"))
    }

    @Test fun canonTeam_statutsEtAccentsIgnores() {
        assertEquals("psg", SportMatching.canonTeam("Paris S.G."))
        assertEquals("bayern", SportMatching.canonTeam("Bayern München"))
        assertEquals(SportMatching.canonTeam("FC Nantes"), SportMatching.canonTeam("Nantes"))
        assertEquals("", SportMatching.canonTeam("  "))
    }

    @Test fun groups_regroupeParEvenement_etSepareLesAutres() {
        val a = ch(1); val b = ch(2); val c = ch(3); val d = ch(4)
        val list = listOf(
            e(a, "PSG - OM"), e(b, "Ligue 1 : Paris SG vs Marseille", T0 + 10 * MIN), e(c, "Lyon - Monaco"),
            e(d, "PSG - OM", T0 + 3 * 24 * 60 * MIN),
        )
        val g = SportMatching.groups(list)
        assertEquals(3, g.size)
        assertEquals(setOf(1L, 2L), g.first { it.size == 2 }.map { it.channel.id }.toSet())
    }

    @Test fun alsoOn_autresChainesSeulement_triees() {
        val a = ch(1, "TV A", num = 5); val b = ch(2, "TV B", num = 3); val c = ch(3, "TV C", num = 1)
        val hd = ch(4, "TV D FHD").copy(quality = 3)
        val x = ch(5, "Autre")
        val list = listOf(e(a, "PSG - OM"), e(b, "Paris SG vs Marseille", T0 + 5 * MIN), e(c, "PSG / OM"), e(hd, "PSG-OM".replace("-", " - ")), e(x, "Lyon - Nice"))
        val out = SportMatching.alsoOn(list[0], list)
        assertEquals(listOf(4L, 3L, 2L), out.map { it.channel.id })   // qualité d'abord, puis numéro ; sans la chaîne elle-même
        assertTrue(SportMatching.alsoOn(list[4], list).isEmpty())
    }

    @Test fun alsoOn_uneEntreeParChaine() {
        val a = ch(1); val b = ch(2)
        val list = listOf(e(a, "PSG - OM"), e(b, "PSG - OM"), e(b, "Paris SG vs Marseille", T0 + 5 * MIN))
        assertEquals(1, SportMatching.alsoOn(list[0], list).size)
    }

    @Test fun alsoOnMap_clePourChaqueMembre() {
        val a = ch(1); val b = ch(2); val c = ch(3)
        val list = listOf(e(a, "PSG - OM"), e(b, "Paris SG vs Marseille"), e(c, "Autre chose"))
        val m = SportMatching.alsoOnMap(list)
        assertEquals(listOf(2L), m[SportMatching.entryKey(list[0])]!!.map { it.channel.id })
        assertEquals(listOf(1L), m[SportMatching.entryKey(list[1])]!!.map { it.channel.id })
        assertNull(m[SportMatching.entryKey(list[2])])
    }

    @Test fun collapse_gardeLaPremiere_etConserveLOrdre() {
        val a = ch(1); val b = ch(2); val c = ch(3)
        val list = listOf(e(a, "Lyon - Nice"), e(b, "PSG - OM"), e(c, "Paris SG vs Marseille", T0 + MIN))
        assertEquals(listOf(1L, 2L), SportMatching.collapse(list).map { it.channel.id })
        assertTrue(SportMatching.collapse(emptyList()).isEmpty())
    }

    @Test fun badgesOf_qualiteEtLangue() {
        assertEquals("FHD · FR", SportMatching.badgesOf(ch(1, lang = "fr").copy(quality = 3)))
        assertEquals("HD · MA", SportMatching.badgesOf(ch(1, country = "MA").copy(quality = 2)))
        assertEquals("", SportMatching.badgesOf(ch(1)))
        assertEquals("4K", SportMatching.badgesOf(ch(1).copy(quality = 4)))
    }
}
