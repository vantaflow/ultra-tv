package com.ultratv.tv.nativeapp.data.remote

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Touche de navigation injectée dans l'appli (jamais dans le système : aucune permission). */
enum class RemoteKey { UP, DOWN, LEFT, RIGHT, OK, BACK }

/** Ce que la télécommande du téléphone demande à l'interface. Le lecteur et l'activité principale l'observent. */
sealed interface RemoteAction {
    /** Lecture / pause (lecteur). */
    data object PlayPause : RemoteAction
    /** Saut relatif en millisecondes (lecteur) ; négatif = en arrière. */
    data class Seek(val deltaMs: Long) : RemoteAction
    /** Chaîne suivante (true) ou précédente (false) en direct (lecteur). */
    data class Zap(val forward: Boolean) : RemoteAction
    /** Flèche / OK / Retour (activité principale). */
    data class Key(val key: RemoteKey) : RemoteAction
}

/** Traduction des commandes de la page (`/api/control`) en [RemoteAction]. Liste blanche : tout le reste est refusé. */
object RemoteCommands {
    fun parse(cmd: String?): RemoteAction? = when (cmd?.trim()?.lowercase()) {
        "playpause" -> RemoteAction.PlayPause
        "back30" -> RemoteAction.Seek(-30_000)
        "back10" -> RemoteAction.Seek(-10_000)
        "fwd10" -> RemoteAction.Seek(10_000)
        "fwd30" -> RemoteAction.Seek(30_000)
        "prev" -> RemoteAction.Zap(false)
        "next" -> RemoteAction.Zap(true)
        "up" -> RemoteAction.Key(RemoteKey.UP)
        "down" -> RemoteAction.Key(RemoteKey.DOWN)
        "left" -> RemoteAction.Key(RemoteKey.LEFT)
        "right" -> RemoteAction.Key(RemoteKey.RIGHT)
        "ok" -> RemoteAction.Key(RemoteKey.OK)
        "back" -> RemoteAction.Key(RemoteKey.BACK)
        else -> null
    }
}

/**
 * Canal entre le serveur local et l'interface, comme `StartupNav.searchRequest` mais pour des actions ponctuelles.
 * Sans abonné (lecteur fermé), un saut ou une pause est simplement perdu : rien n'est mis en file.
 */
@Singleton
class RemoteBus @Inject constructor() {
    private val _actions = MutableSharedFlow<RemoteAction>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val actions: SharedFlow<RemoteAction> = _actions

    /** État de lecture publié par le lecteur (null = pas de lecteur). */
    private val _playing = MutableStateFlow<Boolean?>(null)
    val playing: StateFlow<Boolean?> = _playing

    fun send(action: RemoteAction) { _actions.tryEmit(action) }
    fun setPlaying(value: Boolean?) { _playing.value = value }
}
