package com.ultratv.tv.nativeapp.ui.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Compteur de recompositions du Guide TV, ACTIVABLE EN TEST SEULEMENT : l'application ne fournit jamais de sonde, donc
 * [LocalGuideProbe] reste à null et chaque point de mesure ne fait qu'une lecture de ce local (aucun effet, aucune
 * allocation). Un test fournit une [GuideProbe] et relève, après chaque geste, ce qui a été recomposé.
 */
internal class GuideProbe {
    /** Recompositions du corps d'une ligne, par identifiant de chaîne. */
    val rows = HashMap<Long, Int>()
    /** Recompositions du contenu d'une case, par identifiant de programme. */
    val cells = HashMap<Long, Int>()
    var header = 0
    var preview = 0
    /** Recompositions de l'écran entier ([GuideTvContent]). */
    var screen = 0

    fun clear() { rows.clear(); cells.clear(); header = 0; preview = 0; screen = 0 }
    val rowTotal get() = rows.values.sum()
    val cellTotal get() = cells.values.sum()
    override fun toString() = "écran=$screen lignes=$rowTotal(${rows.size}) cases=$cellTotal(${cells.size}) en-tête=$header aperçu=$preview"
}

// Les points de mesure sont `inline` : sinon une recomposition du parent saute l'appel (mêmes paramètres) et ne compterait rien.
internal val LocalGuideProbe = staticCompositionLocalOf<GuideProbe?> { null }

@Composable
internal inline fun ProbeRow(channelId: Long) {
    val p = LocalGuideProbe.current ?: return
    SideEffect { p.rows.merge(channelId, 1, Int::plus) }
}

@Composable
internal inline fun ProbeCell(programId: Long) {
    val p = LocalGuideProbe.current ?: return
    SideEffect { p.cells.merge(programId, 1, Int::plus) }
}

@Composable
internal inline fun ProbeHeader() {
    val p = LocalGuideProbe.current ?: return
    SideEffect { p.header++ }
}

@Composable
internal inline fun ProbePreview() {
    val p = LocalGuideProbe.current ?: return
    SideEffect { p.preview++ }
}

@Composable
internal inline fun ProbeScreen() {
    val p = LocalGuideProbe.current ?: return
    SideEffect { p.screen++ }
}
