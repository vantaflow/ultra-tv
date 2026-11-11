package com.ultratv.tv.nativeapp.data.config

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstallIdTest {
    private fun prefs(name: String) = RuntimeEnvironment.getApplication().getSharedPreferences(name, Context.MODE_PRIVATE).also { it.edit().clear().commit() }

    @Test fun `genere un identifiant stable une fois cree`() {
        val sp = prefs("install_a")
        val a = InstallId.of(sp)
        assertTrue(a.length >= 16)
        assertEquals(a, InstallId.of(sp))
    }

    @Test fun `reutilise l'identifiant deja stocke par les alertes`() {
        val sp = prefs("install_b")
        sp.edit().putString("install", "deja-la-12345678-abcdef").commit()
        assertEquals("deja-la-12345678-abcdef", InstallId.of(sp))
    }

    @Test fun `deux installations ont des identifiants differents`() = assertNotEquals(InstallId.of(prefs("install_c")), InstallId.of(prefs("install_d")))
}
