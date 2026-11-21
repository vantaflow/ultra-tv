package com.ultratv.tv.nativeapp.data.stalker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Identité de boîtier stockée avec les identifiants : format d'avant inchangé, identité gardée lors d'une mise à jour cloud. */
class StalkerIdentityTest {
    private val mac = "00:1A:79:AB:CD:EF"

    @Test fun sansIdentite_formatInchange() {
        assertEquals("", StalkerCreds.pack("", ""))
        assertEquals("u\u0000p", StalkerCreds.pack("u", "p"))
        assertEquals("u" to "p", StalkerCreds.unpack("u\u0000p"))
        assertEquals("" to "seul", StalkerCreds.unpack("seul"))
        assertTrue(StalkerCreds.identity("u\u0000p").isEmpty)
        assertTrue(StalkerCreds.identity("").isEmpty)
    }

    @Test fun avecIdentite_relueEtIdentifiantsIntacts() {
        val id = StalkerIdentity("SN1", "D1", "D2", "SIG")
        val packed = StalkerCreds.pack("", "", id)
        assertEquals("" to "", StalkerCreds.unpack(packed))
        assertEquals(id, StalkerCreds.identity(packed))
        val withLogin = StalkerCreds.pack("u", "p", id)
        assertEquals("u" to "p", StalkerCreds.unpack(withLogin))
        assertEquals(id, StalkerCreds.identity(withLogin))
    }

    @Test fun miseAJourCloud_gardeLIdentiteLocale() {
        val local = StalkerCreds.pack("old", "old", StalkerIdentity(sn = "SN1"))
        val merged = StalkerCreds.keepIdentity(StalkerCreds.pack("new", "pw"), local)
        assertEquals("new" to "pw", StalkerCreds.unpack(merged))
        assertEquals("SN1", StalkerCreds.identity(merged).sn)
        assertEquals("a\u0000b", StalkerCreds.keepIdentity("a\u0000b", "x\u0000y"))
    }

    @Test fun appareil_valeursSaisiesSinonCalculees() {
        assertEquals(StalkerDevice(mac), StalkerDevice.of(mac, StalkerIdentity()))
        val d = StalkerDevice.of(mac, StalkerIdentity(sn = "SN1"))
        assertEquals("SN1", d.sn)
        assertEquals(StalkerDevice(mac).deviceId, d.deviceId)
        assertEquals(StalkerDevice.sha256("SN1$mac").uppercase(), d.signature)
        assertEquals("StalkerIdentity(custom)", StalkerIdentity(sn = "x").toString())
    }
}
