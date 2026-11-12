package com.ultratv.tv.nativeapp.data.sport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportDetectorTest {
    private fun sport(title: String, desc: String? = null) = SportDetector.isSportProgramme(title, desc)

    @Test fun fold_accentsCasseEtLettresSpeciales() {
        assertEquals("ligue 1 elements", SportDetector.fold("  LIGUE 1 – Éléments "))
        assertEquals("futbol", SportDetector.fold("FÚTBOL"))
        assertEquals("pilka nozna", SportDetector.fold("Piłka nożna"))
        assertEquals("sport", SportDetector.fold("𝗦𝗣𝗢𝗥𝗧"))   // lettres « fantaisie » des noms de chaînes
    }

    @Test fun programme_francais() {
        assertTrue(sport("Le Journal des Sports"))
        assertTrue(sport("Ligue 1 : PSG - OM"))
        assertTrue(sport("Rugby : Top 14"))
        assertTrue(sport("Live: Lyon - Marseille"))
        assertTrue(sport("Direct : Wydad - Raja"))
        assertTrue(sport("Real Madrid vs FC Barcelone"))
        assertTrue(sport("Barça - Real Madrid"))
        assertTrue(sport("Tennis - Roland Garros"))
        assertTrue(sport("FORMULE 1 : Grand Prix de Monaco"))
    }

    @Test fun programme_anglais() {
        assertTrue(sport("Premier League: Arsenal v Chelsea") || sport("Premier League: Arsenal vs Chelsea"))
        assertTrue(sport("NBA: Lakers vs Celtics"))
        assertTrue(sport("UFC 300 Main Event"))
        assertTrue(sport("MotoGP Qualifying"))
        assertTrue(sport("Live: Manchester United vs Liverpool"))
        assertTrue(sport("World Cup Highlights"))
    }

    @Test fun programme_espagnol() {
        assertTrue(sport("Fútbol: Sevilla - Betis"))
        assertTrue(sport("Baloncesto ACB"))
        assertTrue(sport("En directo: Valencia vs Villarreal"))
        assertTrue(sport("Deportes Cuatro"))
    }

    @Test fun programme_arabe() {
        assertTrue(sport("مباراة الأهلي والزمالك"))
        assertTrue(sport("كرة القدم: المغرب ضد الجزائر"))
        assertTrue(sport("الدوري الإسباني"))
        assertTrue(sport("النشرة الرياضية"))
    }

    @Test fun programme_polonais() {
        assertTrue(sport("Mecz Ekstraklasy: Legia - Lech"))
        assertTrue(sport("Piłka nożna: Liga Mistrzów"))
        assertTrue(sport("Koszykówka: Energa Basket Liga") || sport("Siatkówka: PlusLiga"))
    }

    @Test fun programme_faussesAlertes() {
        assertFalse(sport("Transport en commun"))
        assertFalse(sport("Passport to Paradise"))
        assertFalse(sport("Support technique"))
        assertFalse(sport("Godzilla vs Kong"))
        assertFalse(sport("Paris - New York"))
        assertFalse(sport("Mon Mercredi - Le Film"))
        assertFalse(sport("Live: Concert Coldplay"))
        assertFalse(sport("Les Feux de l'amour"))
        assertFalse(sport("   "))
        assertFalse(sport("eSports Weekly"))
    }

    @Test fun programme_tele_achat_ecarte_meme_sur_chaine_de_sport() {
        assertFalse(SportDetector.isSportProgramme("Téléachat", onSportChannel = true))
        assertFalse(SportDetector.isSportProgramme("Teleshopping Sport", onSportChannel = true))
        assertTrue(SportDetector.isSportProgramme("Plateau d'avant-match", onSportChannel = true))
    }

    @Test fun programme_descriptionNeCompteQuePourLesCompetitions() {
        assertTrue(sport("Match du jour", "Retransmission de la Premier League"))
        assertFalse(sport("Le grand film", "Un champion de tennis revient chez lui"))
        // au-delà des 120 premiers caractères, rien n'est lu
        assertFalse(sport("Le grand film", "x".repeat(130) + " Premier League"))
    }

    @Test fun chaine_parNom() {
        for (n in listOf("beIN SPORTS 1 HD", "FR | Canal+ Sport 360", "RMC Sport 2", "DAZN 1", "Eurosport 1", "SuperSport Premier League", "Sky Sports Main Event",
            "Arena Sport 1", "ESPN 2", "TSN 3", "Sport TV 1", "AD Sports 1", "SSC 2", "beIN 1 HD", "Canal+ Foot", "Arryadia", "Movistar Deportes")) {
            assertTrue(n, SportDetector.isSportChannel(n, null))
        }
        assertTrue(SportDetector.isSportChannel("بي ان سبورت 1", null))
    }

    @Test fun chaine_parCategorie() {
        assertTrue(SportDetector.isSportChannel("Chaine X", "FR | SPORTS"))
        assertTrue(SportDetector.isSportChannel("Chaine X", "قنوات رياضية"))
        assertTrue(SportDetector.isSportChannel("Chaine X", "DEPORTES"))
        assertTrue(SportDetector.isSportChannel("Chaine X", "PL | Sportowe") )
    }

    @Test fun chaine_fausseAlerte() {
        assertFalse(SportDetector.isSportChannel("Transport TV", null))
        assertFalse(SportDetector.isSportChannel("beIN Movies 1", "Cinéma"))
        assertFalse(SportDetector.isSportChannel("Movies HD", "Cinema"))
        assertFalse(SportDetector.isSportChannel("eSports TV", null))
        assertFalse(SportDetector.isSportChannel("Teleshopping Sport", null))
        assertFalse(SportDetector.isSportChannel(null, null))
    }

    @Test fun motifsLike_sontDesLikeValides() {
        // pas d'apostrophe (requête paramétrée de toute façon) et chacun avec au moins un caractère littéral
        for (p in SportDetector.TITLE_LIKE + SportDetector.CHANNEL_LIKE) assertTrue(p, p.replace("%", "").replace("_", "").isNotBlank())
    }
}
