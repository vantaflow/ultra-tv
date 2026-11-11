package com.ultratv.tv.nativeapp.i18n

import android.content.Context
import android.content.res.Resources
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Accès unique aux textes de l'interface, indépendant de la langue du système : la langue choisie dans les Réglages
 * ([AppLang]) sélectionne les `Resources` (res/values, values-fr, values-es, values-ar…). Une clé absente d'une langue
 * retombe sur l'anglais (res/values), repli natif d'Android.
 *
 * Fonctionne dans et hors Compose, sans redémarrer l'activité : chaque langue a ses `Resources` (cache), et les
 * porteurs de textes ([DesignStrings], [Strings]…) ne gardent que la langue, pas le texte.
 *
 * Ajouter une langue : un dossier `res/values-xx/strings.xml` + une entrée dans l'enum [AppLang].
 */
object I18n {
    @Volatile private var appContext: Context? = null
    private val resourcesCache = ConcurrentHashMap<String, Resources>()
    private val objectCache = ConcurrentHashMap<String, Any>()

    /** À appeler tôt (Application.attachBaseContext) avec n'importe quel contexte de l'appli. */
    fun init(context: Context) {
        appContext = context.applicationContext ?: context
        resourcesCache.clear()
        objectCache.clear()
    }

    /** Langue effective : [AppLang.System] suit la langue de l'appareil, l'anglais si elle n'est pas prise en charge. */
    fun resolve(lang: AppLang): AppLang {
        if (lang != AppLang.System) return lang
        val deviceCode = appContext?.resources?.configuration?.locales?.get(0)?.language ?: Locale.getDefault().language
        return AppLang.entries.firstOrNull { it != AppLang.System && it.code == deviceCode } ?: AppLang.English
    }

    /** `Resources` configurées sur la langue (résolue) ; l'anglais utilise res/values (aucun dossier `values-en`). */
    fun resources(lang: AppLang): Resources {
        val ctx = appContext ?: error("I18n.init(context) n'a pas été appelé")
        val resolved = resolve(lang)
        return resourcesCache.getOrPut(resolved.code) {
            val cfg = android.content.res.Configuration(ctx.resources.configuration)
            cfg.setLocale(Locale(resolved.code))
            ctx.createConfigurationContext(cfg).resources
        }
    }

    fun string(lang: AppLang, id: Int, vararg args: Any): String {
        val r = resources(lang)
        return if (args.isEmpty()) r.getString(id) else r.getString(id, *args)
    }

    /** Pluriel : `qty` choisit la forme (one/few/many/other selon la langue), `args` remplit les %1$s. */
    fun plural(lang: AppLang, id: Int, qty: Int, vararg args: Any): String {
        val r = resources(lang)
        return if (args.isEmpty()) r.getQuantityString(id, qty) else r.getQuantityString(id, qty, *args)
    }

    fun stringArray(lang: AppLang, id: Int): List<String> = resources(lang).getStringArray(id).toList()

    /** Objet construit une seule fois par (type, langue) : par exemple le [Strings] d'une langue. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> cached(kind: String, lang: AppLang, build: () -> T): T =
        objectCache.getOrPut("$kind/${lang.code}") { build() } as T
}

/** Raccourcis des fichiers de libellés de [DesignStrings] (même paquet) : `tx(R.string.cle, args…)`. */
internal fun DesignStrings.tx(id: Int, vararg args: Any): String = I18n.string(lang, id, *args)

internal fun DesignStrings.tp(id: Int, qty: Int, vararg args: Any): String = I18n.plural(lang, id, qty, *args)
