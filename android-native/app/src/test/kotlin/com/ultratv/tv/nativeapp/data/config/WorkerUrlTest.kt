package com.ultratv.tv.nativeapp.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkerUrlTest {
    @Test fun `https accepte et slash final retire`() =
        assertEquals("https://w.example.workers.dev", WorkerUrl.normalize(" https://w.example.workers.dev/ ", allowCleartext = false))

    @Test fun `http refuse en release`() = assertNull(WorkerUrl.normalize("http://w.example.com", allowCleartext = false))

    @Test fun `http accepte en debug`() =
        assertEquals("http://10.0.2.2:8787", WorkerUrl.normalize("http://10.0.2.2:8787", allowCleartext = true))

    @Test fun `schemas exotiques refuses`() {
        assertNull(WorkerUrl.normalize("file:///etc/passwd", allowCleartext = true))
        assertNull(WorkerUrl.normalize("ftp://w.example.com", allowCleartext = true))
        assertNull(WorkerUrl.normalize("javascript:alert(1)", allowCleartext = true))
    }

    @Test fun `identifiants requete et fragment refuses`() {
        assertNull(WorkerUrl.normalize("https://user:pw@w.example.com", allowCleartext = false))
        assertNull(WorkerUrl.normalize("https://w.example.com?x=1", allowCleartext = false))
        assertNull(WorkerUrl.normalize("https://w.example.com#f", allowCleartext = false))
    }

    @Test fun `vide ou sans hote refuse`() {
        assertNull(WorkerUrl.normalize("", allowCleartext = true))
        assertNull(WorkerUrl.normalize("https://", allowCleartext = true))
        assertNull(WorkerUrl.normalize("pas une url", allowCleartext = true))
    }

    private val newUrl = "https://tv.weedy.dpdns.org"
    private val oldUrl = "https://ultratv-config.khalilbenaz.workers.dev"

    @Test fun `resolve - vide ou ancien defaut donne le nouveau defaut`() {
        assertEquals(newUrl, WorkerUrl.resolve("", newUrl))
        assertEquals(newUrl, WorkerUrl.resolve("  ", newUrl))
        assertEquals(newUrl, WorkerUrl.resolve(oldUrl, newUrl))
        assertEquals(newUrl, WorkerUrl.resolve("$oldUrl/", newUrl))
    }

    @Test fun `resolve - une adresse personnalisee reste intacte`() {
        assertEquals("https://mon.worker.exemple.dev", WorkerUrl.resolve("https://mon.worker.exemple.dev/", newUrl))
        assertEquals("http://10.0.2.2:8787", WorkerUrl.resolve("http://10.0.2.2:8787", newUrl))
        assertEquals(newUrl, WorkerUrl.resolve(newUrl, newUrl))
    }
}

class SiteBaseTest {
    @org.junit.Test fun nosAdressesApi_affichentLeSite_autoHebergeInchange() {
        org.junit.Assert.assertEquals("https://ultratv.dpdns.org", WorkerFallbackLogic.siteBase("https://tv.weedy.dpdns.org"))
        org.junit.Assert.assertEquals("https://ultratv.dpdns.org", WorkerFallbackLogic.siteBase("https://ultratv-config.khalilbenaz.workers.dev/"))
        org.junit.Assert.assertEquals("https://mon-worker.example", WorkerFallbackLogic.siteBase("https://mon-worker.example"))
    }
}
