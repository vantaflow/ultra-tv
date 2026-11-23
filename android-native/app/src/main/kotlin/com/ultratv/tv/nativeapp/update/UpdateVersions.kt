package com.ultratv.tv.nativeapp.update

/**
 * Versions publiées sur GitHub (« v1.3.73 », pré-version « v1.3.73-rc2 ») et choix de la release à proposer selon le canal
 * de mise à jour (règles pures, testées).
 *
 * Ordre : 1.3.72 < 1.3.73-rc1 < 1.3.73-rc2 < 1.3.73 < 1.3.74-rc1. Une pré-version d'une version est plus ancienne que la
 * version stable du même numéro.
 */
object UpdateVersions {

    /** [rc] : numéro de pré-version, null pour une version stable. */
    data class Version(val major: Int, val minor: Int, val patch: Int, val rc: Int? = null) : Comparable<Version> {
        val isPreRelease: Boolean get() = rc != null
        /** Nombre comparable sur une seule échelle : x.y.z sur 10 000 / 100, puis pré-versions sous la stable (99 = stable). */
        val rank: Long get() = (major * 10_000L + minor * 100L + patch) * 100L + (rc?.coerceIn(0, 98) ?: 99)
        val code: Int get() = major * 10_000 + minor * 100 + patch
        /**
         * versionCode Android (miroir de `app/build.gradle.kts`) : une pré-version -rcN est STRICTEMENT inférieure à la stable
         * du même numéro (99). Ex. 1.3.76-rc1 → 1 037 601, 1.3.76 → 1 037 699. Toujours > 10 375 (ancien schéma, 1.3.75).
         */
        val androidCode: Int get() = androidVersionCode(major, minor, patch, rc)
        override fun compareTo(other: Version): Int = rank.compareTo(other.rank)
    }

    /** major*1 000 000 + minor*10 000 + patch*100 + (rcN, 1..98 | 99 pour une stable). Mêmes règles dans build.gradle.kts. */
    fun androidVersionCode(major: Int, minor: Int, patch: Int, rc: Int?): Int =
        major * 1_000_000 + minor * 10_000 + patch * 100 + (rc?.coerceIn(0, 98) ?: 99)

    data class Release(val tag: String, val preRelease: Boolean, val draft: Boolean = false)

    private val PATTERN = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?(?:-rc(\d+))?$""", RegexOption.IGNORE_CASE)

    /** « v1.3.73-rc2 » → 1.3.73 rc 2 ; null si le nom n'a pas cette forme. */
    fun parse(tag: String): Version? {
        val m = PATTERN.matchEntire(tag.trim()) ?: return null
        val (a, b, c, rc) = m.destructured
        return Version(a.toInt(), b.toInt(), c.toIntOrNull() ?: 0, rc.toIntOrNull())
    }

    /**
     * Version installée : l'étiquette de publication embarquée à la compilation ([releaseTag], ex. « v1.3.73-rc2 ») quand
     * elle correspond au numéro de version, sinon le numéro seul (compilation locale = version stable de ce numéro).
     */
    fun installed(versionName: String, releaseTag: String): Version? {
        val base = parse(versionName) ?: return null
        val tagged = parse(releaseTag) ?: return base
        return if (tagged.code == base.code) tagged else base
    }

    /**
     * Release à proposer, ou null : canal stable = la release « latest » seulement ([releases] ne contient alors qu'elle) ;
     * canal pré-version = la plus récente des releases publiées, pré-versions comprises. Jamais une version plus ancienne
     * ou égale à celle installée : revenir au canal stable depuis une pré-version garde la pré-version jusqu'à la prochaine
     * version stable plus récente.
     */
    fun pick(releases: List<Release>, preReleaseChannel: Boolean, installed: Version): Release? =
        releases.asSequence()
            .filter { !it.draft }
            .mapNotNull { r -> parse(r.tag)?.let { r to it } }
            // Canal stable : ni release marquée pré-version, ni étiquette « -rcN ».
            .filter { (r, v) -> v > installed && (preReleaseChannel || (!r.preRelease && !v.isPreRelease)) }
            .maxByOrNull { it.second }?.first

    /** Retour au canal stable depuis une pré-version : il faut prévenir que la pré-version reste installée. */
    fun staysOnPreRelease(installed: Version?, preReleaseChannel: Boolean): Boolean = !preReleaseChannel && installed?.isPreRelease == true
}
