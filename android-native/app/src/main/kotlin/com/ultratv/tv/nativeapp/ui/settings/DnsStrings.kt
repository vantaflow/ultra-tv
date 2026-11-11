package com.ultratv.tv.nativeapp.ui.settings

import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.I18n

/** Libellés du réglage DNS (FR · EN · ES · AR), dans un fichier dédié. */
class DnsStrings(private val lang: AppLang) {
    private fun tx(id: Int, vararg args: Any): String = I18n.string(lang, id, *args)
    private fun tp(id: Int, qty: Int, vararg args: Any): String = I18n.plural(lang, id, qty, *args)

    val group get() = tx(R.string.dns_group)
    val server get() = tx(R.string.dns_server)
    val system get() = tx(R.string.dns_system)
    val doh get() = tx(R.string.dns_doh)
    val custom get() = tx(R.string.dns_custom)
    val dohProvider get() = tx(R.string.dns_doh_provider)
    val dohUrl get() = tx(R.string.dns_doh_url)
    val dohUrlSub get() = tx(R.string.dns_doh_url_sub)
    val invalidDohUrl get() = tx(R.string.dns_invalid_doh_url)
    val servers get() = tx(R.string.dns_servers)
    val serversSub get() = tx(R.string.dns_servers_sub)
    val primary get() = tx(R.string.dns_primary)
    val secondary get() = tx(R.string.dns_secondary)
    val invalidIp get() = tx(R.string.dns_invalid_ip)
    val notSet get() = tx(R.string.dns_not_set)
    val test get() = tx(R.string.dns_test)
    val testHint get() = tx(R.string.dns_test_hint)
    val testing get() = tx(R.string.dns_testing)
    fun testOk(ip: String, ms: Long) = tx(R.string.dns_test_ok, ip, ms)
    fun testFallback(ip: String, reason: String) = tx(R.string.dns_test_fallback, reason, ip)
    val testFailed get() = tx(R.string.dns_test_failed)
    val vlcNote get() = tx(R.string.dns_vlc_note)
    val scopeNote get() = tx(R.string.dns_scope_note)
}
