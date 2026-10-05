package com.ultratv.tv.nativeapp.ui.player.subtitles

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.prefs.LanguagePrefs
import com.ultratv.tv.nativeapp.data.prefs.SubtitlePrefsStore
import com.ultratv.tv.nativeapp.data.prefs.SubtitleSettings
import com.ultratv.tv.nativeapp.data.subtitles.OpenSubtitlesClient
import com.ultratv.tv.nativeapp.data.subtitles.SubtitleHit
import com.ultratv.tv.nativeapp.data.subtitles.SubtitleStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Sous-titres du lecteur : réglages persistés (DataStore) et recherche en ligne via le proxy appairé. */
@HiltViewModel
class SubtitleViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val store: SubtitlePrefsStore,
    private val online: OpenSubtitlesClient,
) : ViewModel() {
    private val _settings = MutableStateFlow(SubtitleSettings())
    val settings: StateFlow<SubtitleSettings> = _settings
    private var loaded = false

    /** Valeurs courantes (lues par la session au démarrage du moteur). */
    val current: SubtitleSettings get() = _settings.value

    /** À attendre avant de lancer le moteur : sinon le premier démarrage ignorerait les réglages enregistrés. */
    suspend fun ensureLoaded() { if (!loaded) { _settings.value = store.flow.first(); loaded = true } }

    fun setStyle(s: SubtitleStyle) { _settings.value = _settings.value.copy(style = s); viewModelScope.launch { store.saveStyle(s) } }
    /** Mémorise le dernier choix (piste choisie = on, « Désactivés » = off) pour la prochaine lecture. */
    fun setAutoOn(on: Boolean) { if (_settings.value.autoOn == on) return; _settings.value = _settings.value.copy(autoOn = on); viewModelScope.launch { store.saveAutoOn(on) } }
    fun setLanguages(l: LanguagePrefs) { _settings.value = _settings.value.copy(languages = l); viewModelScope.launch { store.saveLanguages(l) } }

    private val _hits = MutableStateFlow<List<SubtitleHit>?>(null)
    val hits: StateFlow<List<SubtitleHit>?> = _hits
    private val _onlineAvailable = MutableStateFlow(false)
    val onlineAvailable: StateFlow<Boolean> = _onlineAvailable

    fun refreshOnline() { viewModelScope.launch { _onlineAvailable.value = online.isAvailable() } }

    fun search(title: String) {
        _hits.value = null
        viewModelScope.launch {
            _hits.value = runCatching { online.search(title, _settings.value.languages.text) }.getOrDefault(emptyList())
            if (online.serviceMissing) _onlineAvailable.value = false
        }
    }

    /** Le Worker n'a pas de clé OpenSubtitles (503) : message dédié plutôt que « Aucun sous-titre ». */
    val serviceMissing: Boolean get() = online.serviceMissing

    /** Télécharge le sous-titre choisi ; renvoie le chemin du fichier SRT (cache, supprimé avec le cache). */
    suspend fun download(hit: SubtitleHit): String? =
        runCatching { online.download(hit, File(ctx.cacheDir, "subtitles")) }.getOrNull()?.absolutePath
}
