package com.ultratv.tv.nativeapp.ui.design

import com.ultratv.tv.nativeapp.adaptive.UiEffects
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationsTest {
    @Test fun `le bandeau a la une ne defile qu'au niveau complet`() {
        assertTrue(Animations(AnimationLevel.NORMAL).rotateHero)
        assertFalse(Animations(AnimationLevel.SUBTLE).rotateHero)
        assertFalse(Animations(AnimationLevel.OFF).rotateHero)
    }

    @Test fun `automatique suit le profil de l'appareil`() {
        assertTrue(Animations.resolve(AnimationLevel.AUTO, UiEffects.FULL).rotateHero)
        assertFalse(Animations.resolve(AnimationLevel.AUTO, UiEffects.REDUCED).rotateHero)
        assertFalse(Animations.resolve(AnimationLevel.AUTO, UiEffects.NONE).rotateHero)
    }

    @Test fun `un choix manuel l'emporte sur le profil`() {
        assertTrue(Animations.resolve(AnimationLevel.NORMAL, UiEffects.NONE).rotateHero)
        assertFalse(Animations.resolve(AnimationLevel.OFF, UiEffects.FULL).rotateHero)
    }
}
