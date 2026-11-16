package com.ultratv.tv.nativeapp.data.screentime

import android.content.Context
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Décision courante pour le profil actif (voir [ScreenTime.evaluate]). */
data class ScreenTimeState(val profileId: Long, val verdict: Verdict, val usedTodaySec: Long)

/**
 * Compte la lecture EFFECTIVE (le lecteur plein écran signale [setPlaying]) par profil et par jour local, sur CET appareil
 * seulement, et publie la décision [state] : le décompte n'est jamais synchronisé (seules les règles le sont, avec le profil).
 * Un tick par seconde pendant la lecture, un toutes les 15 s sinon (une plage horaire peut se fermer menus ouverts).
 */
@Singleton
class ScreenTimeController(
    @ApplicationContext ctx: Context,
    private val profiles: ProfileRepository,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {
    @Inject constructor(@ApplicationContext ctx: Context, profiles: ProfileRepository) :
        this(ctx, profiles, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private val sp = ctx.getSharedPreferences("screen_time_state", Context.MODE_PRIVATE)
    private val playing = MutableStateFlow(false)
    private val refresh = MutableStateFlow(0)

    /** Règles du profil courant. */
    val rules: StateFlow<ScreenTimeRules> = profiles.currentPrefs
        .map { ScreenTime.decode(it.screenTime) }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, ScreenTimeRules())

    private val _state = MutableStateFlow(ScreenTimeState(0L, Verdict.Unrestricted, 0L))
    val state: StateFlow<ScreenTimeState> = _state

    /** Appelé par le lecteur plein écran : vrai tant qu'une lecture est réellement en cours. */
    fun setPlaying(on: Boolean) { playing.value = on }

    init {
        scope.launch {
            combine(rules, profiles.currentId, playing, refresh) { r, pid, p, _ -> Triple(r, pid, p) }
                .collectLatest { (r, pid, isPlaying) -> loop(r, pid, isPlaying) }
        }
    }

    private suspend fun loop(r: ScreenTimeRules, pid: Long, isPlaying: Boolean) {
        var usage = ScreenTime.usageFromJson(sp.getString("usage_$pid", null))
        var last = clock()
        var lastSaved = last
        var dirty = false
        try {
            while (true) {
                val now = clock()
                val zone = zoneProvider()
                val grant = grantOf(pid)
                val before = ScreenTime.evaluate(r, ScreenTime.usedToday(usage, now, zone), last, zone, grant)
                // Seule une lecture AUTORISÉE compte, et un tick en retard (veille, appli gelée) est plafonné.
                if (isPlaying && before !is Verdict.Blocked) {
                    usage = ScreenTime.prune(ScreenTime.addUsage(usage, last, last + ScreenTime.creditMs(last, now), zone))
                    dirty = true
                }
                last = now
                val used = ScreenTime.usedToday(usage, now, zone)
                _state.value = ScreenTimeState(pid, ScreenTime.evaluate(r, used, now, zone, grant), used)
                if (dirty && now - lastSaved >= 15_000) { sp.edit().putString("usage_$pid", ScreenTime.usageToJson(usage)).apply(); lastSaved = now; dirty = false }
                delay(if (isPlaying) 1_000 else 15_000)
            }
        } finally {
            if (dirty) sp.edit().putString("usage_$pid", ScreenTime.usageToJson(usage)).apply()
        }
    }

    private fun grantOf(pid: Long): Grant? {
        val until = sp.getLong("grant_until_$pid", 0L)
        return if (until > 0L) Grant(until, sp.getBoolean("grant_day_$pid", false)) else null
    }

    private fun saveGrant(pid: Long, g: Grant) {
        sp.edit().putLong("grant_until_$pid", g.untilMs).putBoolean("grant_day_$pid", g.wholeDay).apply()
        refresh.value++
    }

    /** Dérogation parentale « +30 min » pour le profil courant (le code parental a été vérifié par l'appelant). */
    fun grantExtra() { val pid = _state.value.profileId; saveGrant(pid, ScreenTime.extend(grantOf(pid), clock())) }

    /** Dérogation parentale « pour aujourd'hui » (une fois) : jusqu'à minuit. */
    fun grantToday() { val pid = _state.value.profileId; saveGrant(pid, ScreenTime.forToday(clock(), zoneProvider())) }
}
