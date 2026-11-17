package com.ultratv.tv.nativeapp.data.profile

import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.profile.ProfileSyncLogic.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileSourcesTest {
    private val local = listOf(ProfileEntity(id = 1, name = "Principal", color = 1, initial = "P", createdAt = 100))
    private fun p(id: Long, name: String, active: Boolean = false) =
        ProviderEntity(id = id, name = name, kind = "XTREAM", baseUrl = "u", username = "", password = "", active = active)

    @Test fun sourcesChoisiesAilleurs_appliqueesIci_etJsonAllerRetour() {
        val remote = listOf(SharedProfile("Principal", 1, false, 500, s = listOf("A", "B")))
        assertEquals(listOf(Action.Update(1, remote[0])), ProfileSyncLogic.plan(local, emptyMap(), remote, emptyMap(), mapOf(1L to listOf("A"))))
        assertTrue(ProfileSyncLogic.plan(local, emptyMap(), remote, emptyMap(), mapOf(1L to listOf("A", "B"))).isEmpty())
        assertEquals(listOf("A", "B"), ProfileSyncLogic.parse(ProfileSyncLogic.toJson(remote)).first().s)
        // Absent (ancienne version) : « toutes », et rien n'est envoyé.
        assertEquals(null, ProfileSyncLogic.parse(ProfileSyncLogic.toJson(listOf(SharedProfile("Papa", 1, false, 5)))).first().s)
        assertEquals(listOf("A"), ProfileSyncLogic.local(local, emptyMap(), emptyMap(), mapOf(1L to listOf("A"))).first().s)
    }

    @Test fun choixRetire_ailleurs_revientATouteLesSources() {
        val remote = listOf(SharedProfile("Principal", 1, false, 500))
        assertEquals(1, ProfileSyncLogic.plan(local, emptyMap(), remote, emptyMap(), mapOf(1L to listOf("A"))).size)
    }

    @Test fun sourcesVisibles_etSourceActive() {
        val all = listOf(p(1, "Alpha", active = true), p(2, "Beta"), p(3, "Gamma"))
        assertEquals(all, ProfileSourcesRules.visible(all, null))
        assertEquals(listOf(2L, 3L), ProfileSourcesRules.visible(all, listOf("beta", " GAMMA ")).map { it.id })
        // Noms inconnus de cet appareil : jamais de catalogue vide.
        assertEquals(all, ProfileSourcesRules.visible(all, listOf("Inconnue")))
        // L'active (Alpha) n'est pas visible : on prend la première visible ; sinon on ne touche à rien.
        assertEquals(2L, ProfileSourcesRules.activeToSet(all, listOf("Beta", "Gamma")))
        assertEquals(null, ProfileSourcesRules.activeToSet(all, listOf("Alpha", "Beta")))
        assertEquals(null, ProfileSourcesRules.activeToSet(all, null))
    }

    @Test fun cocher_decocher() {
        val names = listOf("A", "B", "C")
        assertEquals(listOf("B", "C"), ProfileSourcesRules.toggle(names, null, "A"))
        assertEquals(null, ProfileSourcesRules.toggle(names, listOf("B", "C"), "A"))
        assertEquals(null, ProfileSourcesRules.toggle(names, listOf("B"), "B"))
        assertEquals(listOf("A", "B", "C"), ProfileSourcesRules.decode(ProfileSourcesRules.encode(listOf("A", "B", "C"))))
        assertEquals(null, ProfileSourcesRules.decode(""))
    }

    @Test fun sourcePlexAjoutee_sansChoixDeProfil_estVisible_etAvecChoixElleEstFiltreeJusquAEnsureVisible() {
        val xt = p(1, "STRONG", active = true); val plex = p(2, "JDM Plex Server").copy(kind = "PLEX")
        assertEquals(listOf(1L, 2L), ProfileSourcesRules.visible(listOf(xt, plex), null).map { it.id })
        // Le profil avait choisi ses sources : la nouvelle source est masquée tant que son nom n'est pas ajouté au choix.
        assertEquals(listOf(1L), ProfileSourcesRules.visible(listOf(xt, plex), listOf("STRONG")).map { it.id })
        assertEquals(listOf(1L, 2L), ProfileSourcesRules.visible(listOf(xt, plex), listOf("STRONG", "JDM Plex Server")).map { it.id })
    }
}
