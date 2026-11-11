package com.ultratv.tv.nativeapp.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Complétude des traductions, lue directement dans les fichiers `strings.xml` de res/values et res/values-xx (sans réseau, sans Android).
 *  - langues officielles : toute clé de l'anglais existe, sauf la liste blanche (`i18n-identical-whitelist.txt`,
 *    clés volontairement identiques à l'anglais : le repli natif affiche l'anglais) ;
 *  - mêmes variables (%1$s…) que l'anglais, quantités de pluriel valides pour la langue ;
 *  - aucune clé orpheline (dans une traduction mais pas dans l'anglais) ;
 *  - langues communautaires (pl…) : incomplètes tolérées, couverture affichée, échec seulement sur une variable
 *    différente ou une clé orpheline.
 */
class StringsXmlCompletenessTest {
    companion object {
        /** Langues complètes, maintenues avec l'appli. */
        val OFFICIAL = listOf("fr", "es", "ar")

        /** Langues proposées par la communauté, éventuellement partielles (vide pour l'instant ; ex. « pl »). */
        val COMMUNITY: List<String> = emptyList()

        /** Quantités de pluriel CLDR valides par langue (sans « other » : il est toujours exigé). */
        val QUANTITIES = mapOf(
            "en" to setOf("one", "other"),
            "fr" to setOf("one", "many", "other"),
            "es" to setOf("one", "many", "other"),
            "ar" to setOf("zero", "one", "two", "few", "many", "other"),
            "pl" to setOf("one", "few", "many", "other"),
        )
    }

    private sealed interface Res {
        data class Str(val text: String) : Res
        data class Plural(val items: Map<String, String>) : Res
        data class Arr(val items: List<String>) : Res
    }

    private fun resDir(): File = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }

    private fun load(folder: String): Map<String, Res> {
        val f = File(resDir(), "$folder/strings.xml")
        if (!f.exists()) return emptyMap()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val out = linkedMapOf<String, Res>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            val name = e.getAttribute("name")
            assertTrue("clé en double dans $folder : $name", name !in out)
            out[name] = when (e.tagName) {
                "string" -> Res.Str(e.textContent)
                "plurals" -> Res.Plural(children(e, "item").associate { it.getAttribute("quantity") to it.textContent })
                "string-array" -> Res.Arr(children(e, "item").map { it.textContent })
                else -> error("balise inattendue <${e.tagName}> dans $folder")
            }
        }
        return out
    }

    private fun children(e: Element, tag: String): List<Element> =
        (0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? Element }.filter { it.tagName == tag }

    private val spec = Regex("%(?:(\\d+)\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([sdfxXeEgGcb])")

    /** Variables d'un texte : « 1s », « 2d »… (%% et %n ignorés ; non positionnel numéroté dans l'ordre). */
    private fun vars(text: String): Set<String> {
        var auto = 0
        return spec.findAll(text.replace("%%", "")).map { m ->
            val idx = m.groupValues[1].ifEmpty { (++auto).toString() }
            idx + m.groupValues[2]
        }.toSet()
    }

    private fun whitelist(): Set<String> =
        javaClass.classLoader!!.getResourceAsStream("i18n-identical-whitelist.txt")!!.readBytes().toString(Charsets.UTF_8)
            .lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()

    private fun folderOf(lang: String) = if (lang == "en") "values" else "values-$lang"

    @Test fun anglais_estLaSource_nonVide() {
        val en = load("values")
        assertTrue("anglais trop petit : ${en.size}", en.size > 1000)
    }

    @Test fun languesOfficielles_completes_variablesEtQuantitesValides() {
        val en = load("values")
        val wl = whitelist()
        val problems = mutableListOf<String>()
        val usedWl = mutableSetOf<String>()
        for (lang in OFFICIAL) {
            val tr = load(folderOf(lang))
            for (k in en.keys) if (k !in tr) {
                if ("$lang|$k" in wl) usedWl += "$lang|$k" else problems += "$lang : clé absente $k"
            }
            checkCommon(lang, en, tr, problems)
        }
        val stale = wl - usedWl
        assertTrue("Liste blanche : entrées qui ne correspondent à aucune clé absente :\n${stale.joinToString("\n")}", stale.isEmpty())
        assertTrue("${problems.size} problème(s) :\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun languesCommunautaires_couvertureAffichee_variablesEtOrphelines() {
        val en = load("values")
        val problems = mutableListOf<String>()
        for (lang in COMMUNITY) {
            val tr = load(folderOf(lang))
            println("Couverture $lang : ${"%.1f".format(100.0 * tr.keys.count { it in en } / en.size)} % (${tr.size}/${en.size})")
            checkCommon(lang, en, tr, problems)
        }
        assertTrue("${problems.size} problème(s) :\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** Orphelines, types, variables, quantités : communs aux langues officielles et communautaires. */
    private fun checkCommon(lang: String, en: Map<String, Res>, tr: Map<String, Res>, problems: MutableList<String>) {
        for ((k, v) in tr) {
            val e = en[k]
            if (e == null) { problems += "$lang : clé orpheline $k (absente de l'anglais)"; continue }
            if (e::class != v::class) { problems += "$lang : $k n'a pas le même type que l'anglais"; continue }
            when (v) {
                is Res.Str -> {
                    val a = vars((e as Res.Str).text)
                    val b = vars(v.text)
                    if (a != b) problems += "$lang : variables de $k différentes (en $a, $lang $b)"
                }
                is Res.Plural -> {
                    val enVars = (e as Res.Plural).items.values.flatMap { vars(it) }.toSet()
                    val allowed = QUANTITIES.getValue(lang)
                    if ("other" !in v.items) problems += "$lang : $k sans quantité « other »"
                    for ((q, t) in v.items) {
                        if (q !in allowed) problems += "$lang : $k quantité « $q » invalide pour $lang"
                        if (!enVars.containsAll(vars(t))) problems += "$lang : $k/$q a des variables inconnues de l'anglais : ${vars(t) - enVars}"
                    }
                }
                is Res.Arr -> if (v.items.size != (e as Res.Arr).items.size) problems += "$lang : $k n'a pas le même nombre d'éléments que l'anglais"
            }
        }
    }
}
