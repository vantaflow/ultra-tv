package com.ultratv.tv.nativeapp.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDs by preferencesDataStore(name = "user_prefs")

enum class SidebarPosition { LEFT, TOP }
enum class AppTheme { DARK, AMOLED, BLUE, LIGHT }
enum class DefaultPlayer { INTERNAL, EXTERNAL }

data class UserPrefs(
    val sidebarPosition: SidebarPosition = SidebarPosition.LEFT,
    val theme: AppTheme = AppTheme.DARK,
    val defaultPlayer: DefaultPlayer = DefaultPlayer.INTERNAL,
    val autoSyncOnLaunch: Boolean = false,
    val showChannelNumbers: Boolean = true,
    val hideAdultCategories: Boolean = false,
    val resumePlayback: Boolean = true,
    val autoPlayNextEpisode: Boolean = true,
    /** Launch the app automatically once Android TV finishes booting. */
    val launchAtBoot: Boolean = false,
    /** On app start, automatically open the most recently watched item. */
    val autoPlayLastOnLaunch: Boolean = false,
    /** Minimum hours between auto-syncs; 0 = sync on every launch. Only used
     *  when [autoSyncOnLaunch] is true. */
    val syncIntervalHours: Int = 0,
    /** Last successful auto-sync timestamp (ms). Internal; not user-visible. */
    val lastSyncAtMs: Long = 0L,
    /** Cloudflare Worker base URL used by "Sync from cloud". Empty until the
     *  user enters their own — never hard-coded in source. */
    val workerBaseUrl: String = "",
    /** Suppresses the onboarding wizard. Flipped to `true` when the user
     *  dismisses or completes it. */
    val hasSeenOnboarding: Boolean = false,
    /** UI language code: "system", "en", "fr", "es", "ar". */
    val language: String = "system",
    /** Per-MAC password used when fetching config from the worker. Optional —
     *  empty for unprotected entries. Persists across launches; never logged. */
    val configPassword: String = "",
    /** Telemetry opt-in. When false, RemoteLog drops every event/crash POST
     *  silently. Defaults to true because the app surfaces this in Settings
     *  and the diagnostic flow is what keeps the redesign honest; flipping
     *  it off stops the dashboard cold for that install. */
    val telemetryEnabled: Boolean = true,

    // Playback / TV-quality knobs — exposed in Settings.
    /** Buffer target in seconds. Media3's default is 15 s, which is decent but
     *  too tight for shaky IPTV providers. We expose 8/15/30/60 chips. */
    val bufferSeconds: Int = 30,
    /** Auto-switch the TV's refresh rate to match the stream's frame rate
     *  (24/25/29.97/30/50/59.94/60). Avoids judder on motion. */
    val autoFrameRate: Boolean = true,
    /** Prefer the software (FFmpeg / libavcodec via system) decoder over the
     *  hardware one. Useful for channels with codec quirks the hardware
     *  refuses (e.g. HEVC main10 on cheap boxes). */
    val preferSoftwareDecoder: Boolean = false,
    /** EPG times shifted by ± N minutes. Some providers ship EPG at UTC while
     *  channels stream in local time; this lets the user nudge it. */
    val epgTimeOffsetMin: Int = 0,
    /** Optional SAF tree URI for a folder of local channel logos that override
     *  whatever the provider ships. Empty = no override. */
    val localLogosFolderUri: String = "",
)

