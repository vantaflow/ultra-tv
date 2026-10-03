package com.ultratv.tv.nativeapp.data.xtream

import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.net.HttpStatusException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.UnknownHostException

class XtreamStreamingTest {
    private val provider = ProviderEntity(
        id = 1, name = "fictif", kind = "XTREAM", baseUrl = "http://serveur-fictif.invalid",
        username = "test", password = "test",
    )

    private fun client(code: Int = 200, body: String = "[]", failure: Throwable? = null) =
        XtreamClient(
            OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                failure?.let { throw it }
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message("m").body(body.toResponseBody("application/json".toMediaType())).build()
            }).build(),
        )

    @Test fun flux_grosseListe_estLuParLots_sansTout_materialiser() = runBlocking {
        val sb = StringBuilder("[")
        repeat(60_000) { i ->
            if (i > 0) sb.append(',')
            sb.append("""{"stream_id":$i,"name":"Chaine $i","category_id":"${i % 50}","stream_icon":"","tv_archive":0}""")
        }
        sb.append("]")
        val seen = client(body = sb.toString()).withLiveStreams(provider) { seq ->
            var n = 0
            for (batch in seq.chunked(1_000)) n += batch.size
            n
        }
        assertEquals(60_000, seen)
    }

    @Test fun elementsSansIdentifiant_sontIgnores() = runBlocking {
        val r = client(body = """[{"name":"sans id"},{"stream_id":7,"name":"ok"}]""")
            .withLiveStreams(provider) { it.toList() }
        assertEquals(listOf("7"), r.map { it.remoteId })
        assertTrue(r.single().streamUrl.endsWith("/live/test/test/7.ts"))
    }

    @Test fun reponseObjetErreur_donneListeVide() = runBlocking {
        val r = client(body = """{"user_info":{"auth":0}}""").withLiveStreams(provider) { it.toList() }
        assertTrue(r.isEmpty())
    }

    @Test fun http401_remonteUneHttpStatusException() {
        try {
            runBlocking { client(code = 401).withLiveStreams(provider) { it.toList() } }
            fail("attendu : HttpStatusException")
        } catch (e: HttpStatusException) {
            assertEquals(401, e.code)
            assertTrue("pas d'identifiants dans le message", !(e.message ?: "").contains("test"))
        }
    }

    @Test fun hoteInexistant_remonteUnknownHost_etNeVideRien() {
        try {
            runBlocking { client(failure = UnknownHostException("serveur-fictif.invalid")).fetchLiveCategories(provider) }
            fail("attendu : UnknownHostException")
        } catch (e: UnknownHostException) { /* ok : l'erreur remonte au lieu d'être avalée */ }
    }
}
