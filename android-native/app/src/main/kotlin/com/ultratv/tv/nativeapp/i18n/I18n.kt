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

    /**
     * Langue de l'appareil : relevée au lancement (avant tout changement de la langue par défaut), puis rafraîchie à chaque
     * changement de configuration ([onConfigurationChanged]) — la langue du système peut changer à chaud.
     */
    @Volatile private var deviceLocale: Locale = Locale.getDefault()

    /** Dernière langue de l'appli appliquée ([applyDefaultLocale]) : réappliquée après un changement de configuration. */
    @Volatile private var appliedLang: AppLang? = null

    /** Tests seulement : `Resources` imposées (langue nue…) pour vérifier que le formatage ne dépend pas d'elles. */
    @Volatile internal var resourcesOverride: ((AppLang) -> Resources)? = null

    /**
     * Langue par défaut de Java = langue de l'APPLI (chiffres, heures, dates formatés par le code) : une box en arabe avec
     * l'appli en français affichait des chiffres arabo-indiens. [AppLang.System] : on revient à la langue de l'appareil.
     */
    fun applyDefaultLocale(lang: AppLang) {
        appliedLang = lang
        // Chiffres LATINS imposés (nu-latn) : la langue par défaut sert aussi aux adresses, requêtes et dates construites par
        // le code (String.format, NumberFormat). En arabe, les chiffres arabo-indiens y rendaient la synchro impossible
        // (« Chargement du catalogue… » sans fin). Les textes traduits gardent leurs propres chiffres (ressources).
        val base = if (lang == AppLang.System) deviceLocale else Locale.forLanguageTag(lang.code)
        val target = Locale.Builder().setLocale(base).setUnicodeLocaleKeyword("nu", "latn").build()
        if (Locale.getDefault() != target) Locale.setDefault(target)
    }

    /**
     * À appeler après CHAQUE changement de configuration du processus (`Application.onConfigurationChanged`,
     * `Activity.onConfigurationChanged`) : le framework vient de remettre `Locale.getDefault()` sur la langue du système,
     * sans nu-latn (rotation, mode nuit, manette ou clavier branchés, HDMI, langue du système). Sans ce rappel, un appareil
     * en ar-EG ou ar-SA repassait en chiffres arabo-indiens jusqu'au prochain changement de langue de l'appli.
     * [systemLocale] : `newConfig.locales[0]`, la langue du système à jour.
     */
    fun onConfigurationChanged(systemLocale: Locale?) {
        if (systemLocale != null && systemLocale.language.isNotEmpty()) deviceLocale = systemLocale
        val lang = appliedLang ?: return
        applyDefaultLocale(lang)
    }

    /** Langue effective : [AppLang.System] suit la langue de l'appareil, l'anglais si elle n'est pas prise en charge. */
    fun resolve(lang: AppLang): AppLang {
        if (lang != AppLang.System) return lang
        val deviceCode = appContext?.resources?.configuration?.locales?.get(0)?.language ?: Locale.getDefault().language
        return AppLang.entries.firstOrNull { it != AppLang.System && it.baseLanguage == deviceCode } ?: AppLang.English
    }

    /** `Resources` configurées sur la langue (résolue) ; l'anglais utilise res/values (aucun dossier `values-en`). */
    fun resources(lang: AppLang): Resources {
        resourcesOverride?.let { return it(lang) }
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

    /**
     * Texte formaté PAR NOUS avec la locale d'affichage (chiffres latins), jamais par `Resources.getString(id, args)` : celui-ci
     * formate avec la locale des ressources, qui n'a `nu-latn` qu'à partir d'Android 9 (voir [resourceLocale]) — sous Android 7
     * et 8 les « %1$d » sortaient en chiffres arabo-indiens.
     */
    fun string(lang: AppLang, id: Int, vararg args: Any): String = stringIn(resources(lang), DisplayLocale.of(resolve(lang)), id, args)

    internal fun stringIn(r: Resources, display: Locale, id: Int, args: Array<out Any>): String {
        val raw = r.getString(id)
        return if (args.isEmpty()) raw else formatSafely(display, raw, args)
    }

    /**
     * Formatage d'un texte traduit qui ne plante jamais : une traduction mal formée (« 50 % م », un « % » isolé) levait
     * UnknownFormatConversionException et faisait tomber l'écran. Repli : le texte brut, et une ligne de journal (sans les
     * arguments, qui peuvent contenir un titre ou une adresse).
     */
    internal fun formatSafely(display: Locale, raw: String, args: Array<out Any?>): String =
        try {
            String.format(display, raw, *args)
        } catch (e: java.util.IllegalFormatException) {
            runCatching { com.ultratv.tv.nativeapp.RemoteLog.warn("i18n", "traduction mal formée (${e.javaClass.simpleName}) : ${raw.take(80)}") }
            raw
        }

    /** Pluriel : `qty` choisit la forme (one/few/many/other selon la langue), `args` remplit les %1$s. */
    fun plural(lang: AppLang, id: Int, qty: Int, vararg args: Any): String = pluralIn(resources(lang), DisplayLocale.of(resolve(lang)), id, qty, args)

    internal fun pluralIn(r: Resources, display: Locale, id: Int, qty: Int, args: Array<out Any>): String {
        val raw = r.getQuantityString(id, qty)
        return if (args.isEmpty()) raw else formatSafely(display, raw, args)
    }

    fun stringArray(lang: AppLang, id: Int): List<String> = resources(lang).getStringArray(id).toList()

    /** Objet construit une seule fois par (type, langue) : par exemple le [Strings] d'une langue. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> cached(kind: String, lang: AppLang, build: () -> T): T =
        objectCache.getOrPut("$kind/${lang.code}") { build() } as T
}

/**
 * Locale d'AFFICHAGE : point de passage unique de tout formatage (dates, nombres, pourcentages, textes à « %d ») — la langue
 * de l'appli avec les chiffres LATINS (0-9) même pour l'arabe (ou le persan, l'hindi…) dont les chiffres propres sont refusés
 * par le propriétaire. À ne PAS poser sur la `Configuration` des ressources avant Android 9 (voir [I18n.resourceLocale]).
 */
object DisplayLocale {
    /**
     * Langues dont les chiffres PAR DÉFAUT ne sont pas 0-9 (liste fixe : le résultat ne dépend ni du JDK ni d'ICU — sous un
     * JDK récent l'arabe nu donne des chiffres latins, sous Android des chiffres arabo-indiens).
     */
    private val ownDigits = setOf("ar", "fa", "ur", "ps", "ckb", "sd", "bn", "mr", "ne", "my", "dz", "ks", "as", "mni", "sat", "sa")

    /** Écritures à chiffres propres (« uz-Arab », « pa-Arab »…), quelle que soit la langue. */
    private val ownDigitScripts = setOf("Arab", "Beng", "Deva", "Mtei", "Olck", "Mymr", "Tibt")

    private val latinCache = java.util.concurrent.ConcurrentHashMap<Locale, Locale>()
    private val codeCache = java.util.concurrent.ConcurrentHashMap<String, Locale>()

    /**
     * [base] avec `nu-latn` si ses chiffres ne sont pas 0-9 (arabe…, ou un `nu` explicite comme « ar-EG-u-nu-arab » imposé
     * par les préférences régionales du système) ; les autres langues sont rendues telles quelles. Résultat mis en cache.
     */
    fun latin(base: Locale): Locale = latinCache.getOrPut(base) {
        val nu = base.getUnicodeLocaleType("nu")
        val needs = when {
            nu != null -> nu != "latn"
            base.language in ownDigits || base.script in ownDigitScripts -> true
            else -> runCatching { java.text.DecimalFormatSymbols.getInstance(base).zeroDigit != '0' }.getOrDefault(false)
        }
        if (needs) Locale.Builder().setLocale(base).setUnicodeLocaleKeyword("nu", "latn").build() else base
    }

    fun of(code: String): Locale = codeCache.getOrPut(code) { latin(Locale.forLanguageTag(code)) }

    /** [AppLang.System] suit la langue par défaut de Java (alignée sur l'appareil, chiffres latins imposés ici). */
    fun of(lang: AppLang): Locale = if (lang == AppLang.System) current() else of(lang.code)

    /** Langue par défaut de Java avec chiffres latins : pour les formatages sans langue connue. */
    fun current(): Locale = latin(Locale.getDefault())
}

/**
 * Chiffres latins pour tout ce qui part en base, dans une adresse ou dans un titre persisté (« S02E05 », « 01:32:00 ») :
 * jamais la locale par défaut, que le framework peut remettre en arabe à tout moment.
 */
object LatinDigits {
    /** « S02E05 » quelle que soit la locale. */
    fun episodeTag(season: Int, episode: Int): String = String.format(Locale.ROOT, "S%02dE%02d", season, episode)

    /**
     * Chiffres non latins (arabo-indiens, persans, devanagari…) remplacés par 0-9 : valeurs écrites par d'anciennes versions
     * (durées, « S٠٢E٠٥ »). Rend la même chaîne, sans allocation, quand il n'y a rien à remplacer.
     */
    fun normalize(s: String): String {
        if (s.none { it.isDigit() && it !in '0'..'9' }) return s
        val out = StringBuilder(s.length)
        for (c in s) {
            val d = if (c.isDigit() && c !in '0'..'9') Character.digit(c, 10) else -1
            out.append(if (d in 0..9) '0' + d else c)
        }
        return out.toString()
    }
}

/** Raccourcis des fichiers de libellés de [DesignStrings] (même paquet) : `tx(R.string.cle, args…)`. */
internal fun DesignStrings.tx(id: Int, vararg args: Any): String = I18n.string(lang, id, *args)

internal fun DesignStrings.tp(id: Int, qty: Int, vararg args: Any): String = I18n.plural(lang, id, qty, *args)
