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
}
