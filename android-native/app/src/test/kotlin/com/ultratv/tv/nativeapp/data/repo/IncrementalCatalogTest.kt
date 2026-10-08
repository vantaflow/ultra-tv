package com.ultratv.tv.nativeapp.data.repo

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Synchro incrémentale des films : identifiants gardés, seules les lignes modifiées réécrites, disparus supprimés. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class IncrementalCatalogTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), UltraDb::class.java).allowMainThreadQueries().build()
    @After fun close() = db.close()

    private fun movie(rid: String, name: String, backdrop: String? = null) =
        MovieEntity(providerId = 7, remoteId = rid, name = name, poster = null, categoryId = "c", streamUrl = "u/$rid", container = "mp4", year = 2020, rating = 7.0, plot = null, backdrop = backdrop)

    private fun pass(rows: List<MovieEntity>): Triple<Int, Int, Int> = runBlocking {
        db.withTransaction {
            val p = IncrementalTable.MOVIES.begin(db.openHelper.writableDatabase, 7)
            p.write(rows)
            val removed = p.finish()
            Triple(p.inserted, p.updated, removed)
        }
    }

    @Test
    fun deuxiemeSynchro_garderLesIds_majSeulementLesChangements_supprimerLesDisparus() = runBlocking {
        assertEquals(Triple(3, 0, 0), pass(listOf(movie("1", "Alpha"), movie("2", "Bravo"), movie("3", "Charlie", backdrop = "b3"))))
        val idsBefore = listOf("1", "2", "3").associateWith { db.movieDao().byRemoteId(7, it)!!.id }

        // « 2 » renommé, « 3 » sans fond dans la liste (enrichi plus tard : on le garde), « 1 » retiré, « 4 » ajouté.
        val r = pass(listOf(movie("2", "Bravo Nouveau"), movie("3", "Charlie"), movie("4", "Delta")))
        assertEquals(1, r.first)    // 4
        assertEquals(1, r.second)   // 2 seulement : 3 est inchangé
        assertEquals(1, r.third)    // 1

        assertEquals(null, db.movieDao().byRemoteId(7, "1"))
        assertEquals(idsBefore["2"], db.movieDao().byRemoteId(7, "2")!!.id)
        assertEquals("Bravo Nouveau", db.movieDao().byRemoteId(7, "2")!!.name)
        assertEquals("b3", db.movieDao().byRemoteId(7, "3")!!.backdrop)
        assertEquals(4L, db.movieDao().byRemoteId(7, "4")!!.addedKey)
        // Recherche plein texte à jour (déclencheurs FTS) : nouveau nom trouvé, film supprimé introuvable.
        assertEquals(listOf("Bravo Nouveau"), db.movieDao().searchFts(7, "nouveau*", 10).map { it.name })
        assertEquals(emptyList<String>(), db.movieDao().searchFts(7, "alpha*", 10).map { it.name })
    }

    @Test
    fun autreSource_jamaisTouchee() = runBlocking {
        pass(listOf(movie("1", "Alpha")))
        db.movieDao().upsertAll(listOf(movie("1", "Autre source").copy(providerId = 8)))
        pass(emptyList())
        assertEquals(null, db.movieDao().byRemoteId(7, "1"))
        assertEquals("Autre source", db.movieDao().byRemoteId(8, "1")!!.name)
    }

    @Test
    fun deuxiemeSynchro_addedKeyChange_metAJourLOrdreDesDerniersAjouts() = runBlocking {
        // Avant le correctif, addedKey valait l'identifiant : « 2 » (ajouté récemment) passait après « 1 ».
        pass(listOf(movie("1", "Alpha"), movie("2", "Bravo")))
        pass(listOf(movie("1", "Alpha").copy(addedKey = 1_700_000_000), movie("2", "Bravo").copy(addedKey = 1_600_000_000), movie("3", "Charlie").copy(addedKey = 1_760_000_000)))
        val latest = db.movieDao().observeLatest(7, 15).first().map { it.remoteId }
        assertEquals(listOf("3", "1", "2"), latest)
    }
}
