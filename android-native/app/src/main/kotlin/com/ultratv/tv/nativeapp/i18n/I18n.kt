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

    /** Langue de l'appareil au lancement (avant tout changement de la langue par défaut). */
    private val deviceLocale: Locale = Locale.getDefault()

    /**
     * Langue par défaut de Java = langue de l'APPLI (chiffres, heures, dates formatés par le code) : une box en arabe avec
     * l'appli en français affichait des chiffres arabo-indiens. [AppLang.System] : on revient à la langue de l'appareil.
     */
    fun applyDefaultLocale(lang: AppLang) {
        // Chiffres LATINS imposés (nu-latn) : la langue par défaut sert aussi aux adresses, requêtes et dates construites par
        // le code (String.format, NumberFormat). En arabe, les chiffres arabo-indiens y rendaient la synchro impossible
        // (« Chargement du catalogue… » sans fin). Les textes traduits gardent leurs propres chiffres (ressources).
        val base = if (lang == AppLang.System) deviceLocale else Locale.forLanguageTag(lang.code)
        val target = Locale.Builder().setLocale(base).setUnicodeLocaleKeyword("nu", "latn").build()
        if (Locale.getDefault() != target) Locale.setDefault(target)
    }

    /** Langue effective : [AppLang.System] suit la langue de l'appareil, l'anglais si elle n'est pas prise en charge. */
    fun resolve(lang: AppLang): AppLang {
        if (lang != AppLang.System) return lang
        val deviceCode = appContext?.resources?.configuration?.locales?.get(0)?.language ?: Locale.getDefault().language
        return AppLang.entries.firstOrNull { it != AppLang.System && it.baseLanguage == deviceCode } ?: AppLang.English
    }

    /** `Resources` configurées sur la langue (résolue) ; l'anglais utilise res/values (aucun dossier `values-en`). */
    fun resources(lang: AppLang): Resources {
        val ctx = appContext ?: error("I18n.init(context) n'a pas été appelé")
        val resolved = resolve(lang)
        return resourcesCache.getOrPut(resolved.code) {
            val cfg = android.content.res.Configuration(ctx.resources.configuration)
            // Chiffres LATINS (nu-latn) aussi pour les textes à nombre (« %1\$d ») : sans cela, l'arabe mêlait des chiffres
            // arabo-indiens (5 textes formatés par les ressources) aux chiffres latins de tout le reste de l'interface, et le
            // résultat changeait selon la version d'Android ou de Java. L'extension ne change pas le dossier `values-xx` choisi.
            cfg.setLocale(resourceLocale(resolved.code))
            ctx.createConfigurationContext(cfg).resources
        }
    }

    /**
     * Langue des ressources d'une langue de l'appli. Chiffres latins (nu-latn) à partir d'Android 9 seulement : avant, le
     * système ne comprend pas l'extension « -u-nu-latn », la prend pour une écriture latine imposée et écarte `values-ar`,
     * `values-el` ou `values-th` — toute l'interface retomberait en anglais. Sous Android 7 et 8 : langue nue, comme avant.
     */
    internal fun resourceLocale(code: String, sdkInt: Int = android.os.Build.VERSION.SDK_INT): Locale {
        val base = Locale.forLanguageTag(code)
        return if (sdkInt >= android.os.Build.VERSION_CODES.P) Locale.Builder().setLocale(base).setUnicodeLocaleKeyword("nu", "latn").build() else base
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
