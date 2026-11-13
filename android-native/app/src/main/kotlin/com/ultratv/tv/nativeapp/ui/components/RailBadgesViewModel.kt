package com.ultratv.tv.nativeapp.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.RecordingDao
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Repères du menu latéral (maquette « Menu latéral ») : nombre de favoris et enregistrements en cours. */
@HiltViewModel
class RailBadgesViewModel @Inject constructor(
    providers: ProviderRepository,
    catalog: CatalogRepository,
    recordings: RecordingDao,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val favorites: StateFlow<Int> = providers.observeProviders()
        .map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(0) else catalog.allFavoritesCount(id) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val recording: StateFlow<Int> = recordings.observeRunningCount().distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

/** Texte de repère d'une entrée du menu ouvert (pur, testé) : null = rien à droite du libellé. */
internal fun railBadge(route: String, favorites: Int, recording: Int, runningLabel: (Int) -> String): String? = when (route) {
    "favorites" -> favorites.takeIf { it > 0 }?.toString()
    "recordings" -> recording.takeIf { it > 0 }?.let(runningLabel)
    else -> null
}

/** Pastille d'accent sur l'icône (menu replié) : enregistrement en cours. */
internal fun railDot(route: String, recording: Int): Boolean = route == "recordings" && recording > 0
