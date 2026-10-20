package com.ultratv.tv.nativeapp.perf

import android.app.Activity
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import com.ultratv.tv.nativeapp.RemoteLog

/**
 * Télémétrie de fluidité (tag « perf ») : sans appareil de test, c'est le seul moyen de VOIR où l'appli saccade chez
 * l'utilisateur. JankStats (FrameMetrics) mesure chaque image ; on agrège par écran et on n'envoie qu'une ligne par écran
 * et par minute au plus (voir [PerfAggregator]). Aucune donnée personnelle : modèles de route seulement.
 *
 * Mesure : fil UI SEULEMENT ([androidx.metrics.performance.FrameData.frameDurationUiNanos] — mesure + mise en page + dessin
 * du fil principal), pas le fil de rendu ni le GPU. « janky » = heuristique de JankStats (> 2 × la période de l'écran,
 * soit > 33 ms à 60 Hz) ; « slow » = > 1,5 × la période (> 25 ms à 60 Hz) pour voir les images uniques ratées. La période
 * vient de la fréquence réelle de l'écran (relue toutes les [CHECK_EVERY_MS] : elle change avec la fréquence d'image auto).
 *
 * Coût : un rappel par image (quelques comparaisons + un incrément synchronisé), aucune allocation hors rapport. Suivi coupé
 * quand l'appli n'est plus affichée. Inactif sous Robolectric (tests unitaires).
 */
object PerfTelemetry {
    private const val TAG = "perf"
    private const val ROUTE_STATE = "route"
    /** Fenêtre de vérification des rapports dus (évite de lire l'horloge d'une autre façon à chaque image). */
    private const val CHECK_EVERY_MS = 5_000L

    private val aggregator = PerfAggregator()
    private val stallGate = StallGate()
    private val coldStart = ColdStartGate()

    @Volatile private var route: String? = null
    @Volatile private var lastCheck = 0L
    @Volatile private var appCreateMs = 0L
    /** Période d'une image de l'écran (ns), pour le seuil « slow ». */
    @Volatile private var refreshPeriodNanos = PerfAggregator.DEFAULT_REFRESH_PERIOD_NANOS
    @Volatile private var display: android.view.Display? = null
    private var stats: JankStats? = null
    private var stateHolder: PerformanceMetricsState.Holder? = null

    /** Premier instant du processus applicatif : appelé en tête de [android.app.Application.onCreate]. */
    fun markAppCreate() { appCreateMs = SystemClock.elapsedRealtime() }

    /** Environnement de tests unitaires : aucune mesure, aucun envoi. */
    private fun isUnitTest() = "robolectric".equals(Build.FINGERPRINT, ignoreCase = true) || Build.FINGERPRINT == null

    /** À appeler une fois depuis l'activité principale, après la création de la fenêtre. */
    fun attach(activity: Activity) {
        if (stats != null || isUnitTest()) return
        runCatching {
            stateHolder = PerformanceMetricsState.getHolderForHierarchy(activity.window.decorView)
            @Suppress("DEPRECATION")
            display = activity.windowManager.defaultDisplay
            refreshDisplayPeriod()
            stats = JankStats.createAndTrack(activity.window) { f ->
                val ns = f.frameDurationUiNanos
                val r = f.states.firstOrNull { it.key == ROUTE_STATE }?.value ?: route
                onFrame(r, ns / 1_000_000L, f.isJank, PerfAggregator.isSlow(ns, refreshPeriodNanos))
            }.also { it.jankHeuristicMultiplier = 2.0f }
        }.onFailure { Log.w("UltraPerf", "JankStats indisponible", it) }
    }

    fun setTracking(on: Boolean) { runCatching { stats?.isTrackingEnabled = on } }

    /** Changement d'écran (modèle de route). Vide les rapports de l'écran quitté s'ils sont dus. */
    fun onRoute(newRoute: String?) {
        val old = route
        route = PerfAggregator.normalize(newRoute).takeIf { newRoute != null }
        runCatching { stateHolder?.state?.putState(ROUTE_STATE, route ?: "(none)") }
        if (old != null && old != route) emit(aggregator.drain(SystemClock.elapsedRealtime(), only = old))
    }

    /** Fréquence courante de l'écran → période d'une image (60 Hz si illisible). Hors du chemin par image. */
    private fun refreshDisplayPeriod() {
        refreshPeriodNanos = PerfAggregator.refreshPeriodNanos(runCatching { display?.refreshRate ?: 0f }.getOrDefault(0f))
    }

    private fun onFrame(r: String?, ms: Long, janky: Boolean, slow: Boolean) {
        val now = SystemClock.elapsedRealtime()
        coldStart.onFrame(r, now - (Process.getStartElapsedRealtime()), now - appCreateMs)?.let { RemoteLog.info(TAG, it) }
        if (r == null) return
        aggregator.record(r, ms, janky, slow)
        if (stallGate.shouldReport(ms, now)) RemoteLog.warn(TAG, "perf stall ${ms}ms route=${PerfAggregator.normalize(r)}")
        if (now - lastCheck >= CHECK_EVERY_MS) {
            lastCheck = now
            refreshDisplayPeriod()
            emit(aggregator.drain(now))
        }
    }

    private fun emit(lines: List<String>) { for (l in lines) RemoteLog.info(TAG, l) }
}
