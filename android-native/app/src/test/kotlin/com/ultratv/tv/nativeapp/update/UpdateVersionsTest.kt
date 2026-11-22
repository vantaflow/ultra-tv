package com.ultratv.tv.nativeapp.update

import com.ultratv.tv.nativeapp.update.UpdateVersions.Release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Canal de mise à jour : ordre des versions et choix de la release (Stable / Pré-version). */
class UpdateVersionsTest {
    private fun v(t: String) = UpdateVersions.parse(t)!!

    @Test fun `ordre - pre-version sous la stable du meme numero`() {
        assertTrue(v("v1.3.72") < v("v1.3.73-rc1"))
        assertTrue(v("v1.3.73-rc1") < v("v1.3.73-rc2"))
        assertTrue(v("v1.3.73-rc2") < v("v1.3.73"))
        assertTrue(v("v1.3.73") < v("v1.3.74-rc1"))
        assertTrue(v("1.3.73-rc10") > v("1.3.73-rc9"))
        assertNull(UpdateVersions.parse("nightly"))
    }

    @Test fun `version installee - etiquette de publication seulement si elle correspond au numero`() {
        assertEquals(v("v1.3.73-rc2"), UpdateVersions.installed("1.3.73", "v1.3.73-rc2"))
        assertEquals(v("1.3.73"), UpdateVersions.installed("1.3.73", ""))
        assertEquals(v("1.3.73"), UpdateVersions.installed("1.3.73", "v1.3.70"))
    }

    private val releases = listOf(Release("v1.3.74-rc1", preRelease = true), Release("v1.3.73", preRelease = false), Release("v1.3.73-rc2", preRelease = true))

    @Test fun `canal stable - jamais de pre-version`() {
        assertEquals("v1.3.73", UpdateVersions.pick(releases, false, v("1.3.72"))?.tag)
        // Release « latest » mal étiquetée -rc : refusée sur le canal stable.
        assertNull(UpdateVersions.pick(listOf(Release("v1.3.74-rc1", preRelease = false)), false, v("1.3.73")))
    }

    @Test fun `canal pre-version - la plus recente des deux`() {
        assertEquals("v1.3.74-rc1", UpdateVersions.pick(releases, true, v("1.3.72"))?.tag)
        assertEquals("v1.3.74-rc1", UpdateVersions.pick(releases, true, v("1.3.73"))?.tag)
        assertNull(UpdateVersions.pick(releases, true, v("1.3.74-rc1")))
        assertNull(UpdateVersions.pick(listOf(Release("v1.3.75", preRelease = false, draft = true)), true, v("1.3.74")))
    }

    @Test fun `retour au stable depuis une pre-version - rien de plus ancien, la stable du meme numero est proposee`() {
        // rc1 de la 1.3.74 installée, latest = 1.3.73 : rien (pas de retour en arrière).
        assertNull(UpdateVersions.pick(listOf(Release("v1.3.73", false)), false, v("v1.3.74-rc1")))
        // rc2 de la 1.3.73 installée, la 1.3.73 stable sort : proposée.
        assertEquals("v1.3.73", UpdateVersions.pick(listOf(Release("v1.3.73", false)), false, v("v1.3.73-rc2"))?.tag)
        assertTrue(UpdateVersions.staysOnPreRelease(v("v1.3.74-rc1"), preReleaseChannel = false))
        assertFalse(UpdateVersions.staysOnPreRelease(v("v1.3.74-rc1"), preReleaseChannel = true))
        assertFalse(UpdateVersions.staysOnPreRelease(v("1.3.73"), preReleaseChannel = false))
    }
}
