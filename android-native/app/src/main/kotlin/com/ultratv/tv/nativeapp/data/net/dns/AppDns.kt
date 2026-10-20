package com.ultratv.tv.nativeapp.data.net.dns

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * DNS de toute l'application (Réglages › Lecture › DNS). Branché sur le client OkHttp partagé (NetworkModule),
 * donc sur tous les clients qui en dérivent (Xtream, M3U, XMLTV, TMDB, cloud, images…), ainsi que sur les clients
 * autonomes de [com.ultratv.tv.nativeapp.RemoteLog] et de [com.ultratv.tv.nativeapp.update.UpdateChecker].
 *
 * Objet global plutôt que singleton Hilt : ces deux clients autonomes vivent hors du graphe d'injection.
 * Réglage LOCAL (SharedPreferences, lecture synchrone sur les fils réseau) : non synchronisé par le cloud,
 * un DNS dépend du réseau de l'appareil. Tant que [attach] n'a pas été appelé, c'est le DNS du système.
 *
 * Flux ExoPlayer : appliqué (OkHttpDataSource dès qu'un DNS autre que le système est choisi, voir ExoEngine).
 * Non appliqué : libVLC (pile réseau native).
 */
object AppDns : Dns {
    private const val PREFS = "network_dns"

    @Volatile private var prefs: SharedPreferences? = null
    private val _settings = MutableStateFlow(DnsSettings())
    val settings: StateFlow<DnsSettings> = _settings.asStateFlow()

    /** Client du seul résolveur DoH : DNS du système (jamais [AppDns], sinon récursion), délais courts. */
    private val dohClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    private val delegate = SwitchableDns(settings = { _settings.value }, resolverFactory = { resolverFor(it, { dohClient }) })

    /** Charge le réglage enregistré ; idempotent. */
    fun attach(ctx: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _settings.value = DnsSettings.fromMap { p.getString(it, null) }
            prefs = p
        }
    }

    fun update(s: DnsSettings) {
        _settings.value = s
        delegate.clearCache()
        prefs?.edit()?.apply { s.toMap().forEach { (k, v) -> putString(k, v) } }?.apply()
    }

    override fun lookup(hostname: String): List<InetAddress> = delegate.lookup(hostname)

    /** Bloquant (fil d'arrière-plan) : voir [SwitchableDns.probe]. */
    fun probe(host: String): SwitchableDns.Probe = delegate.probe(host)
}
