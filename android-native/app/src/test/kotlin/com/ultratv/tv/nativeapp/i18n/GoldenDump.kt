package com.ultratv.tv.nativeapp.i18n

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Photo de référence des textes de l'interface : pour une langue, la valeur de chaque propriété et de chaque
 * fonction des tables de libellés, appelées avec des arguments d'exemple (nombres 0, 1, 2, 5, 22 pour les pluriels).
 * Utilisée à l'identique avant et après la migration vers `strings.xml` (réflexion : aucune dépendance aux
 * implémentations).
 */
object GoldenDump {
    private const val PKG = "com.ultratv.tv.nativeapp"
    private val ints = listOf(0, 1, 2, 5, 22)

    /** Classes « porteuses » construites avec (AppLang), et les fichiers d'extensions (classes *Kt) de DesignStrings. */
    private val holders = listOf(
        "$PKG.i18n.DesignStrings", "$PKG.i18n.SimpleStrings", "$PKG.ui.player.PlayerExtraStrings",
        "$PKG.ui.memory.MemoryStrings", "$PKG.ui.downloads.DownloadStrings", "$PKG.ui.profile.ProfileStrings",
        "$PKG.ui.settings.CloudSyncStrings", "$PKG.ui.settings.DnsStrings", "$PKG.ui.account.AccountStrings",
        "$PKG.ui.live.LivePreviewStrings",
    )
    private val extensionFiles = listOf(
        "CatalogCategoryStringsKt", "DesignStringsB1Kt", "DirectGuideStringsKt", "GuideStringsKt", "HomeV2StringsKt",
        "PersonStringsKt", "PersonalizationStringsKt", "PhoneSendStringsKt", "RemoteStringsKt", "TraktStringsKt",
    ).map { "$PKG.i18n.$it" }

    /** Membres ignorés faute d'arguments d'exemple (à garder vide ou justifié). */
    val skipped = sortedSetOf<String>()

    /** @param strings le `Strings` de la langue ; @param mobile le `MobileStrings` de la langue. */
    fun dump(lang: AppLang, strings: Any, mobile: Any): Map<String, String> {
        val out = sortedMapOf<String, String>()
        dumpObject("Strings", strings, out, emptyList())
        dumpObject("MobileStrings", mobile, out, emptyList())
        for (name in holders) {
            val cls = Class.forName(name)
            val ctor = cls.getDeclaredConstructor(AppLang::class.java).apply { isAccessible = true }
            val obj = ctor.newInstance(lang)
            val ext = if (cls.simpleName == "DesignStrings") extensionFiles.map { Class.forName(it) } else emptyList()
            dumpObject(cls.simpleName, obj, out, ext)
        }
        return out
    }

    private fun dumpObject(label: String, obj: Any, out: MutableMap<String, String>, extensions: List<Class<*>>) {
        val cls = obj.javaClass
        for (m in cls.declaredMethods) {
            if (Modifier.isStatic(m.modifiers) || m.isSynthetic || !Modifier.isPublic(m.modifiers)) continue
            if (m.name in setOf("toString", "hashCode", "equals", "copy", "getLang") || m.name.startsWith("component")) continue
            invoke(label, m, obj, emptyList(), out)
        }
        for (ext in extensions) for (m in ext.declaredMethods) {
            if (!Modifier.isStatic(m.modifiers) || m.isSynthetic || !Modifier.isPublic(m.modifiers)) continue
            if (m.parameterCount == 0 || m.parameterTypes[0] != cls) continue
            invoke("$label/${ext.simpleName}", m, null, listOf(obj), out)
        }
    }

    private fun invoke(label: String, m: Method, receiver: Any?, prefixArgs: List<Any>, out: MutableMap<String, String>) {
        val types = m.parameterTypes.drop(prefixArgs.size)
        val rt = m.returnType
        val textual = rt == String::class.java || rt == List::class.java || rt == java.util.Locale::class.java
        val nested = rt.name.startsWith("$PKG.i18n.") && types.isEmpty()
        if (!textual && !nested) return
        val combos = combos(types) ?: run { skipped += "$label.${m.name}(${types.joinToString { it.simpleName }})"; return }
        for ((desc, args) in combos) {
            val key = "$label.${m.name}($desc)"
            val v = try {
                m.isAccessible = true
                m.invoke(receiver, *(prefixArgs + args).toTypedArray())
            } catch (e: java.lang.reflect.InvocationTargetException) {
                out[key] = "EXC:" + e.targetException.javaClass.simpleName; continue
            }
            when {
                v == null -> out[key] = "<null>"
                nested -> dumpObject("$label.${m.name}", v, out, emptyList())
                v is List<*> -> out[key] = v.joinToString("|")
                else -> out[key] = v.toString()
            }
        }
    }

    /** Combinaisons d'arguments d'exemple ; null si un type n'est pas pris en charge. */
    private fun combos(types: List<Class<*>>): List<Pair<String, List<Any?>>>? {
        if (types.isEmpty()) return listOf("" to emptyList())
        val numeric = types.any { it == Int::class.javaPrimitiveType || it == Long::class.javaPrimitiveType || it == Double::class.javaPrimitiveType }
        var acc: List<Pair<List<Pair<String, Any?>>, Int>> =
            if (numeric) ints.indices.map { listOf<Pair<String, Any?>>() to it } else listOf(listOf<Pair<String, Any?>>() to 0)
        for (t in types) {
            val next = mutableListOf<Pair<List<Pair<String, Any?>>, Int>>()
            for ((so, k) in acc) {
                val opts: List<Pair<String, Any?>> = when {
                    t == Int::class.javaPrimitiveType -> listOf("${ints[k]}" to ints[k])
                    t == Long::class.javaPrimitiveType -> listOf("${ints[k]}L" to ints[k].toLong())
                    t == Double::class.javaPrimitiveType -> listOf("${ints[k]}.5" to ints[k] + 0.5)
                    t == Boolean::class.javaPrimitiveType -> listOf("true" to true, "false" to false)
                    t == String::class.java -> listOf("Zed" to "Zed")
                    t.isEnum -> t.enumConstants.map { (it as Enum<*>).name to it }
                    t.name == "$PKG.data.xmltv.GuideCheck" -> guideChecks(t, k)
                    else -> return null
                }
                for (o in opts) next += (so + o) to k
            }
            acc = next
        }
        return acc.map { (l, _) -> l.joinToString(",") { it.first } to l.map { it.second } }
    }

    private fun guideChecks(t: Class<*>, k: Int): List<Pair<String, Any?>> {
        val res = mutableListOf<Pair<String, Any?>>()
        val code = ints[k]
        for (n in listOf("Ok", "Unreachable", "NotXmltv")) res += n to Class.forName(t.name + "$" + n).getField("INSTANCE").get(null)
        res += "Http$code" to Class.forName(t.name + "\$Http").getConstructor(Int::class.javaPrimitiveType).newInstance(code)
        val problem = Class.forName("$PKG.data.xmltv.GuideAddress\$Problem")
        val inv = Class.forName(t.name + "\$Invalid")
        for (p in problem.enumConstants) res += "Invalid${(p as Enum<*>).name}" to inv.getConstructor(problem).newInstance(p)
        return res
    }

    fun toJson(map: Map<String, String>): String =
        map.entries.joinToString(",\n", "{\n", "\n}\n") { "  ${org.json.JSONObject.quote(it.key)}: ${org.json.JSONObject.quote(it.value)}" }

    fun parseJson(text: String): Map<String, String> {
        val o = org.json.JSONObject(text)
        return o.keys().asSequence().associateWith { o.getString(it) }
    }
}
