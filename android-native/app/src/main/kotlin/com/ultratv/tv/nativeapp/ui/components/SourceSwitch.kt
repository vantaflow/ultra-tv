package com.ultratv.tv.nativeapp.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Source proposée par le sélecteur du menu latéral. */
data class SourceChoice(val id: Long, val name: String, val active: Boolean)

/**
 * Sélecteur de source du menu latéral (retour utilisateur du 22/11 : changer de source sans passer par Réglages › Source ›
 * Définir par défaut). Règles pures : sources visibles du profil courant, hors serveurs multimédias et NAS (ils ont leur
 * entrée « Mes bibliothèques ») ; source active = la première active, à défaut la première (même règle que les écrans).
 */
object SourceSwitch {
    fun choices(providers: List<ProviderEntity>, profileSources: List<String>?): List<SourceChoice> {
        val list = com.ultratv.tv.nativeapp.data.profile.ProfileSourcesRules.visible(providers, profileSources)
            .filter { !com.ultratv.tv.nativeapp.data.mediaserver.MediaServer.isMediaKind(it.kind) && !com.ultratv.tv.nativeapp.data.nas.Nas.isNasKind(it.kind) }
        val activeId = (providers.firstOrNull { it.active } ?: providers.firstOrNull())?.id
        return list.map { SourceChoice(it.id, it.name, it.id == activeId) }
    }

    /** L'entrée n'apparaît qu'à partir de deux sources (une seule : rien à choisir). */
    fun shown(choices: List<SourceChoice>): Boolean = choices.size >= 2
}

@HiltViewModel
class SourceSwitchViewModel @Inject constructor(
    private val providers: ProviderRepository,
    profiles: com.ultratv.tv.nativeapp.data.profile.ProfileRepository,
    private val profileSources: com.ultratv.tv.nativeapp.data.profile.ProfileSources,
) : ViewModel() {
    val choices: StateFlow<List<SourceChoice>> = combine(providers.observeProviders(), profiles.currentSources) { ps, names -> SourceSwitch.choices(ps, names) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Même effet que Réglages › Source › Définir par défaut. */
    fun select(id: Long) {
        viewModelScope.launch {
            providers.setDefault(id)
            profileSources.ensureVisible(id)
        }
    }
}
