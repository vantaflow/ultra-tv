package com.ultratv.tv.nativeapp.data.xtream

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortEpgTimeTest {
    private val H = 3_600_000L
    // 2026-10-04 21:35:00 UTC
    private val realStart = 1791149700000L

    private fun root(s: String) = Json.parseToJsonElement(s) as JsonObject

    @Test fun decalage_serveurParisEte_deuxHeures() =
        assertEquals(2 * H, ShortEpgTime.serverOffsetMs(root("""{"server_info":{"timestamp_now":1791149700,"time_now":"2026-10-04 23:35:00"}}""")))

    @Test fun decalage_serverInfoAbsent_null() = assertNull(ShortEpgTime.serverOffsetMs(root("""{"user_info":{}}""")))

    @Test fun horodatageCalculeSurHeureLocale_corrige() =
        assertEquals(realStart, ShortEpgTime.resolve(realStart + 2 * H, "2026-10-04 23:35:00", 2 * H))

    @Test fun horodatageCorrect_faitFoi() =
        assertEquals(realStart, ShortEpgTime.resolve(realStart, "2026-10-04 23:35:00", 2 * H))

    @Test fun sansHorodatage_heureLocaleCorrigee() =
        assertEquals(realStart, ShortEpgTime.resolve(null, "2026-10-04 23:35:00", 2 * H))

    @Test fun serveurUtc_inchange() =
        assertEquals(realStart, ShortEpgTime.resolve(realStart, "2026-10-04 21:35:00", 0))
}
