package com.ultratv.tv.nativeapp.data.net.dns

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

class SwitchableDnsTest {
    private val sysAddr = InetAddress.getByAddress("h", byteArrayOf(10, 0, 0, 1))
    private val customAddr = InetAddress.getByAddress("h", byteArrayOf(1, 2, 3, 4))

    private class FakeSystem(val answer: () -> List<InetAddress>) : Dns {
        val calls = AtomicInteger()
        override fun lookup(hostname: String): List<InetAddress> { calls.incrementAndGet(); return answer() }
    }

    private class FakeResolver(val block: (String) -> Resolved) : DnsResolver {
        val calls = AtomicInteger()
        override fun resolve(host: String): Resolved { calls.incrementAndGet(); return block(host) }
    }

    private var clock = 1_000_000L
    private var settings = DnsSettings(DnsMode.CUSTOM, primary = "9.9.9.9")

    private fun dns(system: Dns, resolver: DnsResolver?, timeoutMs: Long = 2_000, factoryCalls: AtomicInteger = AtomicInteger()) =
        SwitchableDns(settings = { settings }, resolverFactory = { factoryCalls.incrementAndGet(); resolver }, system = system, timeoutMs = timeoutMs, now = { clock })

    @Test fun systeme_passeDirectementAuDnsDuSysteme() {
        settings = DnsSettings(DnsMode.SYSTEM)
        val sys = FakeSystem { listOf(sysAddr) }
        val factory = AtomicInteger()
        val r = FakeResolver { Resolved(listOf(customAddr), 300) }
        val d = dns(sys, r, factoryCalls = factory)
        assertEquals(listOf(sysAddr), d.lookup("a.example"))
        assertEquals(listOf(sysAddr), d.lookup("a.example"))
        assertEquals(2, sys.calls.get())   // aucun cache en mode Système
        assertEquals(0, r.calls.get())
        assertEquals(0, factory.get())
    }

    @Test fun personnalise_utiliseLeResolveurEtMetEnCache() {
        val sys = FakeSystem { listOf(sysAddr) }
        val r = FakeResolver { Resolved(listOf(customAddr), 120) }
        val d = dns(sys, r)
        assertEquals(listOf(customAddr), d.lookup("a.example"))
        assertEquals(listOf(customAddr), d.lookup("A.EXAMPLE"))
        assertEquals(1, r.calls.get())
        assertEquals(0, sys.calls.get())
        clock += 119_000; d.lookup("a.example"); assertEquals(1, r.calls.get())
        clock += 2_000; d.lookup("a.example"); assertEquals(2, r.calls.get())   // TTL écoulé
    }

    @Test fun ttlCourt_releveA60s_etTtlInconnu_300s() {
        val r = FakeResolver { Resolved(listOf(customAddr), 5) }
        val d = dns(FakeSystem { listOf(sysAddr) }, r)
        d.lookup("a.example")
        clock += 59_000; d.lookup("a.example"); assertEquals(1, r.calls.get())
        clock += 2_000; d.lookup("a.example"); assertEquals(2, r.calls.get())

        val r2 = FakeResolver { Resolved(listOf(customAddr), null) }
        val d2 = dns(FakeSystem { listOf(sysAddr) }, r2)
        d2.lookup("b.example")
        clock += 299_000; d2.lookup("b.example"); assertEquals(1, r2.calls.get())
        clock += 2_000; d2.lookup("b.example"); assertEquals(2, r2.calls.get())
    }

    @Test fun echec_repliSurLeSysteme_memoriseUneMinute() {
        val sys = FakeSystem { listOf(sysAddr) }
        val r = FakeResolver { throw IOException("injoignable") }
        val d = dns(sys, r)
        assertEquals(listOf(sysAddr), d.lookup("a.example"))
        assertEquals(listOf(sysAddr), d.lookup("a.example"))
        assertEquals(1, r.calls.get())
        assertEquals(1, sys.calls.get())
        clock += 61_000
        d.lookup("a.example")
        assertEquals(2, r.calls.get())   // le serveur choisi est retenté après le délai
    }

