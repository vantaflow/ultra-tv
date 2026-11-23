package com.ultratv.tv.nativeapp.releasecheck

import com.ultratv.tv.nativeapp.update.UpdateNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * En-tête « Which file should I download? / Quel fichier télécharger ? » ajouté en tête des notes de chaque release
 * GitHub par release.yml. Il doit nommer les bons fichiers et ne pas perturber le résumé des nouveautés que
 * l'invite de mise à jour tire de ces mêmes notes (UpdateNotes).
 */
class ReleaseNotesHeaderTest {
    private val header = File("../../.github/release-notes-header.md")
    private val workflow = File("../../.github/workflows/release.yml")
    private val script = File("../../.github/release-notes.sh")
    private fun rendered(v: String) = header.readText().replace("{{VERSION}}", v)

    @Test
    fun en_tete_anglais_puis_francais_avec_les_bons_fichiers() {
        val t = rendered("1.3.80")
        val en = t.indexOf("Which file should I download?")
        val fr = t.indexOf("Quel fichier télécharger ?")
        assertTrue("titre anglais présent", en >= 0)
        assertTrue("titre français présent, après l'anglais", fr > en)
        listOf(
            "UltraTV-1.3.80-arm64-v8a.apk", "UltraTV-1.3.80-armeabi-v7a.apk", "UltraTV-1.3.80-armeabi-v7a-android7.apk",
            "Chromecast", "Fire OS 6", ".dm", ".sha256", "SHA256SUMS.txt", "UltraTV-debug.apk",
        ).forEach { assertTrue("« $it » mentionné", t.contains(it)) }
        assertTrue("aucun {{VERSION}} restant", !t.contains("{{"))
    }

    @Test
    fun en_tete_sans_effet_sur_le_resume_de_mise_a_jour() {
        val changelog = """
            ### Nouveau
            - Lecteur : bouton « Passer l'intro ».

            ### Corrigé
            - Fenêtre trop petite.
        """.trimIndent()
        val avec = rendered("1.3.80") + "\n" + changelog
        assertEquals(UpdateNotes.summary(changelog), UpdateNotes.summary(avec))
        assertEquals(2, UpdateNotes.summary(avec).size)
    }

    @Test
    fun resume_anglais_sans_effet_sur_le_resume_de_mise_a_jour() {
        // Même structure que la sortie de release-notes.sh : en-tête, résumé anglais (titre de niveau 2), notes en français.
        val francais = """
            ### Ajouté
            - Lecteur : bouton « Passer l'intro ».

            ### Corrigé
            - Fenêtre trop petite.
        """.trimIndent()
        val notes = rendered("1.3.80") + "\n## Changes in 1.3.80 (English summary)\n\n" +
            "- Player: new \"Skip intro\" button, fixed window size.\n\n## Notes de version 1.3.80 (français)\n\n" + francais
        assertEquals(UpdateNotes.summary(francais), UpdateNotes.summary(notes))
        val s = script.readText()
        assertTrue("le script écrit ce titre anglais", s.contains("## Changes in \$FV (English summary)"))
        assertTrue("le script écrit ce titre français", s.contains("## Notes de version \$FV (français)"))
    }

    @Test
    fun workflow_place_l_en_tete_avant_le_changelog() {
        val wf = workflow.readText()
        assertTrue("release.yml appelle le script des notes", wf.contains(".github/release-notes.sh"))
        val s = script.readText()
        assertTrue("le script lit l'en-tête", s.contains(".github/release-notes-header.md"))
        assertTrue("le script remplace {{VERSION}}", s.contains("{{VERSION}}"))
        assertTrue("les notes sont toujours tirées du CHANGELOG", s.contains("CHANGELOG.md"))
        assertTrue("le résumé anglais vient de « ### In English »", s.contains("### In English"))
    }
}
