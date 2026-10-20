package com.ultratv.tv.nativeapp.data.downloads

import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import java.io.File

/**
 * Lecture hors ligne : table (source, type, identifiant) → fichier téléchargé TERMINÉ, tenue à jour par
 * [DownloadRepository.start] depuis la base. Lue de façon synchrone au lancement de la lecture (un seul point
 * d'entrée dans le lecteur) : quel que soit l'écran d'origine (fiche, « Continuer à regarder », épisode suivant
 * automatique, lien profond), un film / épisode téléchargé est lu depuis le fichier local (`file://`, lu par Media3
 * comme par VLC). Le contexte de lecture garde l'URL distante : historique, reprise et Trakt fonctionnent comme d'habitude.
 */
object OfflineMedia {
    @Volatile private var files: Map<String, String> = emptyMap()

    internal fun update(completed: List<DownloadEntity>) {
        files = completed.associate { DownloadPlan.key(it.providerId, it.kind, it.remoteId) to it.filePath }
    }

    /** Fichier local de l'élément s'il est téléchargé et présent sur le disque, sinon null. */
    fun localPath(providerId: Long, kind: String, remoteId: String): String? =
        files[DownloadPlan.key(providerId, kind, remoteId)]?.takeIf { File(it).isFile }

    /**
     * URL à lire : `file://…` si [item] (film / épisode) est téléchargé et que [url] est bien son flux, sinon [url]
     * inchangée (direct, replay, timeshift, autre élément…).
     */
    fun resolve(item: PlaybackContext.Item?, url: String): String {
        if (item == null || (item.kind != DownloadKind.MOVIE && item.kind != DownloadKind.EPISODE)) return url
        if (url != item.streamUrl) return url
        return localPath(item.providerId, item.kind, item.remoteId)?.let { "file://$it" } ?: url
    }
}
