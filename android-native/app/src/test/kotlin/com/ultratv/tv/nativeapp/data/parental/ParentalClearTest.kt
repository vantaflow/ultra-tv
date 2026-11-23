package com.ultratv.tv.nativeapp.data.parental

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Effacer ou remplacer le code parental exige le code ACTUEL : sans lui, rien ne change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ParentalClearTest {
    private lateinit var store: ParentalStore

    @Before fun setUp() = runBlocking {
        store = ParentalStore(ApplicationProvider.getApplicationContext())
        store.setPin("1234")
    }

    @Test fun bonCode_efface() = runBlocking {
        assertTrue(store.clearWith("1234"))
        assertFalse(store.isSet())
    }

    @Test fun mauvaisCode_inchange() = runBlocking {
        assertFalse(store.clearWith("0000"))
        assertTrue(store.isSet())
        assertTrue(store.check("1234"))
    }

    @Test fun annulation_inchange() = runBlocking {
        // Annuler la saisie = aucun appel : le code reste celui d'avant.
        assertTrue(store.isSet())
        assertTrue(store.check("1234"))
    }

    @Test fun remplacement_exigeLeCodeActuel() = runBlocking {
        assertFalse(store.changeWith("0000", "5678"))
        assertTrue(store.check("1234"))
        assertTrue(store.changeWith("1234", "5678"))
        assertTrue(store.check("5678"))
        assertFalse(store.changeWith("5678", ""))   // jamais d'effacement déguisé en remplacement
        assertTrue(store.isSet())
    }
}
