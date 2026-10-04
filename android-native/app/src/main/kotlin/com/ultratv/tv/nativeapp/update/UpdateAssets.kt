package com.ultratv.tv.nativeapp.update

/**
 * Choix de l'APK à télécharger parmi les assets d'une release : l'APK propre au premier ABI de l'appareil
 * (`UltraTV-<version>-<abi>.apk`, bien plus léger) vérifié par `SHA256SUMS.txt`, sinon repli sur l'universel
 * `UltraTV-debug.apk` + `UltraTV-debug.apk.sha256` (seul nom connu des versions ≤ 1.1.0, toujours publié).
 */
object UpdateAssets {
    const val UNIVERSAL = "UltraTV-debug.apk"
    const val SUMS = "SHA256SUMS.txt"

    data class Asset(val name: String, val url: String)

    /** [sha256Url] : fichier d'un seul condensat (universel) ; [sumsUrl] : SHA256SUMS.txt (APK par ABI). Exactement l'un des deux. */
    data class Choice(val apkName: String, val apkUrl: String, val sha256Url: String?, val sumsUrl: String?)

    fun choose(assets: List<Asset>, versionName: String, supportedAbis: List<String>): Choice? {
        fun find(n: String) = assets.firstOrNull { it.name.equals(n, ignoreCase = true) }
        val sums = find(SUMS)
        if (sums != null) {
            for (abi in supportedAbis) {
                val apk = find("UltraTV-$versionName-$abi.apk") ?: continue
                return Choice(apk.name, apk.url, null, sums.url)
            }
        }
        val uni = find(UNIVERSAL) ?: return null
        return Choice(uni.name, uni.url, find("$UNIVERSAL.sha256")?.url, null)
    }

    /** Condensat d'un fichier dans un `SHA256SUMS.txt` (« <hex>  <nom> », nom éventuellement précédé de « * »). "" si absent. */
    fun digestFor(sums: String, apkName: String): String {
        for (line in sums.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val hex = t.substringBefore(' ').substringBefore('\t').lowercase()
            val name = t.substring(hex.length).trim().removePrefix("*")
            if (name.equals(apkName, ignoreCase = true) && hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' }) return hex
        }
        return ""
    }
}
