package com.ultratv.tv.nativeapp.data.profile

import com.ultratv.tv.nativeapp.data.profile.ProfileSyncLogic.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileScreenTimeSyncTest {
    private val local = listOf(ProfileEntity(id = 1, name = "Léa", color = 1, initial = "L", isKids = true, createdAt = 100))

    @Test fun reglesRecuesAilleurs_appliquees_ici() {
        val remote = listOf(SharedProfile("Léa", 1, true, 500, t = "L60;1=480-1200"))
        assertEquals(listOf(Action.Update(1, remote[0])), ProfileSyncLogic.plan(local, emptyMap(), remote, screenTimes = emptyMap()))
        // Mêmes règles : rien à faire.
        assertTrue(ProfileSyncLogic.plan(local, emptyMap(), remote, screenTimes = mapOf(1L to "L60;1=480-1200")).isEmpty())
    }

    @Test fun reglesRetireesAilleurs_effacent_les_regles_locales_seulement_si_plus_recent() {
        val remote = listOf(SharedProfile("Léa", 1, true, 500, t = ""))
        assertEquals(1, ProfileSyncLogic.plan(local, emptyMap(), remote, screenTimes = mapOf(1L to "L60")).size)
        // Modifié ici après (600) : la version distante plus ancienne ne gagne pas.
        val log = mapOf("léa" to SharedProfile("Léa", 0, false, 600, true))
        assertTrue(ProfileSyncLogic.plan(local, log, remote, screenTimes = mapOf(1L to "L60")).isEmpty())
    }

    @Test fun ancienneVersion_sans_champ_t_ne_touche_pas_aux_regles() {
        val remote = listOf(SharedProfile("Léa", 1, true, 500))
        assertTrue(ProfileSyncLogic.plan(local, emptyMap(), remote, screenTimes = mapOf(1L to "L60")).isEmpty())
    }

    @Test fun envoi_et_json_aller_retour_avec_champ_facultatif() {
        val sent = ProfileSyncLogic.local(local, emptyMap(), screenTimes = mapOf(1L to "L60;6="))
        assertEquals("L60;6=", sent.first().t)
        assertEquals(sent, ProfileSyncLogic.parse(ProfileSyncLogic.toJson(sent)))
        // Pas de règle : champ vide explicite (efface les règles sur les autres appareils).
        assertEquals("", ProfileSyncLogic.local(local, emptyMap()).first().t)
        // Un JSON d'ancienne version (sans « t ») donne t = null.
        assertNull(ProfileSyncLogic.parse("""{"profiles":[{"n":"Léa","c":1,"k":true,"at":5,"on":true}]}""").first().t)
    }
}