    @Test fun reponseVide_repliSurLeSysteme() {
        val sys = FakeSystem { listOf(sysAddr) }
        val d = dns(sys, FakeResolver { Resolved(emptyList(), 300) })
        assertEquals(listOf(sysAddr), d.lookup("a.example"))
        assertEquals(1, sys.calls.get())
    }

    @Test fun delaiDepasse_repliSurLeSystemeSansAttendreLeResolveur() {
        val sys = FakeSystem { listOf(sysAddr) }
        val d = dns(sys, FakeResolver { Thread.sleep(5_000); Resolved(listOf(customAddr), 300) }, timeoutMs = 150)
        val start = System.nanoTime()
        assertEquals(listOf(sysAddr), d.lookup("slow.example"))
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2_000)
    }

    @Test(expected = UnknownHostException::class)
    fun echecDesDeux_leveUnknownHostException() {
        dns(FakeSystem { throw UnknownHostException("nope") }, FakeResolver { throw IOException("x") }).lookup("a.example")
    }

    @Test fun reglageInutilisable_seComporteCommeSysteme() {
        val sys = FakeSystem { listOf(sysAddr) }
        val d = dns(sys, resolver = null)
        assertEquals(listOf(sysAddr), d.lookup("a.example"))
        assertEquals(listOf(sysAddr), d.lookup("a.example"))
        assertEquals(2, sys.calls.get())
    }

    @Test fun fabriqueQuiPlante_seComporteCommeSysteme() {
        val sys = FakeSystem { listOf(sysAddr) }
        val d = SwitchableDns(settings = { settings }, resolverFactory = { error("boom") }, system = sys, now = { clock })
        assertEquals(listOf(sysAddr), d.lookup("a.example"))
    }

    @Test fun ipLitterale_etNomsLocaux_neSollicitentPasLeResolveur() {
        val sys = FakeSystem { listOf(sysAddr) }
        val r = FakeResolver { Resolved(listOf(customAddr), 300) }
        val d = dns(sys, r)
        assertEquals("192.168.1.5", d.lookup("192.168.1.5").single().hostAddress)
        d.lookup("localhost"); d.lookup("nas.local"); d.lookup("mybox")
        assertEquals(0, r.calls.get())
        assertEquals(3, sys.calls.get())
    }

    @Test fun changementDeReglage_nouveauResolveurEtCacheSepare() {
        val factory = AtomicInteger()
        val r = FakeResolver { Resolved(listOf(customAddr), 300) }
        val d = dns(FakeSystem { listOf(sysAddr) }, r, factoryCalls = factory)
        d.lookup("a.example"); d.lookup("b.example")
        assertEquals(1, factory.get())   // résolveur réutilisé tant que le réglage ne change pas
        settings = settings.copy(primary = "8.8.8.8")
        d.lookup("a.example")
        assertEquals(2, factory.get())
        assertEquals(3, r.calls.get())   // pas de réponse servie depuis le cache de l'ancien serveur
    }

    @Test fun clearCache_forceUneNouvelleResolution() {
        val r = FakeResolver { Resolved(listOf(customAddr), 300) }
        val d = dns(FakeSystem { listOf(sysAddr) }, r)
        d.lookup("a.example"); d.clearCache(); d.lookup("a.example")
        assertEquals(2, r.calls.get())
    }

    @Test fun probe_directPuisRepli() {
        val ok = dns(FakeSystem { listOf(sysAddr) }, FakeResolver { Resolved(listOf(customAddr), 300) }).probe("a.example")
        assertEquals(listOf(customAddr), ok.addresses)
        assertFalse(ok.viaSystem); assertFalse(ok.fellBack)

        val ko = dns(FakeSystem { listOf(sysAddr) }, FakeResolver { throw IOException("refused") }).probe("a.example")
        assertEquals(listOf(sysAddr), ko.addresses)
        assertTrue(ko.fellBack)
        assertEquals("refused", ko.error)

        settings = DnsSettings(DnsMode.SYSTEM)
        val sys = dns(FakeSystem { listOf(sysAddr) }, null).probe("a.example")
        assertTrue(sys.viaSystem); assertFalse(sys.fellBack)
        assertNotNull(sys.addresses.firstOrNull())
    }
}
