package com.ultratv.tv.nativeapp.data.net.dns

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ResolversTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val closers = mutableListOf<AutoCloseable>()

    @After fun tearDown() { closers.forEach { runCatching { it.close() } } }

    /** Faux serveur DNS UDP : [answer] reçoit la requête et renvoie la réponse (null = silence). */
    private fun udpServer(answer: (ByteArray) -> ByteArray?): DatagramSocket {
        val sock = DatagramSocket(0, loopback).also { closers += it }
        thread(isDaemon = true) {
            val buf = ByteArray(1500)
            while (!sock.isClosed) {
                val p = DatagramPacket(buf, buf.size)
                runCatching { sock.receive(p) }.onFailure { return@thread }
                val q = buf.copyOf(p.length)
                answer(q)?.let { r -> runCatching { sock.send(DatagramPacket(r, r.size, p.socketAddress)) } }
            }
        }
        return sock
    }

    // ───────────── Format binaire ─────────────

    @Test fun wire_requeteEtLecture() {
        val q = DnsWire.query(0x1234, "cdn.example.com", DnsWire.TYPE_A)
        assertEquals(0x1234, TestDnsMessages.id(q))
        assertEquals(DnsWire.TYPE_A, TestDnsMessages.qtype(q))
        assertEquals(12 + 17 + 4, q.size)   // en-tête + 3cdn7example3com0 + type/classe
        val a = DnsWire.parse(TestDnsMessages.response(q, listOf("1.2.3.4", "5.6.7.8"), ttl = 42), host = "cdn.example.com")
        assertEquals(listOf("1.2.3.4", "5.6.7.8"), a.addresses.map { it.hostAddress })
        assertEquals("cdn.example.com", a.addresses.first().hostName)
        assertEquals(42L, a.minTtlSeconds)
        assertFalse(a.truncated)
    }

    @Test fun wire_reponseTronqueeOuInvalide() {
        val q = DnsWire.query(1, "a.example", DnsWire.TYPE_A)
        assertTrue(DnsWire.parse(TestDnsMessages.response(q, emptyList(), truncated = true)).truncated)
        val good = TestDnsMessages.response(q, listOf("1.2.3.4"))
        try { DnsWire.parse(good, good.size - 3); fail() } catch (_: java.io.IOException) {}
        try { DnsWire.parse(q); fail("une requête n'est pas une réponse") } catch (_: java.io.IOException) {}
        try { DnsWire.query(1, "a".repeat(64) + ".example", DnsWire.TYPE_A); fail() } catch (_: java.io.IOException) {}
    }

    // ───────────── UDP ─────────────

    @Test fun udp_resoutAetAAAA() {
        val srv = udpServer { q -> TestDnsMessages.response(q, listOf("1.2.3.4", "2001:db8::1"), ttl = 90) }
        val r = UdpDnsResolver(listOf(loopback), timeoutMs = 1_500, port = srv.localPort).resolve("tv.example")
        assertEquals(listOf("1.2.3.4", "2001:db8:0:0:0:0:0:1"), r.addresses.map { it.hostAddress })
        assertEquals(90L, r.ttlSeconds)
    }

    @Test fun udp_ignoreUneReponseAuMauvaisIdentifiant() {
        val srv = udpServer { q -> if (TestDnsMessages.qtype(q) == DnsWire.TYPE_A) TestDnsMessages.response(q, listOf("6.6.6.6"), idOverride = (TestDnsMessages.id(q) + 7) and 0xFFFF) else TestDnsMessages.response(q, listOf("2001:db8::2")) }
        val r = UdpDnsResolver(listOf(loopback), timeoutMs = 600, port = srv.localPort).resolve("tv.example")
        assertEquals(listOf("2001:db8:0:0:0:0:0:2"), r.addresses.map { it.hostAddress })
    }

    @Test fun udp_serveurMuet_puisSecondServeur() {
        // Même port pour les deux « serveurs » : 127.0.0.2 n'écoute pas (aucune réponse), 127.0.0.1 répond.
        val srv = udpServer { q -> TestDnsMessages.response(q, listOf("1.2.3.4")) }
        val silent = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 2))
        val r = UdpDnsResolver(listOf(silent, loopback), timeoutMs = 1_000, port = srv.localPort).resolve("tv.example")
        assertEquals("1.2.3.4", r.addresses.single().hostAddress)
    }

    @Test fun udp_nxdomain_leveUnknownHost() {
        val srv = udpServer { q -> TestDnsMessages.response(q, emptyList(), rcode = DnsWire.RCODE_NXDOMAIN) }
        try {
            UdpDnsResolver(listOf(loopback), timeoutMs = 500, port = srv.localPort).resolve("nope.example"); fail()
        } catch (_: UnknownHostException) {}
    }

    @Test fun udp_toutMuet_echoueDansLeBudget() {
        val srv = udpServer { null }
        val start = System.nanoTime()
        try { UdpDnsResolver(listOf(loopback), timeoutMs = 300, port = srv.localPort).resolve("tv.example"); fail() } catch (_: UnknownHostException) {}
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1_500)
    }

    @Test fun udp_reponseTronquee_repliTcp() {
        // UDP et TCP sur le même numéro de port (réessai si le port TCP est déjà pris).
        var udp: DatagramSocket? = null; var tcp: ServerSocket? = null
        for (i in 0 until 20) {
            val u = DatagramSocket(0, loopback)
            val t = runCatching { ServerSocket(u.localPort, 5, loopback) }.getOrNull()
            if (t != null) { udp = u; tcp = t; break } else u.close()
        }
        val u = udp ?: return; val t = tcp!!
        closers += u; closers += t
        thread(isDaemon = true) {
            val buf = ByteArray(1500)
            while (!u.isClosed) {
                val p = DatagramPacket(buf, buf.size)
                runCatching { u.receive(p) }.onFailure { return@thread }
                val r = TestDnsMessages.response(buf.copyOf(p.length), emptyList(), truncated = true)
                runCatching { u.send(DatagramPacket(r, r.size, p.socketAddress)) }
            }
        }
        thread(isDaemon = true) {
            while (!t.isClosed) {
                val c = runCatching { t.accept() }.getOrNull() ?: return@thread
                c.use {
                    val input = DataInputStream(it.getInputStream())
                    val q = ByteArray(input.readUnsignedShort()).also { b -> input.readFully(b) }
                    val r = TestDnsMessages.response(q, listOf("9.8.7.6", "2001:db8::9"))
                    DataOutputStream(it.getOutputStream()).apply { writeShort(r.size); write(r); flush() }
                }
            }
        }
        val res = UdpDnsResolver(listOf(loopback), timeoutMs = 1_500, port = u.localPort).resolve("big.example")
        assertTrue(res.addresses.any { it.hostAddress == "9.8.7.6" })
    }

    // ───────────── DNS-over-HTTPS ─────────────

    @Test fun doh_resoutViaMockWebServer() {
        val server = MockWebServer().also { closers += AutoCloseable { it.shutdown() } }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val q = request.requestUrl?.queryParameter("dns")?.decodeBase64()?.toByteArray()
                    ?: return MockResponse().setResponseCode(400)
                return MockResponse().setHeader("Content-Type", "application/dns-message")
                    .setBody(Buffer().write(TestDnsMessages.response(q, listOf("1.2.3.4", "2001:db8::7"))))
            }
        }
        server.start()
        val client = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build()
        val r = DohResolver(server.url("/dns-query"), emptyList(), client).resolve("tv.example")
        assertEquals(setOf("1.2.3.4", "2001:db8:0:0:0:0:0:7"), r.addresses.map { it.hostAddress }.toSet())
        assertNull(r.ttlSeconds)
        assertTrue(server.requestCount >= 2)   // A et AAAA
        val first = server.takeRequest()
        assertEquals("application/dns-message", first.getHeader("Accept"))
    }

    @Test fun doh_erreurServeur_bascule_surLeSystemeViaSwitchableDns() {
        val server = MockWebServer().also { closers += AutoCloseable { it.shutdown() } }
        repeat(4) { server.enqueue(MockResponse().setResponseCode(500)) }
        server.start()
        val doh = DohResolver(server.url("/dns-query"), emptyList(), OkHttpClient())
        val sysAddr = InetAddress.getByAddress("tv.example", byteArrayOf(10, 0, 0, 9))
        val d = SwitchableDns(settings = { DnsSettings(DnsMode.DOH, DohPreset.CUSTOM, "https://x.example/q") }, resolverFactory = { doh }, system = object : okhttp3.Dns { override fun lookup(hostname: String) = listOf(sysAddr) })
        assertEquals(listOf(sysAddr), d.lookup("tv.example"))
    }

    @Test fun resolverFor_selonLeMode() {
        val client = { OkHttpClient() }
        assertNull(resolverFor(DnsSettings(DnsMode.SYSTEM), client))
        assertNull(resolverFor(DnsSettings(DnsMode.CUSTOM, primary = "bad"), client))
        assertNull(resolverFor(DnsSettings(DnsMode.DOH, DohPreset.CUSTOM, dohUrl = "ftp://x"), client))
        assertTrue(resolverFor(DnsSettings(DnsMode.GOOGLE), client) is UdpDnsResolver)
        assertTrue(resolverFor(DnsSettings(DnsMode.CUSTOM, primary = "1.1.1.1"), client) is UdpDnsResolver)
        assertTrue(resolverFor(DnsSettings(DnsMode.DOH, DohPreset.QUAD9), client) is DohResolver)
    }
}
