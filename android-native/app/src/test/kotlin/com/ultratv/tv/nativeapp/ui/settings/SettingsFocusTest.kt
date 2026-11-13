package com.ultratv.tv.nativeapp.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFocusTest {
    @Test fun premiereLigneSeulementEnArrivantDesRubriques() {
        assertTrue(SettingsFocus.enteringFromRubrics(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, recent = true))
        assertTrue(SettingsFocus.enteringFromRubrics(android.view.KeyEvent.KEYCODE_DPAD_LEFT, recent = true))
        // Focus rendu après une validation (OK) : il reste sur la ligne validée.
        assertFalse(SettingsFocus.enteringFromRubrics(android.view.KeyEvent.KEYCODE_DPAD_CENTER, recent = true))
        assertFalse(SettingsFocus.enteringFromRubrics(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, recent = false))
    }
}
