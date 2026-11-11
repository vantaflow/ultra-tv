package com.ultratv.tv.nativeapp.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ultratv.tv.nativeapp.data.m3u.DrmScheme
import com.ultratv.tv.nativeapp.data.m3u.StreamProps
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 20 → 21 : colonne `channel.props` (flux protégés), nulle pour l'existant, données intactes. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ChannelPropsMigration21Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UltraDb::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun migrate20to21_colonneNulle_chaineExistanteIntacte() {
        helper.createDatabase("p21", 20).apply {
            execSQL("INSERT INTO channel (providerId, remoteId, name, logo, categoryId, streamUrl, epgChannelId, catchupSource, catchupDays, userPosition, title, sortKey, num, seq, junk, isSeparator, country, quality, flags, lang, catRank) VALUES (1, 'c1', 'Chaîne', NULL, NULL, 'http://serveur-fictif.invalid/1.ts', NULL, NULL, 0, 0, 'Chaîne', 'chaine', 0, 0, 0, 0, NULL, 0, 0, '', 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate("p21", 21, true, MIGRATION_20_21)
        db.query("SELECT name, props FROM channel WHERE remoteId = 'c1'").use { it.moveToFirst(); assertEquals("Chaîne", it.getString(0)); assertEquals(true, it.isNull(1)) }
    }

    @Test fun chaineComplete_baseMigreeParRoom_ecritureEtLecturedesProprietes() = runBlocking {
        helper.createDatabase("p21full", 20).close()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.databaseBuilder(ctx, UltraDb::class.java, "p21full").addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        val props = StreamProps(DrmScheme.WIDEVINE, "https://lic.invalid/wv")
        room.channelDao().upsertAll(listOf(
            ChannelEntity(providerId = 3, remoteId = "p", name = "Protégée", logo = null, categoryId = null, streamUrl = "http://s.invalid/a.mpd", props = props.toJson()),
            ChannelEntity(providerId = 3, remoteId = "o", name = "Ordinaire", logo = null, categoryId = null, streamUrl = "http://s.invalid/a.ts"),
        ))
        assertEquals(props, room.channelDao().streamProps("3:p"))
        assertNull(room.channelDao().streamProps("3:o"))
        assertNull(room.channelDao().streamProps("3:absente"))
        room.close()
    }
}
