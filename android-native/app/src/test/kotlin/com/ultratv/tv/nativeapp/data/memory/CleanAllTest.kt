package com.ultratv.tv.nativeapp.data.memory

import org.junit.Assert.assertEquals
import org.junit.Test

/** Stockage › « Tout nettoyer » : place libérée additionnée, un module empêché n'empêche pas les autres. */
class CleanAllTest {
    @Test fun `somme des nettoyages faits`() {
        assertEquals(ActionResult.Done(1_500L), CleanAll.combine(listOf(ActionResult.Done(1_000L), ActionResult.Done(500L), ActionResult.Done(0L))))
    }

    @Test fun `synchro en cours ou pause du direct active - les autres modules sont nettoyes`() {
        assertEquals(ActionResult.Done(800L), CleanAll.combine(listOf(ActionResult.Done(800L), ActionResult.BlockedBySync, ActionResult.TimeshiftInUse)))
    }

    @Test fun `rien n'a pu se faire - premier empechement rendu`() {
        assertEquals(ActionResult.BlockedBySync, CleanAll.combine(listOf(ActionResult.BlockedBySync, ActionResult.TimeshiftInUse)))
        assertEquals(ActionResult.Failed, CleanAll.combine(emptyList()))
    }
}
