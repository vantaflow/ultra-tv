package com.ultratv.tv.nativeapp.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncLogicTest {
    private fun cloud(id: String, name: String = "Src $id", url: String = "http://h/$id", user: String = "u", pass: String = "p", kind: String = "XTREAM", assign: List<String>? = null) =
        CloudProvider(id, kind, name, url, user, pass, "", assign, "TV salon", 0L)

    private fun local(localId: Long, cloudId: String?, name: String = "Src", url: String = "http://h/a", user: String = "u", pass: String = "p", kind: String = "XTREAM") =
        LocalProvider(localId, kind, name, url, user, pass, "", cloudId)

    private fun plan(local: List<LocalProvider>, cloud: List<CloudProvider>, deps: Map<Long, Int> = emptyMap(), applied: Map<String, String> = emptyMap()) =
        CloudSyncLogic.plan(local, cloud, deps, applied)

    // ── ajout ──
    @Test fun plan_m3uGetPhpDuCloud_ajouteeEnXtream() {
        val a = plan(emptyList(), listOf(cloud("aaaaaaaa", kind = "M3U", url = "http://iptv.example.test/get.php?username=demo&password=s3cret&type=m3u_plus&output=ts", user = "", pass = "")))
        val add = a.single() as SyncAction.Add
        assertEquals("XTREAM", add.cloud.kind); assertEquals("http://iptv.example.test", add.cloud.url)
        assertEquals("demo", add.cloud.username); assertEquals("s3cret", add.cloud.password)
    }

    @Test fun plan_m3uGetPhpDuCloud_localeDejaConvertie_aucuneReecriture() {
        val l = local(1, "aaaaaaaa", name = "STRONG", url = "http://iptv.example.test", user = "demo", pass = "s3cret")
        val c = cloud("aaaaaaaa", name = "STRONG", kind = "M3U", url = "http://iptv.example.test/get.php?username=demo&password=s3cret&type=m3u_plus&output=ts", user = "", pass = "")
        assertEquals(emptyList<SyncAction>(), plan(listOf(l), listOf(c), applied = mapOf("aaaaaaaa" to "STRONG")))
    }

    @Test fun plan_sourceCloudInconnue_estAjoutee() {
        val a = plan(emptyList(), listOf(cloud("aaaaaaaa")))
        assertEquals(1, a.size); assertTrue(a[0] is SyncAction.Add)
    }

    @Test fun plan_sourceCloudIdentiqueAUneLocaleNonLiee_estAdoptee_pasDupliquee() {
        val l = local(1, null, url = "http://h/a/")
        val a = plan(listOf(l), listOf(cloud("aaaaaaaa", url = "http://h/a")))
        assertEquals(listOf<SyncAction>(SyncAction.Link(1, cloud("aaaaaaaa", url = "http://h/a"))), a)
    }

    @Test fun plan_typeNonPris_enCharge_ignore() {
        assertTrue(plan(emptyList(), listOf(cloud("aaaaaaaa", kind = "STALKER"))).isEmpty())
    }

    // ── modification ──
    @Test fun plan_nouveauServeur_metAJour() {
        val a = plan(listOf(local(1, "aaaaaaaa", url = "http://old")), listOf(cloud("aaaaaaaa", url = "http://new")))
        val u = a.single() as SyncAction.Update
        assertEquals(1L, u.localId); assertEquals("http://new", u.cloud.url)
    }

    @Test fun plan_nouveauxIdentifiants_metAJour() {
        val a = plan(listOf(local(1, "aaaaaaaa", user = "u", pass = "old")), listOf(cloud("aaaaaaaa", url = "http://h/a", user = "u", pass = "new")))
        assertTrue(a.single() is SyncAction.Update)
    }

    @Test fun plan_inchange_neFaitRien() {
        assertTrue(plan(listOf(local(1, "aaaaaaaa", name = "Src aaaaaaaa", url = "http://h/aaaaaaaa")), listOf(cloud("aaaaaaaa"))).isEmpty())
    }

    @Test fun plan_conflit_leCloudGagnePourLesIdentifiants_memeSiLeLocalAChange() {
        // Le local a changé le mot de passe de son côté ET le cloud aussi : le cloud l'emporte.
        val a = plan(listOf(local(1, "aaaaaaaa", pass = "local-edit", url = "http://h/aaaaaaaa", name = "Src aaaaaaaa")), listOf(cloud("aaaaaaaa", pass = "cloud-edit")))
        assertEquals("cloud-edit", (a.single() as SyncAction.Update).cloud.password)
    }

    @Test fun plan_leLocalGardeSonNom_s_ilEstRenomme() {
        val l = local(1, "aaaaaaaa", name = "Mon salon", url = "http://old")
        val a = plan(listOf(l), listOf(cloud("aaaaaaaa", name = "Nom cloud", url = "http://new")), applied = mapOf("aaaaaaaa" to "Src aaaaaaaa"))
        assertFalse((a.single() as SyncAction.Update).renameLocal)
    }

    @Test fun plan_leNomSuitLeCloud_s_ilNAPasEteRenommeLocalement() {
        val l = local(1, "aaaaaaaa", name = "Src aaaaaaaa", url = "http://h/aaaaaaaa")
        val a = plan(listOf(l), listOf(cloud("aaaaaaaa", name = "Renommée")), applied = mapOf("aaaaaaaa" to "Src aaaaaaaa"))
        val u = a.single() as SyncAction.Update
        assertTrue(u.renameLocal); assertEquals("Renommée", u.cloud.name)
    }

    // ── suppression ──
    @Test fun plan_sourceLieeAbsenteDuCloud_estRetiree_sansConfirmationSiAucuneDonnee() {
        val r = plan(listOf(local(1, "aaaaaaaa")), emptyList()).single() as SyncAction.Remove
        assertEquals(1L, r.localId); assertFalse(r.needsConfirm)
    }

    @Test fun plan_retraitAvecFavorisOuEnregistrements_demandeConfirmation() {
        val r = plan(listOf(local(1, "aaaaaaaa")), emptyList(), deps = mapOf(1L to 3)).single() as SyncAction.Remove
        assertTrue(r.needsConfirm); assertEquals(3, r.dependents)
    }

    @Test fun plan_sourceLocalePrivee_n_estJamaisRetiree() {
        assertTrue(plan(listOf(local(1, null)), emptyList()).isEmpty())
    }

    // ── affectations ──
    @Test fun plan_sourceRetireeDeLAffectationDeCetAppareil_disparaitDuCloudRecu_donneRetrait() {
        // Le Worker ne renvoie plus la source à cet appareil : elle est absente de la réponse.
        val a = plan(listOf(local(1, "aaaaaaaa"), local(2, "bbbbbbbb", url = "http://h/bbbbbbbb", name = "Src bbbbbbbb")), listOf(cloud("bbbbbbbb")))
        assertEquals(listOf<SyncAction>(SyncAction.Remove(1, "Src", 0)), a)
    }

    @Test fun sharedWith_toutLeMonde_ouListe() {
        assertEquals(4, cloud("a", assign = null).sharedWith(4))
        assertEquals(2, cloud("a", assign = listOf("d1", "d2")).sharedWith(4))
    }

    @Test fun parseConfig_litAssignDevicesEtVersion() {
        val body = """{"version":7,"devices":[{"id":"d1","name":"TV","model":"Pixel","isCurrent":true},{"id":"d2","name":"","model":"Mac"}],
          "providers":[{"id":"aaaaaaaa","kind":"xtream","name":"A","url":"http://h","username":"u","password":"p","mac":"","sharedWith":["d1","d2"],"originName":"TV","updatedAt":5},
                       {"id":"bbbbbbbb","kind":"M3U","name":"B","url":"https://x/l.m3u","sharedWith":"all"},{"kind":"M3U","url":"x"}]}"""
        val c = CloudSyncLogic.parseConfig(body)
        assertEquals(7L, c.version); assertEquals("d1", c.selfId); assertEquals(2, c.devices.size)
        assertEquals(2, c.providers.size)                            // l'entrée sans id est ignorée
        assertEquals(listOf("d1", "d2"), c.providers[0].assign); assertEquals("XTREAM", c.providers[0].kind)
        assertNull(c.providers[1].assign)                            // "all"
        assertEquals("Mac", CloudSyncLogic.deviceDisplayName(c.devices[1]))
    }

    @Test fun parseConfig_sansChamps_nePlantePas() {
        val c = CloudSyncLogic.parseConfig("{}")
        assertTrue(c.providers.isEmpty()); assertTrue(c.devices.isEmpty()); assertNull(c.selfId)
    }

    // ── avertissement de connexions ──
    @Test fun connectionWarning_uneConnexionEtPlusieursAppareils() {
        assertTrue(CloudSyncLogic.connectionWarning(maxConnections = 1, sharedWith = 2))
        assertFalse(CloudSyncLogic.connectionWarning(maxConnections = 2, sharedWith = 3))
        assertFalse(CloudSyncLogic.connectionWarning(maxConnections = 1, sharedWith = 1))
    }

    // ── envoi ──
    @Test fun uploadBody_xtreamAvecIdentifiants_m3uSans_etPartage() {
        val x = JSONObjectOf(CloudSyncLogic.uploadBody(local(1, null, name = "X"), listOf("d1"), null))
        assertEquals("XTREAM", x.getString("kind")); assertEquals("u", x.getString("username")); assertFalse(x.has("id"))
        assertEquals("d1", x.getJSONArray("shareWith").getString(0))
        val m = JSONObjectOf(CloudSyncLogic.uploadBody(local(2, "aaaaaaaa", kind = "M3U"), null, "aaaaaaaa"))
        assertFalse(m.has("username")); assertEquals("aaaaaaaa", m.getString("id")); assertEquals("all", m.getString("shareWith"))
    }

    @Test fun identity_ignoreLaCasseEtLeSlashFinal() {
        assertEquals(CloudSyncLogic.identity("xtream", "HTTP://H/", "u"), CloudSyncLogic.identity("XTREAM", "http://h", "u"))
    }

    private fun JSONObjectOf(s: String) = org.json.JSONObject(s)
}
