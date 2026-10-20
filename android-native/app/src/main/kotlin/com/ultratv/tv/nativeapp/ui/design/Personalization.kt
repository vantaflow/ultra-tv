package com.ultratv.tv.nativeapp.ui.design

/**
 * Personnalisation de l'affichage (Réglages › Affichage) : couleur d'accent, taille du texte, contraste.
 * Logique PURE (couleurs en ARGB `Long`, aucune dépendance Compose) : testée à part, puis publiée dans les jetons [Ux].
 */

/** Calculs de contraste WCAG 2.x (luminance relative, rapport de contraste, mélange de couleurs). */
object Wcag {
    /** Seuil AA pour un texte courant. */
    const val AA_TEXT = 4.5
    /** Seuil AA pour un composant d'interface non textuel (anneau de focus, bord d'une pastille). */
    const val AA_UI = 3.0

    private fun channel(argb: Long, shift: Int): Double {
        val c = ((argb shr shift) and 0xFF).toDouble() / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    /** Luminance relative (0 = noir, 1 = blanc) ; l'alpha est ignoré (couleurs opaques). */
    fun luminance(argb: Long): Double = 0.2126 * channel(argb, 16) + 0.7152 * channel(argb, 8) + 0.0722 * channel(argb, 0)

    /** Rapport de contraste (1 à 21), symétrique. */
    fun ratio(a: Long, b: Long): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Mélange linéaire (sRGB) de [a] vers [b] : t = 0 → a, t = 1 → b. Résultat opaque. */
    fun mix(a: Long, b: Long, t: Double): Long {
        fun ch(shift: Int): Long {
            val x = (a shr shift) and 0xFF; val y = (b shr shift) and 0xFF
            return Math.round(x + (y - x) * t.coerceIn(0.0, 1.0)).coerceIn(0, 255)
        }
        return 0xFF000000L or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * [fg] éclairci (fonds sombres) ou assombri (fonds clairs) juste assez pour atteindre [min] contre CHACUN des
     * [backgrounds] : la teinte reste reconnaissable, le texte reste lisible partout.
     */
    fun ensureContrast(fg: Long, backgrounds: LongArray, min: Double = AA_TEXT): Long {
        val darkBgs = backgrounds.all { luminance(it) < 0.5 }
        val target = if (darkBgs) WHITE else BLACK
        var t = 0.0
        while (t <= 1.0) {
            val c = mix(fg, target, t)
            if (backgrounds.all { ratio(c, it) >= min }) return c
            t += 0.02
        }
        return target
    }

    /** Couleur du texte posé sur [bg] : blanc ou encre, celle qui contraste le plus (toujours ≥ 4,5:1). */
    fun onColor(bg: Long): Long = if (ratio(WHITE, bg) >= ratio(INK, bg)) WHITE else INK

    const val WHITE = 0xFFFFFFFFL
    const val BLACK = 0xFF000000L
    /** Encre des maquettes (#0A0A0C), utilisée comme « noir » du texte sur fond clair. */
    const val INK = 0xFF0A0A0CL
}

/** Surfaces des thèmes (mêmes valeurs que [Ux]) : base des calculs de contraste et des tests. */
object UxSurfaces {
    /** Sombre : Bg, Rail, SurfaceDeep, Tone, Surface, Surface2. */
    val DARK = longArrayOf(0xFF0A0A0CL, 0xFF0F0F12L, 0xFF141418L, 0xFF1F1F25L, 0xFF1C1C21L, 0xFF26262DL)
    /** Clair : Bg, Rail, SurfaceDeep, Tone, Surface, Surface2. */
    val LIGHT = longArrayOf(0xFFF4F3EFL, 0xFFFFFFFFL, 0xFFFFFFFFL, 0xFFE3E1DBL, 0xFFECEAE5L, 0xFFD9D6CFL)
    /**
     * Fonds de PAGE / de ligne, derrière les éléments focalisables (anneau de focus, pastilles) : Bg, Rail, SurfaceDeep,
     * Surface. Surface2 est un fond d'élément (bouton, logo), pas un fond sur lequel l'anneau est dessiné.
     */
    val PAGE_DARK = longArrayOf(0xFF0A0A0CL, 0xFF0F0F12L, 0xFF141418L, 0xFF1C1C21L)
    val PAGE_LIGHT = longArrayOf(0xFFF4F3EFL, 0xFFFFFFFFL, 0xFFECEAE5L)
}

/**
 * Couleurs d'accent proposées. Chacune a une luminance moyenne (≈ 0,15–0,20) : l'anneau de focus garde au moins 3:1
 * sur toutes les surfaces, sombres comme claires ; le texte posé dessus ([onAccent]) est choisi automatiquement.
 */
enum class AccentChoice(val key: String, val argb: Long) {
    RED("red", 0xFFD91E2BL),
    BLUE("blue", 0xFF2563EBL),
    VIOLET("violet", 0xFF7C4DFFL),
    GREEN("green", 0xFF15803DL),
    ORANGE("orange", 0xFFD9480FL),
    PINK("pink", 0xFFDB2777L);

    /** Texte / icône sur un fond accent (blanc, ou encre pour l'orange). */
    val onAccent: Long = Wcag.onColor(argb)
    /** Accent utilisable comme TEXTE sur les surfaces sombres (AA sur toutes). */
    val inkOnDark: Long = Wcag.ensureContrast(argb, UxSurfaces.DARK)
    /** Accent utilisable comme TEXTE sur les surfaces claires (AA sur toutes). */
    val inkOnLight: Long = Wcag.ensureContrast(argb, UxSurfaces.LIGHT)

    companion object {
        val DEFAULT = RED
        fun parse(raw: String?): AccentChoice = entries.firstOrNull { it.key == raw?.lowercase() } ?: DEFAULT
    }
}

/** Taille du texte : facteur appliqué à toute l'interface (densité de police) et aux cadres qui contiennent du texte. */
enum class TextSize(val key: String, val scale: Float) {
    NORMAL("normal", 1f),
    LARGE("large", 1.15f),
    XLARGE("xlarge", 1.3f);

    companion object {
        fun parse(raw: String?): TextSize = entries.firstOrNull { it.key == raw?.lowercase() } ?: NORMAL
    }
}

/**
 * Pastilles de la liste Direct (langue, qualité) : fond plein, texte gras, contraste AA (texte/pastille ≥ 4,5:1)
 * ET pastille distincte de la ligne (≥ 3:1). Deux jeux : ligne sombre (repos en thème sombre, focus en thème clair)
 * et ligne claire (focus en thème sombre, repos en thème clair). Couleurs fixes, indépendantes de l'accent choisi.
 */
object BadgePalette {
    data class Chip(val bg: Long, val fg: Long)

    /** Qualité 4 = 4K, 3 = FHD, 2 = HD, 1 = SD ; null sinon. Teintes discrètes : ambre, blanc, bleu, gris. */
    fun quality(q: Int, darkRow: Boolean): Chip? = when (q) {
        4 -> if (darkRow) Chip(0xFFF2C14EL, 0xFF1A1300L) else Chip(0xFF7A4E00L, 0xFFFFFFFFL)
        3 -> if (darkRow) Chip(0xFFE4E4E7L, 0xFF0A0A0CL) else Chip(0xFF1C1C21L, 0xFFFFFFFFL)
        2 -> if (darkRow) Chip(0xFF9CC1FFL, 0xFF0A1A33L) else Chip(0xFF1E4FA8L, 0xFFFFFFFFL)
        1 -> if (darkRow) Chip(0xFF6B6B76L, 0xFFFFFFFFL) else Chip(0xFF4A4A53L, 0xFFFFFFFFL)
        else -> null
    }

    /** Langue (« FR », « AR ») et « MULTI » (légèrement plus marqué). */
    fun lang(darkRow: Boolean, multi: Boolean): Chip =
        if (darkRow) Chip(if (multi) 0xFFC9C9D1L else 0xFFB4B4BEL, 0xFF0A0A0CL)
        else Chip(if (multi) 0xFF2E2E36L else 0xFF45454EL, 0xFFFFFFFFL)
}
