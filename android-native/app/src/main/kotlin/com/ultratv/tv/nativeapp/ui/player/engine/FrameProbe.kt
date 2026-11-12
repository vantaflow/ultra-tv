package com.ultratv.tv.nativeapp.ui.player.engine

import android.graphics.Bitmap
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup

/**
 * Regarde réellement l'image affichée. Certains décodeurs matériels (HEVC 10 bits sur Exynos, par exemple) signalent
 * « première image rendue » mais ne produisent que du noir : le son passe, l'écran reste noir et rien ne se déclenche.
 * On copie une vignette de la surface vidéo (getBitmap d'une TextureView ; une SurfaceView n'est pas vérifiable, voir [sample]).
 */
object FrameProbe {
    private const val W = 32
    private const val H = 18

    /** Plus forte luminance (0..255) d'une liste de pixels ARGB. */
    fun maxLuma(px: IntArray): Int = px.maxOfOrNull { p ->
        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
        (r * 299 + g * 587 + b * 114) / 1000
    } ?: 0

    /** Image entièrement noire (aucun pixel au-dessus du seuil). */
    fun isBlack(px: IntArray, threshold: Int = 20): Boolean = maxLuma(px) < threshold

    /** Vue de rendu vidéo du moteur : la première TextureView, sinon la première SurfaceView (VLC ajoute la vidéo avant les sous-titres). */
    private fun videoView(root: View): View? {
        if (root is TextureView || root is SurfaceView) return root
        if (root !is ViewGroup) return null
        val all = ArrayList<View>()
        fun walk(v: View) {
            if (v is TextureView || v is SurfaceView) all += v
            else if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        return all.firstOrNull { it is TextureView } ?: all.firstOrNull()
    }

    /** Vignette de l'image vidéo (pixels ARGB), ou null si la copie n'est pas possible (surface pas prête, Android < 7). */
    suspend fun sample(root: View): IntArray? {
        val v = videoView(root) ?: return null
        if (v.width == 0 || v.height == 0) return null
        if (v is TextureView) {
            val bmp = runCatching { v.getBitmap(W, H) }.getOrNull() ?: return null
            return pixels(bmp)
        }
        // SurfaceView (TV, box, Fire TV) : la vidéo matérielle passe souvent par un plan d'affichage séparé (overlay
        // Amlogic / Fire TV, HDR, 4K) que PixelCopy ne voit pas : la copie revient NOIRE alors que l'image s'affiche.
        // Conclure « pas d'image » faisait basculer en décodage logiciel (flash au démarrage), puis en boucle entre
        // moteurs en 4K (le logiciel ne suit pas). Copie jugée non fiable : on ne conclut rien.
        return null
    }

    /** La vignette n'est fiable que sur une TextureView (rendue par le GPU de l'appli). Pur, testé. */
    fun reliable(isTextureView: Boolean): Boolean = isTextureView

    private fun pixels(bmp: Bitmap): IntArray {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        bmp.recycle()
        return px
    }
}
