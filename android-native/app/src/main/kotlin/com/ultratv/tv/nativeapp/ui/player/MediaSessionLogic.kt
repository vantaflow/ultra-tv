package com.ultratv.tv.nativeapp.ui.player

import androidx.media3.common.Player
import com.ultratv.tv.nativeapp.ui.player.engine.Phase

/** Ce qui est lu, tel que le système l'affiche (Google TV, Fire TV, écran de verrouillage, commandes Bluetooth). */
data class NowPlaying(
    val title: String,
    /** Chaîne (direct) ou série (épisode) ; null pour un film. */
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val live: Boolean = false,
)

/** Règles pures de la session média : métadonnées, état et commandes exposés au système. */
object MediaSessionLogic {
    /**
     * Titre / sous-titre / image selon le type de contenu :
     * - direct : programme en cours en titre et chaîne en sous-titre (la chaîne seule sans guide) ;
     * - épisode (« Série · S01E02 · Titre ») : « S01E02 · Titre » en titre, la série en sous-titre ;
     * - film : titre seul.
     */
    fun nowPlaying(kind: String?, itemTitle: String?, fallbackTitle: String, programmeTitle: String?, poster: String?): NowPlaying {
        val name = itemTitle?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val art = artwork(poster)
        return when (kind) {
            "LIVE" -> {
                val prog = programmeTitle?.trim()?.takeIf { it.isNotEmpty() }
                if (prog != null) NowPlaying(prog, name, art, live = true) else NowPlaying(name, null, art, live = true)
            }
            "EPISODE" -> {
                val parts = name.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size >= 2) NowPlaying(parts.drop(1).joinToString(" · "), parts.first(), art) else NowPlaying(name, null, art)
            }
            else -> NowPlaying(name, null, art)
        }
    }

    /** Image utilisable par le système : une adresse réseau ou un contenu local ; le reste (vide, chemin relatif) est ignoré. */
    fun artwork(poster: String?): String? = poster?.trim()?.takeIf {
        it.startsWith("http://", true) || it.startsWith("https://", true) || it.startsWith("content://") || it.startsWith("file://")
    }

    /** Phase de la session de lecture → état Media3 : chargement = tampon, erreur = inactif. */
    fun playbackState(phase: Phase): Int = when (phase) {
        Phase.LOADING -> Player.STATE_BUFFERING
        Phase.PLAYING -> Player.STATE_READY
        Phase.ENDED -> Player.STATE_ENDED
        Phase.ERROR -> Player.STATE_IDLE
    }

    /** « Lecture voulue » : en chargement on veut lire ; ensuite c'est l'état réel du moteur (en pause = faux). */
    fun playWhenReady(phase: Phase, enginePlaying: Boolean?): Boolean = when (phase) {
        Phase.LOADING -> true
        Phase.PLAYING -> enginePlaying ?: true
        Phase.ENDED, Phase.ERROR -> false
    }

    /** Avance / retour système : seulement pour un contenu à la demande de durée connue (jamais le direct). */
    fun seekable(live: Boolean, durationMs: Long): Boolean = !live && durationMs > 0
}
