package com.ultratv.tv.nativeapp.data.net.dns

import com.ultratv.tv.nativeapp.ui.settings.DnsSettingsViewModel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsSettingsTest {

    @Test fun mapping_allerRetour() {
        val s = DnsSettings(DnsMode.CUSTOM, DohPreset.QUAD9, "https://dns.example/dns-query", "9.9.9.9", "2620:fe::fe")
        val stored = s.toMap()
        assertEquals("custom", stored[DnsSettings.K_MODE])
        assertEquals("quad9", stored[DnsSettings.K_DOH_PRESET])
        assertEquals(s, DnsSettings.fromMap { stored[it] })
    }

    @Test fun mapping_valeursAbsentesOuInconnues_retombentSurSysteme() {
        assertEquals(DnsSettings(), DnsSettings.fromMap { null })
        val s = DnsSettings.fromMap { mapOf(DnsSettings.K_MODE to "bogus", DnsSettings.K_DOH_PRESET to "???")[it] }
        assertEquals(DnsMode.SYSTEM, s.mode)
        assertEquals(DohPreset.CLOUDFLARE, s.dohPreset)
        assertEquals(DnsMode.QUAD9, DnsMode.parse(" Quad9 "))
    }

    @Test fun serveursDesModesPredefinis() {
        assertEquals(listOf("8.8.8.8", "8.8.4.4"), DnsSettings(DnsMode.GOOGLE).plainServers().map { it.hostAddress })
        assertEquals(listOf("1.1.1.1", "1.0.0.1"), DnsSettings(DnsMode.CLOUDFLARE).plainServers().map { it.hostAddress })
        assertEquals(listOf("9.9.9.9", "149.112.112.112"), DnsSettings(DnsMode.QUAD9).plainServers().map { it.hostAddress })
        assertTrue(DnsSettings(DnsMode.SYSTEM).plainServers().isEmpty())
        assertFalse(DnsSettings(DnsMode.SYSTEM).usable)
    }

    @Test fun personnalise_ignoreLesAdressesInvalides() {
        assertEquals(listOf("1.2.3.4"), DnsSettings(DnsMode.CUSTOM, primary = "1.2.3.4", secondary = "not-an-ip").plainServers().map { it.hostAddress })
        assertEquals(listOf("1.2.3.4"), DnsSettings(DnsMode.CUSTOM, primary = "bad", secondary = "1.2.3.4").plainServers().map { it.hostAddress })
        assertFalse(DnsSettings(DnsMode.CUSTOM, primary = "", secondary = "").usable)
        assertFalse(DnsSettings(DnsMode.CUSTOM, primary = "dns.google").usable)   // un nom d'hôte n'est jamais résolu ici
        assertTrue(DnsSettings(DnsMode.CUSTOM, primary = "::1").usable)
    }

    @Test fun doh_urlEtBootstrap() {
        val cf = DnsSettings(DnsMode.DOH, DohPreset.CLOUDFLARE)
        assertEquals("https://cloudflare-dns.com/dns-query", cf.dohHttpUrl().toString())
        assertTrue(cf.dohBootstrap().map { it.hostAddress }.containsAll(listOf("1.1.1.1", "1.0.0.1")))
        assertEquals("https://dns.google/dns-query", DnsSettings(DnsMode.DOH, DohPreset.GOOGLE).dohHttpUrl().toString())
        assertEquals("https://dns.quad9.net/dns-query", DnsSettings(DnsMode.DOH, DohPreset.QUAD9).dohHttpUrl().toString())
        val custom = DnsSettings(DnsMode.DOH, DohPreset.CUSTOM, dohUrl = "https://doh.example/q")
        assertEquals("https://doh.example/q", custom.dohHttpUrl().toString())
        assertTrue(custom.dohBootstrap().isEmpty())
        assertFalse(DnsSettings(DnsMode.DOH, DohPreset.CUSTOM, dohUrl = "http://doh.example/q").usable)
        assertFalse(DnsSettings(DnsMode.DOH, DohPreset.CUSTOM, dohUrl = "").usable)
        assertNull(DnsSettings.validDohUrl("https://user:pw@doh.example/q"))
        assertNull(DnsSettings(DnsMode.GOOGLE, DohPreset.GOOGLE).dohHttpUrl())
    }

    @Test fun ipLiteral_ipv4() {
        assertArrayEquals(byteArrayOf(1, 1, 1, 1), IpLiteral.parse("1.1.1.1")!!.address)
        assertEquals("255.255.255.255", IpLiteral.parse(" 255.255.255.255 ")!!.hostAddress)
        for (bad in listOf("256.1.1.1", "1.1.1", "1.1.1.1.1", "01.1.1.1", "a.b.c.d", "1..1.1", "", "1.1.1.-1", "example.com")) {
            assertNull(bad, IpLiteral.parse(bad))
        }
    }

    @Test fun ipLiteral_ipv6() {
        assertEquals(16, IpLiteral.parse("2620:fe::fe")!!.address.size)
        assertArrayEquals(ByteArray(15) + byteArrayOf(1), IpLiteral.parse("::1")!!.address)
        assertArrayEquals(ByteArray(16), IpLiteral.parse("::")!!.address)
        val full = IpLiteral.parse("2001:0db8:0000:0000:0000:ff00:0042:8329")!!.address
        assertArrayEquals(IpLiteral.parse("2001:db8::ff00:42:8329")!!.address, full)
        assertArrayEquals(IpLiteral.parse("[2606:4700:4700::1111]")!!.address, IpLiteral.parse("2606:4700:4700:0:0:0:0:1111")!!.address)
        // Adresse IPv4 « mappée » : Java la ramène à l'IPv4 correspondante.
        assertEquals("1.2.3.4", IpLiteral.parse("::ffff:1.2.3.4")!!.hostAddress)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4), IpLiteral.parse("::1.2.3.4")!!.address)
        assertEquals(16, IpLiteral.parse("1:2:3:4:5:6:1.2.3.4")!!.address.size)
        for (bad in listOf("1::2::3", "12345::1", "1:2:3:4:5:6:7:8:9", "1:2:3:4:5:6:7", "g::1", "fe80::1%wlan0", "1.2.3.4::", "::1.2.3", ":1", "1:2:3:4:5:6:7:8::")) {
            assertNull(bad, IpLiteral.parse(bad))
        }
    }

    @Test fun hoteDuTest_extraitDeLaSource() {
        assertEquals("provider.tv", DnsSettingsViewModel.probeHostOf("http://provider.tv:8080/get.php?username=u&password=p"))
        assertEquals("cdn.example.org", DnsSettingsViewModel.probeHostOf(" https://cdn.example.org/list.m3u "))
        assertNull(DnsSettingsViewModel.probeHostOf("http://192.168.1.10:8000"))
        assertNull(DnsSettingsViewModel.probeHostOf("content://local/file.m3u"))
        assertNull(DnsSettingsViewModel.probeHostOf("pas une url"))
    }
}
