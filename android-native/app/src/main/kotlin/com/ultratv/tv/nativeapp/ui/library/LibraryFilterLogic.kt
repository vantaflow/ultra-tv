package com.ultratv.tv.nativeapp.ui.library

import com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic

/** Filtre par TYPE de « Mes bibliothèques ». Les éléments sont des films ou des séries : pas d'entrée « Autres » (aucun autre type n'existe). */
enum class LibraryType { ALL, MOVIES, SERIES }

/** Filtres combinés : [type] et [sourceId] (identifiant du serveur ou du partage ; null = toutes les sources). */
data class LibraryFilter(val type: LibraryType = LibraryType.ALL, val sourceId: Long? = null)

/** Entrée du filtre de source : une par bibliothèque réellement configurée. [kind] : JELLYFIN, EMBY, PLEX ou NAS (…). */
data class SourceOption(val id: Long, val name: String, val kind: String)

/** Pourquoi il n'y a rien à montrer sous un filtre (pour un message clair au lieu d'un écran vide). */
enum class LibraryEmpty { MOVIES, SERIES, MOVIES_IN_SOURCE, SERIES_IN_SOURCE, SOURCE }

/** Ce que l'écran affiche : pastilles disponibles (vides = filtre masqué), filtre effectif, sections filtrées. */
data class LibraryView(
    val types: List<LibraryType>,
    val sources: List<SourceOption>,
    val filter: LibraryFilter,
    val rows: List<LibraryRow>,
    val empty: LibraryEmpty?,
)

/**
 * Logique pure (sans Android) des filtres de « Mes bibliothèques ». Calculée hors composition (ViewModel) : l'interface ne
 * fait que lire le résultat.
 */
object LibraryFilterLogic {
    /** Types proposés : « Tout, Films, Séries » seulement si les deux existent ; sinon un seul choix possible, filtre masqué (liste vide). */
    fun typeOptions(rows: List<LibraryRow>): List<LibraryType> =
        if (rows.any { it.movies > 0 } && rows.any { it.series > 0 }) listOf(LibraryType.ALL, LibraryType.MOVIES, LibraryType.SERIES) else emptyList()

    /** Sources proposées : une par bibliothèque, dans l'ordre de l'écran ; masqué (liste vide) avec une seule. */
    fun sourceOptions(rows: List<LibraryRow>): List<SourceOption> =
        if (rows.size < 2) emptyList() else rows.map { SourceOption(it.provider.id, it.provider.name, it.provider.kind) }.distinctBy { it.id }

    /** Un choix mémorisé qui n'existe plus (source supprimée, type disparu) retombe sur « tout ». */
    fun sanitize(filter: LibraryFilter, rows: List<LibraryRow>): LibraryFilter = LibraryFilter(
        type = if (filter.type in typeOptions(rows)) filter.type else LibraryType.ALL,
        sourceId = filter.sourceId?.takeIf { id -> rows.size >= 2 && rows.any { it.provider.id == id } },
    )

    fun apply(rows: List<LibraryRow>, requested: LibraryFilter): LibraryView {
        val f = sanitize(requested, rows)
        val inSource = if (f.sourceId == null) rows else rows.filter { it.provider.id == f.sourceId }
        val shown = if (f.type == LibraryType.ALL) inSource
        else inSource.mapNotNull { r -> r.copy(shelves = shelves(r.shelves, f.type)).takeIf { it.shelves.isNotEmpty() } }
        val empty = when {
            rows.isEmpty() -> null
            shown.isNotEmpty() && (f.type != LibraryType.ALL || f.sourceId == null || shown.any { it.shelves.isNotEmpty() }) -> null
            f.type == LibraryType.ALL && f.sourceId == null -> null
            f.type == LibraryType.MOVIES -> if (f.sourceId != null) LibraryEmpty.MOVIES_IN_SOURCE else LibraryEmpty.MOVIES
            f.type == LibraryType.SERIES -> if (f.sourceId != null) LibraryEmpty.SERIES_IN_SOURCE else LibraryEmpty.SERIES
            else -> LibraryEmpty.SOURCE
        }
        return LibraryView(typeOptions(rows), sourceOptions(rows), f, if (empty != null) emptyList() else shown, empty)
    }

    /**
     * Rangées d'un serveur sous un filtre de type (≠ ALL) : « Reprendre » et « Ajoutés récemment » gardent les affiches du type ;
     * Films / Séries / une bibliothèque suivent leur type ; un type inconnu n'est gardé que sous « Tout » (donc ici écarté).
     * Les affiches sont dédoublonnées : une clé répétée plante la liste paresseuse.
     */
    fun shelves(shelves: List<Shelf>, type: LibraryType): List<Shelf> {
        if (type == LibraryType.ALL) return shelves
        val wantSeries = type == LibraryType.SERIES
        return shelves.mapNotNull { sh ->
            when (sh.kind) {
                ShelfKind.RESUME, ShelfKind.RECENT -> {
                    val tiles = sh.tiles.filter { it.isSeries == wantSeries }.distinctBy { it.isSeries to it.id }
                    sh.takeIf { tiles.isNotEmpty() }?.copy(tiles = tiles)
                }
                ShelfKind.MOVIES -> sh.takeIf { !wantSeries }
                ShelfKind.SERIES -> sh.takeIf { wantSeries }
                ShelfKind.LIBRARY -> sh.takeIf { it.seeAllSeries == wantSeries }
            }
        }
    }

    /** Libellé « Nom · Genre » d'une source ; le genre est omis quand le nom le contient déjà. */
    fun sourceLabel(o: SourceOption): String {
        val genre = kindName(o.kind)
        return if (genre.isBlank() || o.name.contains(genre, ignoreCase = true)) o.name else "${o.name} · $genre"
    }

    fun kindName(kind: String): String = when (kind) {
        "JELLYFIN" -> "Jellyfin"; "EMBY" -> "Emby"; "PLEX" -> "Plex"; "NAS" -> "NAS"; else -> ""
    }
}

/** Dernier choix de filtres, gardé tant que l'appli tourne (aller-retour vers une fiche ou un autre écran) ; jamais écrit sur disque. */
object LibraryFilterMemory {
    @Volatile var last: LibraryFilter = LibraryFilter()
}
