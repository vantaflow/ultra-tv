package com.ultratv.tv.nativeapp.ui.common

import android.content.ActivityNotFoundException
import androidx.activity.result.ActivityResultLauncher

/**
 * Lance un sélecteur système (fichier, dossier, enregistrement). Beaucoup de box Android TV
 * n'ont pas d'appli « Fichiers » (DocumentsUI) : le lancement lève alors ActivityNotFoundException
 * au lieu de renvoyer un résultat vide. On affiche un message au lieu de planter.
 */
fun <I> ActivityResultLauncher<I>.launchOrToast(input: I, unavailable: String): Boolean =
    try {
        launch(input)
        true
    } catch (_: ActivityNotFoundException) {
        Toaster.err(unavailable)
        false
    }
