package com.ultratv.tv.nativeapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La télémétrie ne quitte jamais la machine pendant les tests : seul un serveur local est accepté sous Robolectric. */
class RemoteLogTestIsolationTest {
    @Test fun sousRobolectric_unServeurDistantEstRefuse() {
        assertTrue(RemoteLog.blockedUnderTest("https://exemple.invalid", "robolectric"))
        assertTrue(RemoteLog.blockedUnderTest("https://api.exemple.invalid/chemin", "robolectric"))
        assertTrue(RemoteLog.blockedUnderTest("pas une adresse", "robolectric"))
    }

    @Test fun sousRobolectric_leServeurLocalDuTestEstAccepte() {
        assertFalse(RemoteLog.blockedUnderTest("http://localhost:8080", "robolectric"))
        assertFalse(RemoteLog.blockedUnderTest("http://127.0.0.1:53211", "robolectric"))
    }

    @Test fun surUnVraiAppareil_rienNestBloque() {
        assertFalse(RemoteLog.blockedUnderTest("https://exemple.invalid", "google/sabrina/sabrina:14/x:user/release-keys"))
        assertFalse(RemoteLog.blockedUnderTest("https://exemple.invalid", null))
    }
}