@Singleton
class UserPreferencesStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private object Keys {
        val sidebar = stringPreferencesKey("sidebar_position")
        val theme = stringPreferencesKey("theme")
        val player = stringPreferencesKey("default_player")
        val autoSync = booleanPreferencesKey("auto_sync_on_launch")
        val channelNums = booleanPreferencesKey("show_channel_numbers")
        val hideAdult = booleanPreferencesKey("hide_adult")
        val resume = booleanPreferencesKey("resume_playback")
        val autoPlayNext = booleanPreferencesKey("auto_play_next")
        val launchAtBoot = booleanPreferencesKey("launch_at_boot")
        val autoPlayLast = booleanPreferencesKey("auto_play_last_on_launch")
        val syncInterval = intPreferencesKey("sync_interval_hours")
        val lastSyncAt = longPreferencesKey("last_sync_at_ms")
        val workerBase = stringPreferencesKey("worker_base_url")
        val seenOnboarding = booleanPreferencesKey("has_seen_onboarding")
        val language = stringPreferencesKey("language")
        val configPassword = stringPreferencesKey("config_password")
        val telemetry = booleanPreferencesKey("telemetry_enabled")
        val bufferSec = intPreferencesKey("buffer_seconds")
        val autoFrameRate = booleanPreferencesKey("auto_frame_rate")
        val preferSwDec = booleanPreferencesKey("prefer_software_decoder")
        val epgOffsetMin = intPreferencesKey("epg_offset_min")
        val localLogosUri = stringPreferencesKey("local_logos_uri")
    }

    val flow: Flow<UserPrefs> = ctx.userPrefsDs.data.map { p ->
        UserPrefs(
            sidebarPosition = enumValueOf<SidebarPosition>(p[Keys.sidebar] ?: SidebarPosition.LEFT.name),
            theme = enumValueOf<AppTheme>(p[Keys.theme] ?: AppTheme.DARK.name),
            defaultPlayer = enumValueOf<DefaultPlayer>(p[Keys.player] ?: DefaultPlayer.INTERNAL.name),
            autoSyncOnLaunch = p[Keys.autoSync] ?: false,
            showChannelNumbers = p[Keys.channelNums] ?: true,
            hideAdultCategories = p[Keys.hideAdult] ?: false,
            resumePlayback = p[Keys.resume] ?: true,
            autoPlayNextEpisode = p[Keys.autoPlayNext] ?: true,
            launchAtBoot = p[Keys.launchAtBoot] ?: false,
            autoPlayLastOnLaunch = p[Keys.autoPlayLast] ?: false,
            syncIntervalHours = p[Keys.syncInterval] ?: 0,
            lastSyncAtMs = p[Keys.lastSyncAt] ?: 0L,
            workerBaseUrl = p[Keys.workerBase] ?: "",
            hasSeenOnboarding = p[Keys.seenOnboarding] ?: false,
            language = p[Keys.language] ?: "system",
            configPassword = p[Keys.configPassword] ?: "",
            telemetryEnabled = p[Keys.telemetry] ?: true,
            bufferSeconds = p[Keys.bufferSec] ?: 30,
            autoFrameRate = p[Keys.autoFrameRate] ?: true,
            preferSoftwareDecoder = p[Keys.preferSwDec] ?: false,
            epgTimeOffsetMin = p[Keys.epgOffsetMin] ?: 0,
            localLogosFolderUri = p[Keys.localLogosUri] ?: "",
        )
    }

    suspend fun setSidebar(pos: SidebarPosition) = update { it[Keys.sidebar] = pos.name }
    suspend fun setTheme(t: AppTheme) = update { it[Keys.theme] = t.name }
    suspend fun setDefaultPlayer(p: DefaultPlayer) = update { it[Keys.player] = p.name }
    suspend fun setAutoSync(v: Boolean) = update { it[Keys.autoSync] = v }
    suspend fun setShowChannelNumbers(v: Boolean) = update { it[Keys.channelNums] = v }
    suspend fun setHideAdult(v: Boolean) = update { it[Keys.hideAdult] = v }
    suspend fun setResumePlayback(v: Boolean) = update { it[Keys.resume] = v }
    suspend fun setAutoPlayNext(v: Boolean) = update { it[Keys.autoPlayNext] = v }
    suspend fun setLaunchAtBoot(v: Boolean) = update { it[Keys.launchAtBoot] = v }
    suspend fun setAutoPlayLast(v: Boolean) = update { it[Keys.autoPlayLast] = v }
    suspend fun setSyncInterval(hours: Int) = update { it[Keys.syncInterval] = hours }
    suspend fun setLastSyncAt(ms: Long) = update { it[Keys.lastSyncAt] = ms }
    suspend fun setWorkerBase(url: String) = update { it[Keys.workerBase] = url.trim() }
    suspend fun markOnboardingSeen() = update { it[Keys.seenOnboarding] = true }
    suspend fun setLanguage(code: String) = update { it[Keys.language] = code }
    suspend fun setConfigPassword(pwd: String) = update { it[Keys.configPassword] = pwd }
    suspend fun setTelemetry(on: Boolean) = update { it[Keys.telemetry] = on }
    suspend fun setBufferSeconds(v: Int) = update { it[Keys.bufferSec] = v.coerceIn(5, 300) }
    suspend fun setAutoFrameRate(v: Boolean) = update { it[Keys.autoFrameRate] = v }
    suspend fun setPreferSoftwareDecoder(v: Boolean) = update { it[Keys.preferSwDec] = v }
    suspend fun setEpgTimeOffsetMin(v: Int) = update { it[Keys.epgOffsetMin] = v.coerceIn(-720, 720) }
    suspend fun setLocalLogosFolderUri(uri: String) = update { it[Keys.localLogosUri] = uri }

    private suspend inline fun update(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        ctx.userPrefsDs.edit { block(it) }
    }
}
