package com.ultratv.tv.nativeapp.ui.player.engine

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.ultratv.tv.nativeapp.data.sync.SyntheticLiveTs
import com.ultratv.tv.nativeapp.data.sync.SyntheticXtream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sous-titres intégrés aux flux directs. Le flux de test vient du FAUX serveur Xtream ([SyntheticXtream], route
 * `/live/.../<id>.ts`) et est lu par le VRAI extracteur MPEG-TS de Media3, obtenu par la fabrique du lecteur
 * ([LiveCaptions.extractorsFactory], celle qu'utilise ExoEngine) : ce test prouve que l'extracteur expose bien une piste
 * CEA-608 et lui livre les données de sous-titres. Il ne prouve PAS le décodage jusqu'à l'affichage (rendu, SubtitleView,
 * ExoPlayer complet : décodeurs matériels absents de la JVM) ni le comportement de LibVLC.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveCaptionsTest {
    private class Track(val id: Int, val type: Int) : TrackOutput {
        var format: Format? = null
        var samples = 0
        var bytes = 0
        override fun format(format: Format) { this.format = format }
        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
            val buf = ByteArray(length); val n = input.read(buf, 0, length); if (n > 0) bytes += n; return n
        }
        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) { data.skipBytes(length); bytes += length }
        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) { samples++ }
    }

    private class Output : ExtractorOutput {
        val tracks = LinkedHashMap<Int, Track>()
        override fun track(id: Int, type: Int): TrackOutput = tracks.getOrPut(id) { Track(id, type) }
        override fun endTracks() {}
        override fun seekMap(seekMap: SeekMap) {}
    }

    private fun extractFrom(factory: androidx.media3.extractor.ExtractorsFactory, data: ByteArray): Output {
        // Longueur inconnue, comme un flux direct : avec une longueur connue, l'extracteur TS cherche la durée en
        // demandant des repositionnements (RESULT_SEEK), que l'on gère quand même ci-dessous.
        fun open(at: Long): ExtractorInput {
            val ds = ByteArrayDataSource(data); ds.open(DataSpec.Builder().setUri(Uri.EMPTY).setPosition(at).build())
            return DefaultExtractorInput(ds, at, C.LENGTH_UNSET.toLong())
        }
        var input = open(0)
        val extractor: Extractor = factory.createExtractors().first { e -> (e.sniff(input)).also { input.resetPeekPosition() } }
        val out = Output(); extractor.init(out)
        val pos = PositionHolder()
        var guard = 0
        while (true) {
            check(++guard < 1_000_000) { "lecture du flux sans fin" }
            when (extractor.read(input, pos)) {
                Extractor.RESULT_END_OF_INPUT -> break
                Extractor.RESULT_SEEK -> input = open(pos.position)
            }
        }
        return out
    }

    /** Le flux direct est obtenu comme l'appli le fait : une requête HTTP passant par le faux serveur Xtream. */
    private fun liveStreamFromFakeXtream(): ByteArray {
        val server = SyntheticXtream(java.io.File(System.getProperty("java.io.tmpdir"), "utv-cc"), 1, 1, 1, 1, 1, 1)
        val client = OkHttpClient.Builder().addInterceptor(server.interceptor).build()
        client.newCall(Request.Builder().url("http://xtream.invalid/live/test/test/100001.ts").build()).execute().use { r ->
            assertEquals(200, r.code)
            assertEquals("video/mp2t", r.body.contentType()?.toString())
            assertTrue(server.requests.contains("live_ts"))
            return r.body.bytes()
        }
    }

    @Test fun configuration_declareLesSousTitresCodes_etDetecteLesUnitesDAcces() {
        assertTrue(LiveCaptions.TS_FLAGS and DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS != 0)
        val mimes = LiveCaptions.CLOSED_CAPTION_FORMATS.map { it.sampleMimeType }
        assertTrue(MimeTypes.APPLICATION_CEA608 in mimes)
        assertFalse(MimeTypes.APPLICATION_CEA708 in mimes)
        assertEquals("CC1", LiveCaptions.ccLabel(MimeTypes.APPLICATION_CEA608, 1))
        assertEquals(null, LiveCaptions.ccLabel(MimeTypes.APPLICATION_SUBRIP, 1))
    }

    @Test fun flux_direct_ts_expose_une_piste_cea608_qui_recoit_les_donnees() {
        val out = extractFrom(LiveCaptions.extractorsFactory(), liveStreamFromFakeXtream())
        val text = out.tracks.values.filter { it.type == C.TRACK_TYPE_TEXT }
        println("pistes de texte : " + text.map { "${it.format?.sampleMimeType}/${it.format?.language}/ch${it.format?.accessibilityChannel} samples=${it.samples}" })
        println("toutes les pistes : " + out.tracks.values.map { "${it.type}:${it.format?.sampleMimeType}" })
        val cea = text.firstOrNull { it.format?.sampleMimeType == MimeTypes.APPLICATION_CEA608 }
        assertNotNull("piste CEA-608 absente", cea)
        assertEquals(1, cea!!.format!!.accessibilityChannel)
        assertTrue("aucune donnée de sous-titres livrée à la piste CEA-608", cea.samples > 0)
        assertTrue(out.tracks.values.any { it.type == C.TRACK_TYPE_VIDEO })
        assertTrue(out.tracks.values.any { it.type == C.TRACK_TYPE_AUDIO })
    }

    @Test fun flux_avec_sei_la_piste_cc_est_proposee() {
        val probe = LiveCaptions.CcProbe()
        extractFrom(LiveCaptions.extractorsFactory(probe), SyntheticLiveTs.bytes(withCea608 = true))
        println("608 vu=" + probe.hasData(MimeTypes.APPLICATION_CEA608, 1) + " 708 vu=" + probe.hasData(MimeTypes.APPLICATION_CEA708, 1))
        assertTrue(probe.isVisible(MimeTypes.APPLICATION_CEA608, 1))
        // SEI en 608 seulement : la voie 708 ne doit pas être proposée à tort.
        assertFalse("708 proposée alors que le flux n'a que du 608", probe.isVisible(MimeTypes.APPLICATION_CEA708, 1))
    }

    @Test fun flux_sans_sei_aucune_piste_cc_n_est_proposee() {
        val probe = LiveCaptions.CcProbe()
        val out = extractFrom(LiveCaptions.extractorsFactory(probe), SyntheticLiveTs.bytes(withCea608 = false))
        // L'extracteur déclare bien les voies (c'est la déclaration), mais aucune donnée n'arrive : rien à proposer.
        assertTrue(out.tracks.values.any { it.format?.sampleMimeType == MimeTypes.APPLICATION_CEA608 })
        assertFalse(probe.isVisible(MimeTypes.APPLICATION_CEA608, 1))
        assertFalse(probe.isVisible(MimeTypes.APPLICATION_CEA708, 1))
        // Les autres pistes de texte (DVB, télétexte…) ne sont jamais masquées.
        assertTrue(probe.isVisible(MimeTypes.APPLICATION_SUBRIP, 0))
    }

    @Test fun sondage_remis_a_zero_au_changement_de_chaine() {
        val probe = LiveCaptions.CcProbe()
        probe.markSeen(MimeTypes.APPLICATION_CEA608, 1); assertTrue(probe.isVisible(MimeTypes.APPLICATION_CEA608, 1))
        probe.clear(); assertFalse(probe.isVisible(MimeTypes.APPLICATION_CEA608, 1))
    }
}
