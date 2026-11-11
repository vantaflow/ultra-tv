package com.ultratv.tv.nativeapp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNotesTest {
    private val notes = """
        ## [1.2.83] — 2026-11-11 (Android)

        ### Nouveau
        - **« Depuis le téléphone »** : la TV affiche un QR
          code et un code.
        - Lecteur : bouton **« Passer l'intro »**.

        ### Corrigé
        - Fenêtre trop petite.

        ### Technique
        - Mise à jour des dépendances.

        **Full Changelog**: https://github.com/x/y/compare/v1...v2
    """.trimIndent()

    @Test fun `garde les rubriques utilisateur, sans le markdown`() {
        assertEquals(
            listOf("« Depuis le téléphone » : la TV affiche un QR code et un code", "Lecteur : bouton « Passer l'intro »", "Fenêtre trop petite"),
            UpdateNotes.summary(notes),
        )
    }

    @Test fun `limite le nombre de points`() {
        assertEquals(2, UpdateNotes.summary(notes, max = 2).size)
    }

    @Test fun `notes generees par GitHub seules donnent un resume vide`() {
        assertTrue(UpdateNotes.summary("**Full Changelog**: https://github.com/x/y/compare/v1...v2").isEmpty())
    }

    @Test fun `point trop long coupe proprement`() {
        val long = "### Nouveau\n- " + "mot ".repeat(60)
        val s = UpdateNotes.summary(long).single()
        assertTrue(s.length <= 111 && s.endsWith("…"))
    }
}
