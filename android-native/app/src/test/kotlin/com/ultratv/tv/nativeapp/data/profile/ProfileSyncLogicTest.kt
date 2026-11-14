package com.ultratv.tv.nativeapp.data.profile

import com.ultratv.tv.nativeapp.data.profile.ProfileSyncLogic.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileSyncLogicTest {
    private fun prof(id: Long, name: String, color: Int = 1, kids: Boolean = false, created: Long = 100) = ProfileEntity(id = id, name = name, color = color, initial = name.take(1), isKids = kids, createdAt = created)

    @Test fun profilCreeAilleurs_estCreeIci() {
        val acts = ProfileSyncLogic.plan(listOf(prof(1, "Principal")), emptyMap(), listOf(SharedProfile("Principal", 1, false, 100), SharedProfile("Enfants", 7, true, 500)))
        assertEquals(listOf(Action.Create(SharedProfile("Enfants", 7, true, 500))), acts)
    }

    @Test fun suppressionPlusRecente_supprime_maisPasSiModifieIciApres() {
        val local = listOf(prof(1, "Principal"), prof(2, "Invité", created = 200))
        assertEquals(listOf(Action.Delete(2, SharedProfile("invité", 0, false, 300, false))),
            ProfileSyncLogic.plan(local, emptyMap(), listOf(SharedProfile("invité", 0, false, 300, false))))
        // Modifié ici à 400 : la suppression de 300 ne s'applique pas.
        val log = mapOf("invité" to SharedProfile("Invité", 0, false, 400, true))
        assertTrue(ProfileSyncLogic.plan(local, log, listOf(SharedProfile("invité", 0, false, 300, false))).isEmpty())
    }

    @Test fun supprimeIci_neRevientPas_etEstEnvoyeCommeTombe() {
        val log = mapOf("invité" to SharedProfile("Invité", 0, false, 900, false))
        assertTrue(ProfileSyncLogic.plan(listOf(prof(1, "Principal")), log, listOf(SharedProfile("Invité", 3, false, 200))).isEmpty())
        val sent = ProfileSyncLogic.local(listOf(prof(1, "Principal")), log)
        assertEquals(listOf("Principal" to true, "Invité" to false), sent.map { it.n to it.on })
    }

    @Test fun couleurPlusRecente_misAJour_etJsonAllerRetour() {
        val acts = ProfileSyncLogic.plan(listOf(prof(1, "Papa", color = 1)), emptyMap(), listOf(SharedProfile("Papa", 9, true, 500)))
        assertEquals(listOf(Action.Update(1, SharedProfile("Papa", 9, true, 500))), acts)
        val list = listOf(SharedProfile("Papa", -16711936, true, 5), SharedProfile("Vieux", 0, false, 6, false))
        assertEquals(list, ProfileSyncLogic.parse(ProfileSyncLogic.toJson(list)))
    }
}

@RunWith(RobolectricTestRunner::class)
class ProfileRenameSyncTest {
    @Test fun renommeAilleurs_renommeIci_sansSupprimer() {
        val local = listOf(ProfileEntity(id = 1, name = "Principal", color = 1, initial = "P", createdAt = 100), ProfileEntity(id = 2, name = "Papa", color = 3, initial = "P", createdAt = 100))
        val remote = listOf(SharedProfile("Principal", 1, false, 100), SharedProfile("Papa 2", 3, false, 500, true, from = "Papa"), SharedProfile("Papa", 0, false, 500, false))
        assertEquals(listOf(Action.Rename(2, SharedProfile("Papa 2", 3, false, 500, true, "Papa"))), ProfileSyncLogic.plan(local, emptyMap(), remote))
    }
}

@RunWith(RobolectricTestRunner::class)
class ProfileLanguageSyncTest {
    @Test fun langueChoisieAilleurs_appliqueeIci() {
        val local = listOf(ProfileEntity(id = 1, name = "Principal", color = 1, initial = "P", createdAt = 100))
        val remote = listOf(SharedProfile("Principal", 1, false, 500, l = "fr"))
        assertEquals(listOf(Action.Update(1, SharedProfile("Principal", 1, false, 500, l = "fr"))), ProfileSyncLogic.plan(local, emptyMap(), remote, mapOf(1L to "ar")))
        // Même langue : rien à faire.
        assertTrue(ProfileSyncLogic.plan(local, emptyMap(), remote, mapOf(1L to "fr")).isEmpty())
        assertEquals("fr", ProfileSyncLogic.parse(ProfileSyncLogic.toJson(remote)).first().l)
    }
}
