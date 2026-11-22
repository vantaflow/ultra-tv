package com.ultratv.tv.nativeapp.data.kairo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seul Kairo, signé par la clé convenue, lit le fournisseur « en cours / suivant ». */
class KairoAccessTest {
    private val good = "b5c0901798aa6a67b3cd619c56c27057431eec2681c9b1799f9afc2aba5e8c3e"

    @Test fun empreinte_figee_seule_entree() {
        // Revérifiée sur les APK Kairo 1.26 et 1.27 (apksigner) : ne change que par une nouvelle version d'Ultra TV.
        assertEquals(listOf(good), KairoAccess.ALLOWED_SHA256)
        assertEquals("com.vantaflow.kairo", KairoAccess.PACKAGE)
    }

    @Test fun appelant_autorise() {
        assertTrue(KairoAccess.isAllowed("com.vantaflow.kairo", listOf("com.vantaflow.kairo"), listOf(good)))
        // Empreinte au format keytool (majuscules, deux-points) acceptée.
        val colons = good.uppercase().chunked(2).joinToString(":")
        assertTrue(KairoAccess.isAllowed("com.vantaflow.kairo", listOf("com.vantaflow.kairo"), listOf(colons)))
    }

    @Test fun paquet_inconnu_refuse() {
        assertFalse(KairoAccess.isAllowed("com.exemple.autre", listOf("com.exemple.autre"), listOf(good)))
        assertFalse(KairoAccess.isAllowed(null, emptyList(), listOf(good)))
    }

    @Test fun bon_paquet_mauvaise_signature_refuse() {
        val old = "51ad3989" + "0".repeat(56)
        assertFalse(KairoAccess.isAllowed("com.vantaflow.kairo", listOf("com.vantaflow.kairo"), listOf(old)))
        assertFalse(KairoAccess.isAllowed("com.vantaflow.kairo", listOf("com.vantaflow.kairo"), emptyList()))
        // Plusieurs signataires : tous doivent être autorisés.
        assertFalse(KairoAccess.isAllowed("com.vantaflow.kairo", listOf("com.vantaflow.kairo"), listOf(good, old)))
    }

    @Test fun paquet_annonce_qui_n_appartient_pas_a_l_uid_refuse() {
        assertFalse(KairoAccess.isAllowed("com.vantaflow.kairo", listOf("com.exemple.autre"), listOf(good)))
    }

    @Test fun empreinte_sha256_d_un_certificat() {
        // SHA-256 de "abc" (vecteur NIST).
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", KairoAccess.sha256Hex("abc".toByteArray()))
    }
}
