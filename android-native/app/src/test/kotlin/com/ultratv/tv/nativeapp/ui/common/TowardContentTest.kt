package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Écrans à deux colonnes en arabe : OK sur une catégorie du Direct envoyait le focus à DROITE (vers le menu latéral, les
 * chaînes étant à gauche), et dans Films / Séries la flèche ◀ qui entre dans la grille ne choisissait pas la catégorie.
 */
class TowardContentTest {
    @Test fun versLeContenu_dependDuSensDeLecture() {
        assertEquals(FocusDirection.Right, towardContent(LayoutDirection.Ltr))
        assertEquals(FocusDirection.Left, towardContent(LayoutDirection.Rtl))
        assertEquals(Key.DirectionRight, towardContentKey(LayoutDirection.Ltr))
        assertEquals(Key.DirectionLeft, towardContentKey(LayoutDirection.Rtl))
    }
}
