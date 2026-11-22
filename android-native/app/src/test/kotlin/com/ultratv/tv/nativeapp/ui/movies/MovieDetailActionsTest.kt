package com.ultratv.tv.nativeapp.ui.movies

import org.junit.Assert.assertEquals
import org.junit.Test

/** Actions de la fiche film, sans Hilt : avertissement de téléchargement et choix du bouton principal à la télécommande. */
class MovieDetailActionsTest {
    @Test fun filmTv_lectureAppelleLAvertissement() {
        val calls = mutableListOf<String>()
        val onPlay = withDownloadWarning(warn = { calls += "warn" }, play = { u, t -> calls += "play:$u|$t" })
        onPlay("http://h/m.mkv", "Orbite basse")
        assertEquals(listOf("warn", "play:http://h/m.mkv|Orbite basse"), calls)
        onPlay("http://h/m.mkv", "Orbite basse")
        assertEquals("un avertissement par lecture", 2, calls.count { it == "warn" })
    }

    @Test fun filmTv_filmCommence_principalReprend_depuisLeDebutRepart() {
        val calls = mutableListOf<String>()
        val a = movieTvActions(resumeMs = 1_872_000L, play = { calls += "play" }, restart = { calls += "restart" })
        a.onPrimary()
        assertEquals(listOf("play"), calls)
        a.onFromStart()
        assertEquals(listOf("play", "restart"), calls)
    }

    @Test fun filmTv_filmJamaisCommence_principalLanceDepuisLeDebut() {
        val calls = mutableListOf<String>()
        val a = movieTvActions(resumeMs = 0L, play = { calls += "play" }, restart = { calls += "restart" })
        a.onPrimary()
        assertEquals(listOf("restart"), calls)
    }
}
