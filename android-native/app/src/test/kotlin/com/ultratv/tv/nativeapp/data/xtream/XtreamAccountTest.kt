package com.ultratv.tv.nativeapp.data.xtream

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamAccountTest {
    private fun parse(s: String) = XtreamAccount.parse(Json.parseToJsonElement(s) as JsonObject)

    @Test fun champsEnChaine_convertis() {
        val a = parse("""{"user_info":{"status":"Active","exp_date":"1790000000","active_cons":"1","max_connections":"2","created_at":"1700000000","is_trial":"0"},"server_info":{"url":"srv.example"}}""")!!
        assertEquals("Active", a.status)
        assertEquals(1_790_000_000_000L, a.expiresAt)
        assertEquals(1, a.activeConnections)
        assertEquals(2, a.maxConnections)
        assertEquals(1_700_000_000_000L, a.createdAt)
        assertEquals(false, a.trial)
        assertEquals("srv.example", a.server)
    }

    @Test fun nombresEtNull_toleres() {
        val a = parse("""{"user_info":{"status":"Expired","exp_date":null,"active_cons":0,"max_connections":1,"is_trial":"1"}}""")!!
        assertNull(a.expiresAt)
        assertEquals(0, a.activeConnections)
        assertTrue(a.trial)
        assertNull(a.server)
    }

    @Test fun sansUserInfo_null() = assertNull(parse("""{"server_info":{}}"""))
}
