package com.ultratv.tv.nativeapp.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFocusTest {
    private val right = android.view.KeyEvent.KEYCODE_DPAD_RIGHT
    private val left = android.view.KeyEvent.KEYCODE_DPAD_LEFT

    @Test fun premiereLigneSeulementEnArrivantDesRubriques() {
        assertTrue(SettingsFocus.enteringFromRubrics(right, recent = true, rtl = false))
        // Focus rendu après une validation (OK) : il reste sur la ligne validée.
        assertFalse(SettingsFocus.enteringFromRubrics(android.view.KeyEvent.KEYCODE_DPAD_CENTER, recent = true, rtl = false))
        assertFalse(SettingsFocus.enteringFromRubrics(right, recent = false, rtl = false))
    }

    /** La flèche vers le volet dépend du sens de lecture : ▶ en LTR, ◀ en arabe ; l'autre n'est jamais une entrée. */
    @Test fun regleDEntree_dependDuSensDeLecture() {
        assertFalse(SettingsFocus.enteringFromRubrics(left, recent = true, rtl = false))
        assertTrue(SettingsFocus.enteringFromRubrics(left, recent = true, rtl = true))
        assertFalse(SettingsFocus.enteringFromRubrics(right, recent = true, rtl = true))
        assertFalse(SettingsFocus.enteringFromRubrics(left, recent = false, rtl = true))
    }
}
