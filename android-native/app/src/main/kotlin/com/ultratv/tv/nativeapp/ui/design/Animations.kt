package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween as composeTween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.ultratv.tv.nativeapp.adaptive.UiEffects

/**
 * Niveau d'animation de l'interface (Réglages › Affichage › Animations) : « Automatique » suit le profil de l'appareil
 * (box modeste → réduites, très modeste → désactivées). Désactivées : pas de zoom au focus, pas de fondu, « À la une »
 * ne défile plus. Réduites : zoom et fondus divisés par deux.
 */
enum class AnimationLevel(val key: String) {
    AUTO("auto"), OFF("off"), SUBTLE("subtle"), NORMAL("normal");

    companion object {
        fun parse(raw: String?): AnimationLevel = entries.firstOrNull { it.key == raw?.lowercase() } ?: AUTO
    }
}

@Immutable
data class Animations(val level: AnimationLevel) {
    val off get() = level == AnimationLevel.OFF
    /**
     * « À la une » défile (toutes les 9 s) et se fond entre deux titres seulement au niveau complet : en « réduites » ou
     * « désactivées » le premier titre reste affiché (chaque rotation décodait un nouveau fond plein cadre et lançait un
     * fondu sur une box modeste). Quatre autres titres ne sont alors plus préparés en arrière-plan non plus.
     */
    val rotateHero get() = level == AnimationLevel.NORMAL

    /** Zoom d'un élément focalisé selon le niveau (1 = pas de zoom). */
    fun focusScale(full: Float): Float = when (level) {
        AnimationLevel.OFF -> 1f
        AnimationLevel.SUBTLE -> 1f + (full - 1f) / 2f
        else -> full
    }

    fun <T> tween(ms: Int): FiniteAnimationSpec<T> = when (level) {
        AnimationLevel.OFF -> snap()
        AnimationLevel.SUBTLE -> composeTween(ms / 2)
        else -> composeTween(ms)
    }

    companion object {
        val NORMAL = Animations(AnimationLevel.NORMAL)

        /** Niveau effectif : le choix de l'utilisateur, ou celui du profil adaptatif en automatique. */
        fun resolve(pref: AnimationLevel, auto: UiEffects): Animations = Animations(
            if (pref != AnimationLevel.AUTO) pref
            else when (auto) { UiEffects.FULL -> AnimationLevel.NORMAL; UiEffects.REDUCED -> AnimationLevel.SUBTLE; UiEffects.NONE -> AnimationLevel.OFF },
        )
    }
}

val LocalAnimations = staticCompositionLocalOf { Animations.NORMAL }
