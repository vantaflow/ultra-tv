package com.ultratv.tv.nativeapp.data.config

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identifiant aléatoire propre à cette installation (aucun lien avec un compte ni le matériel). Partagé par les
 * statistiques des alertes, le relevé anonyme d'installation et l'envoi de source depuis le web : même valeur,
 * même emplacement (« announcements » / « install ») que depuis les alertes, donc inchangé à la mise à jour.
 */
object InstallId {
    const val PREFS = "announcements"
    private const val KEY = "install"

    fun of(sp: SharedPreferences): String = sp.getString(KEY, null) ?: java.util.UUID.randomUUID().toString().also {
        sp.edit().putString(KEY, it).apply()
    }
}

@Singleton
class InstallIdStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    fun get(): String = InstallId.of(ctx.getSharedPreferences(InstallId.PREFS, Context.MODE_PRIVATE))
}
