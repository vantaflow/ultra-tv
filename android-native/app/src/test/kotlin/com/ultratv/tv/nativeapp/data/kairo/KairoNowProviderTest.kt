package com.ultratv.tv.nativeapp.data.kairo

import android.content.ContentValues
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Le fournisseur refuse tout appelant qui n'est pas Kairo (curseur vide, colonnes du contrat) et toute écriture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class KairoNowProviderTest {
    private val authority = "com.ultratv.tv.nativeapp.debug.kairo"
    private val provider = Robolectric.setupContentProvider(KairoNowProvider::class.java, authority)

    @Test fun appelant_non_kairo_recoit_un_curseur_vide() {
        provider.query(Uri.parse("content://$authority/now"), null, null, null, null).use { c ->
            assertEquals(0, c.count)
            assertEquals(KairoNowPlan.COLUMNS.toList(), c.columnNames.toList())
        }
        provider.query(Uri.parse("content://$authority/profile"), null, null, null, null).use { c ->
            assertEquals(0, c.count)
            assertEquals(listOf("kids"), c.columnNames.toList())
        }
    }

    @Test fun ecritures_refusees() {
        val uri = Uri.parse("content://$authority/now")
        assertTrue(runCatching { provider.insert(uri, ContentValues()) }.exceptionOrNull() is UnsupportedOperationException)
        assertTrue(runCatching { provider.update(uri, ContentValues(), null, null) }.exceptionOrNull() is UnsupportedOperationException)
        assertTrue(runCatching { provider.delete(uri, null, null) }.exceptionOrNull() is UnsupportedOperationException)
    }
}
