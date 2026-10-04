package com.ultratv.tv.nativeapp.data.subtitles

import androidx.media3.ui.CaptionStyleCompat

enum class SubSize(val px1080: Int) { SMALL(32), MEDIUM(40), LARGE(48), XLARGE(56) }
enum class SubColor(val rgb: Int) { WHITE(0xFFFFFF), YELLOW(0xFFEB3B), CYAN(0x4DD0E1), GREEN(0x66BB6A) }
/** Opacité du fond derrière le texte. */
enum class SubBackground(val alpha: Float) { NONE(0f), SEMI(0.55f), OPAQUE(1f) }
enum class SubOutline { NONE, THIN, THICK }
/** Hauteur du texte : part de la hauteur de l'image réservée sous le texte. */
enum class SubPosition(val bottomFraction: Float) { BOTTOM(0.06f), RAISED(0.14f), HIGH(0.28f) }

/** Réglages d'apparence des sous-titres (aperçu en direct, appliqués aux deux moteurs). */
data class SubtitleStyle(
    val size: SubSize = SubSize.MEDIUM,
    val color: SubColor = SubColor.WHITE,
    val background: SubBackground = SubBackground.SEMI,
    val outline: SubOutline = SubOutline.THIN,
    val position: SubPosition = SubPosition.BOTTOM,
    /** Décalage de synchronisation (positif = sous-titres plus tard). */
    val delayMs: Int = 0,
) {
    companion object {
        val DEFAULT = SubtitleStyle()
        const val DELAY_STEP_MS = 100
        const val DELAY_MAX_MS = 10_000
    }
    fun withDelay(ms: Int) = copy(delayMs = ms.coerceIn(-DELAY_MAX_MS, DELAY_MAX_MS))
}

/** Valeurs ARGB / réelles prêtes pour `CaptionStyleCompat` + `SubtitleView` (Media3). */
data class CaptionSpec(
    val foregroundArgb: Int, val backgroundArgb: Int, val edgeType: Int, val edgeArgb: Int,
    val textSizeFraction: Float, val bottomPaddingFraction: Float,
)

/** Correspondance pure style -> moteurs. */
object SubtitleStyleMapper {
    private const val OPAQUE = 0xFF000000.toInt()

    fun toCaption(s: SubtitleStyle): CaptionSpec = CaptionSpec(
        foregroundArgb = OPAQUE or s.color.rgb,
        backgroundArgb = ((s.background.alpha * 255).toInt() shl 24),
        edgeType = when (s.outline) {
            SubOutline.NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
            SubOutline.THIN -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            SubOutline.THICK -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        },
        edgeArgb = OPAQUE,
        textSizeFraction = s.size.px1080 / 1080f,
        bottomPaddingFraction = s.position.bottomFraction,
    )

    /** Options LibVLC (`--freetype-*`), à la création du moteur. Taille : relative à la hauteur de l'image (valeur plus grande = texte plus petit). */
    fun toVlcOptions(s: SubtitleStyle): List<String> = buildList {
        add("--freetype-rel-fontsize=${(1080f / s.size.px1080 / 1.5f).toInt().coerceIn(6, 40)}")
        add("--freetype-color=${s.color.rgb}")
        add("--freetype-opacity=255")
        add("--freetype-background-color=0")
        add("--freetype-background-opacity=${(s.background.alpha * 255).toInt()}")
        add("--freetype-outline-thickness=${when (s.outline) { SubOutline.NONE -> 0; SubOutline.THIN -> 1; SubOutline.THICK -> 3 }}")
        add("--freetype-outline-color=0")
        add("--sub-margin=${((s.position.bottomFraction * 1080).toInt() - 65).coerceAtLeast(0)}")
    }

    /** Décalage VLC en microsecondes. */
    fun vlcDelayUs(s: SubtitleStyle): Long = s.delayMs * 1000L
}

/** Choix auto de la piste préférée : premier code de la liste ordonnée qui correspond à une piste. */
object TrackLanguagePicker {
    /** Noms d'une langue ISO 639 usuels dans les étiquettes des pistes (« Français », « French », « fre », « fr »). */
    fun aliases(code: String): Set<String> {
        val c = code.lowercase().take(3)
        val loc = java.util.Locale.forLanguageTag(c)
        val iso3 = runCatching { loc.isO3Language.lowercase() }.getOrDefault("")
        return setOfNotNull(
            c, iso3.takeIf { it.isNotBlank() }, bibliographic[c],
            loc.getDisplayLanguage(java.util.Locale.ENGLISH).lowercase().takeIf { it.isNotBlank() && it != c },
            loc.getDisplayLanguage(loc).lowercase().takeIf { it.isNotBlank() && it != c },
        )
    }
    private val bibliographic = mapOf("fr" to "fre", "de" to "ger", "es" to "spa", "ar" to "ara", "it" to "ita", "pt" to "por", "en" to "eng")

    /** Renvoie l'id de la piste dont l'étiquette désigne la meilleure langue préférée, ou null. [labels] : id -> étiquette. */
    fun pick(labels: List<Pair<String, String>>, preferred: List<String>): String? {
        for (code in preferred) {
            val names = aliases(code)
            labels.firstOrNull { (_, label) -> matches(label, names) }?.let { return it.first }
        }
        return null
    }

    private fun matches(label: String, names: Set<String>): Boolean {
        val words = label.lowercase().split(Regex("[^\\p{L}0-9]+")).filter { it.isNotEmpty() }
        return names.any { n -> n in words || (n.length > 3 && label.lowercase().contains(n)) }
    }
}
