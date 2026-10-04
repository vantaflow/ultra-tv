package com.ultratv.tv.nativeapp.data.prefs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderLimitsTest {
    private fun parse(s: String) = ProviderLimitsStore.parseMaxConnections(Json.parseToJsonElement(s) as JsonObject)

    @Test fun parseMaxConnections_chaine_lue() = assertEquals(3, parse("""{"user_info":{"max_connections":"3"}}"""))
    @Test fun parseMaxConnections_nombre_lu() = assertEquals(2, parse("""{"user_info":{"max_connections":2}}"""))
    @Test fun parseMaxConnections_absent_null() = assertNull(parse("""{"user_info":{"status":"Active"}}"""))
    @Test fun parseMaxConnections_zeroOuTexte_null() { assertNull(parse("""{"user_info":{"max_connections":"0"}}""")); assertNull(parse("""{"user_info":{"max_connections":"abc"}}""")) }
    @Test fun parseMaxConnections_sansUserInfo_null() = assertNull(parse("""{"server_info":{}}"""))
}
