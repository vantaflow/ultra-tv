package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.ui.player.PlayerExtraStrings
import com.ultratv.tv.nativeapp.ui.profile.ProfileStrings
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Garde-fou de traduction : toutes les tables de libellés (Strings, DesignStrings + lot B1,
 * PlayerExtraStrings, ProfileStrings) doivent exister en français, espagnol et arabe, et une valeur
 * ne peut pas rester identique à l'anglais (sauf noms propres, sigles et symboles listés ci-dessous).
 * L'arabe doit en plus contenir des lettres arabes.
 */
class TranslationsCompleteTest {
    /**
     * Mots légitimement identiques à l'anglais (noms propres, sigles, termes techniques ou mots communs aux langues).
     * Comparés en minuscules, après retrait des jetons de format (%d, %1$s), des chiffres et des unités (min, h).
     */
    private val invariant = setOf(
        "ultra tv", "tmdb", "opensubtitles", "xtream", "codes", "m3u", "epg", "xmltv", "hd", "fhd", "4k", "uhd", "sd", "vlc", "exoplayer",
        "pin", "mac", "url", "http", "https", "wi-fi", "live", "replay", "ok", "cyan", "lime", "menu", "total", "audio", "version", "source",
        "pro", "cache", "format", "cloud", "code", "film", "notes", "original", "pause", "stop", "zed",
        // FR / ES : mots identiques dans la langue cible
        "guide", "stats", "cast", "diagnostic", "position", "type", "mobile", "animations", "auto", "zoom", "sources", "stable", "active",
        "programmes", "series", "hardware", "software", "manual", "no", "sections", "amoled", "zapping", "rec", "e", "s", "—", "·",
    )
    private val arabicLetter = Regex("[\\u0600-\\u06FF]")
    private val latinLetter = Regex("[A-Za-zÀ-ÿ]")

    private fun sample(t: Class<*>): Any? = when (t) {
        Int::class.javaPrimitiveType, Int::class.javaObjectType -> 3
        Long::class.javaPrimitiveType, Long::class.javaObjectType -> 3L
        Boolean::class.javaPrimitiveType -> true
        Double::class.javaPrimitiveType -> 1.5
        Float::class.javaPrimitiveType -> 1.5f
        String::class.java -> "Zed"
        else -> UnsupportedOperationException()
    }

    /** Lit toutes les valeurs textuelles d'une table : getters, fonctions à paramètres simples, extensions. */
    private fun read(target: Any, extra: List<Class<*>>): Map<String, String> {
        val out = sortedMapOf<String, String>()
        fun collect(m: Method, receiver: Any?, skipFirst: Boolean) {
            if (m.returnType != String::class.java && m.returnType != List::class.java) return
            val ptypes = m.parameterTypes.drop(if (skipFirst) 1 else 0)
            val args = ptypes.map { sample(it) }
            if (args.any { it is UnsupportedOperationException }) return
            m.isAccessible = true
            val full = if (skipFirst) listOf(target) + args else args
            val v = m.invoke(if (skipFirst) null else receiver, *full.toTypedArray()) ?: return
            out["${m.name}(${ptypes.joinToString { it.simpleName }})"] = if (v is List<*>) v.joinToString("|") else v.toString()
        }
        for (m in target.javaClass.declaredMethods) {
            if (Modifier.isStatic(m.modifiers) || m.isSynthetic || m.name == "toString" || m.name.startsWith("component") || m.name == "copy") continue
            if (m.name == "getLang" || m.name == "t") continue
            collect(m, target, false)
        }
        for (c in extra) for (m in c.declaredMethods) {
            if (!Modifier.isStatic(m.modifiers) || m.parameterCount == 0 || m.parameterTypes[0] != target.javaClass || m.isSynthetic) continue
            collect(m, null, true)
        }
        return out
    }

    private fun strings(name: String): Map<String, String> {
        val f = Class.forName("com.ultratv.tv.nativeapp.i18n.StringsKt").getDeclaredField(name).apply { isAccessible = true }
        val s = f.get(null)
        return s.javaClass.declaredFields.filter { it.type == String::class.java }.associate { it.isAccessible = true; it.name to (it.get(s) as String) }
    }

    private val tables: Map<String, (AppLang) -> Map<String, String>> = mapOf(
        "Strings" to { l -> strings(mapOf(AppLang.English to "EN", AppLang.French to "FR", AppLang.Spanish to "ES", AppLang.Arabic to "AR").getValue(l)) },
        "DesignStrings" to { l -> read(DesignStrings(l), listOf(Class.forName("com.ultratv.tv.nativeapp.i18n.DesignStringsB1Kt"))) },
        "PlayerExtraStrings" to { l -> read(PlayerExtraStrings(l), emptyList()) },
        "ProfileStrings" to { l -> read(ProfileStrings(l), emptyList()) },
    )

    private fun isExempt(v: String): Boolean {
        val words = v.replace(Regex("%\\d*\\$?[sdf]"), " ").replace(Regex("[0-9]+"), " ").lowercase()
            .split(Regex("[\\s|·/:+()\\-—,.]+")).filter { it.isNotBlank() && it != "min" && it != "h" }
        return words.all { it in invariant }
    }

    @Test fun tables_enFrEsAr_completesEtTraduites() {
        val problems = mutableListOf<String>()
        for ((table, load) in tables) {
            val en = load(AppLang.English)
            assertTrue("$table : table anglaise vide (réflexion cassée ?)", en.size > 20)
            for (lang in listOf(AppLang.French, AppLang.Spanish, AppLang.Arabic)) {
                val tr = load(lang)
                for ((key, enValue) in en) {
                    val v = tr[key]
                    when {
                        v == null -> problems += "$table/${lang.code} : clé absente $key"
                        v.isBlank() && enValue.isNotBlank() -> problems += "$table/${lang.code} : $key vide"
                        lang == AppLang.Arabic && latinLetter.containsMatchIn(v) && !arabicLetter.containsMatchIn(v) && !isExempt(enValue) ->
                            problems += "$table/ar : $key sans arabe : « $v »"
                        lang != AppLang.Arabic && v == enValue && !isExempt(enValue) -> problems += "$table/${lang.code} : $key identique à l'anglais : « $v »"
                    }
                }
            }
        }
        assertTrue("${problems.size} problème(s) de traduction :\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
