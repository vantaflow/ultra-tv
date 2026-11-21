package com.ultratv.tv.nativeapp.data.stalker

import com.ultratv.tv.nativeapp.data.stalker.StalkerClient.IdMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Lecture de `get_profile` : profil bloqué ou accepté, ordre des essais d'identité. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StalkerProfileTest {
    @Test fun bloque_statusMessageSansId() {
        assertTrue(StalkerProfile.blocked("""{"js":{"status":1,"msg":"Device conflict"}}"""))
        assertTrue(StalkerProfile.blocked("""{"js":{"status":"2","msg":"<b>Account blocked</b>","id":null}}"""))
    }

    @Test fun accepte_avecIdOuSansMessageOuStatusNul() {
        assertFalse(StalkerProfile.blocked("""{"js":{"id":"5","status":1,"msg":"Subscription ends soon"}}"""))
        assertFalse(StalkerProfile.blocked("""{"js":{"status":0,"msg":"ok"}}"""))
        assertFalse(StalkerProfile.blocked("""{"js":{"status":1}}"""))
        assertFalse(StalkerProfile.blocked("""{"js":[]}"""))
        assertFalse(StalkerProfile.blocked("Authorization failed."))
    }

    @Test fun ordreDesEssais() {
        assertEquals(listOf(IdMode.DERIVED, IdMode.BARE, IdMode.LOWER), StalkerProfile.modeOrder(null))
        assertEquals(listOf(IdMode.LOWER, IdMode.DERIVED, IdMode.BARE), StalkerProfile.modeOrder(IdMode.LOWER))
    }
}
