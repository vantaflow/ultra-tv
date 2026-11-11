package com.ultratv.tv.nativeapp.ui.player.engine

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingExtractor
import androidx.media3.extractor.ForwardingExtractorOutput
import androidx.media3.extractor.ForwardingExtractorsFactory
import androidx.media3.extractor.ForwardingTrackOutput
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory

/**
 * Sous-titres intégrés aux flux directs (MPEG-TS des fournisseurs IPTV).
 *
 * Dans un MPEG-TS, les sous-titres codés (CEA-608 / 708) ne sont pas une piste du conteneur : ils voyagent dans les
 * messages SEI de la vidéo H.264 et ne sont exposés par l'extracteur que si le lecteur les DÉCLARE. Les flux directs
 * n'annoncent presque jamais de descripteur de sous-titres dans la PMT : sans déclaration, ExoPlayer n'affichait aucune
 * piste, même quand la chaîne en diffuse. On déclare donc CC1 (CEA-608) seulement (une voie 708 déclarée reçoit les mêmes données que la 608 : elle serait proposée à tort) ; si la PMT contient
 * un descripteur de sous-titres, il l'emporte (c'est le comportement de l'extracteur, pas un forçage).
 *
 * [DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS] : repère les unités d'accès H.264 sans délimiteur explicite
 * (flux IPTV sans AUD), pour que les SEI de sous-titres soient rattachés à la bonne image.
 *
 * Déclarer une voie ne prouve pas que la chaîne l'émet : une chaîne sans sous-titres codés (TF1…) se retrouvait avec un
 * « CC1 » vide dans le panneau. Media3 (1.11.1) ne déclare PAS de voie CEA-608 par défaut (liste vide) et n'a aucune
 * API « cette voie est vide » : [CcProbe] observe donc les échantillons livrés par l'extracteur à chaque piste CEA, et
 * le panneau ne propose une piste CC qu'après en avoir reçu au moins un (des données de SEI présentes dans le flux).
 */
@UnstableApi
object LiveCaptions {
    const val TS_FLAGS: Int = DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS

    /** Voie déclarée (repli quand la PMT n'en annonce aucune). */
    val CLOSED_CAPTION_FORMATS: List<Format> = listOf(
        Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA608).setAccessibilityChannel(1).build(),
    )

    /** Fabrique d'extracteurs : celle de Media3, avec la déclaration ci-dessus pour le MPEG-TS (direct comme VOD). */
    fun extractorsFactory(probe: CcProbe = CcProbe()): ExtractorsFactory = ProbingFactory(
        DefaultExtractorsFactory().setTsExtractorFlags(TS_FLAGS).setTsSubtitleFormats(CLOSED_CAPTION_FORMATS), probe,
    )

    private fun isCc(mime: String?) = mime == MimeTypes.APPLICATION_CEA608 || mime == MimeTypes.APPLICATION_CEA708

    /** Clé d'une voie CC (type + voie / service). */
    fun key(mime: String?, channel: Int) = "$mime/$channel"

    /** Voies CC dont l'extracteur a réellement reçu des données (thread-safe : écrit par le fil de chargement). */
    class CcProbe {
        private val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        fun markSeen(mime: String?, channel: Int) { seen.add(key(mime, channel)) }
        fun clear() = seen.clear()
        fun hasData(mime: String?, channel: Int) = key(mime, channel) in seen
        /** Une piste qui n'est pas CC est toujours visible ; une piste CC seulement si des données sont arrivées. */
        fun isVisible(mime: String?, channel: Int) = !isCc(mime) || hasData(mime, channel)
    }

    private class ProbingFactory(private val inner: ExtractorsFactory, private val probe: CcProbe) : ForwardingExtractorsFactory(inner) {
        override fun createExtractors(): Array<Extractor> = wrap(inner.createExtractors())
        override fun createExtractors(uri: android.net.Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
            wrap(inner.createExtractors(uri, responseHeaders))
        // Les réglages de Media3 doivent s'appliquer à la fabrique interne ET renvoyer CETTE fabrique (sinon le wrapper est perdu).
        override fun experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled: Boolean): ExtractorsFactory { super.experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled); return this }
        override fun setSubtitleParserFactory(subtitleParserFactory: androidx.media3.extractor.text.SubtitleParser.Factory): ExtractorsFactory { super.setSubtitleParserFactory(subtitleParserFactory); return this }
        override fun experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies: Int): ExtractorsFactory { super.experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies); return this }
        override fun setParseHagcMetadata(parseHagcMetadata: Boolean): ExtractorsFactory { super.setParseHagcMetadata(parseHagcMetadata); return this }
        private fun wrap(list: Array<Extractor>): Array<Extractor> = Array(list.size) { ProbingExtractor(list[it], probe) }
    }

    private class ProbingExtractor(delegate: Extractor, private val probe: CcProbe) : ForwardingExtractor(delegate) {
        override fun init(output: ExtractorOutput) = super.init(object : ForwardingExtractorOutput(output) {
            override fun track(id: Int, type: Int): TrackOutput {
                val t = super.track(id, type)
                return if (type == androidx.media3.common.C.TRACK_TYPE_TEXT) object : ForwardingTrackOutput(t) {
                    var fmt: Format? = null
                    override fun format(format: Format) { fmt = format; super.format(format) }
                    override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
                        fmt?.takeIf { size > 0 && isCc(it.sampleMimeType) }?.let { probe.markSeen(it.sampleMimeType, it.accessibilityChannel) }
                        super.sampleMetadata(timeUs, flags, size, offset, cryptoData)
                    }
                } else t
            }
        })
    }

    /** Libellé d'une piste de sous-titres codés sans nom ni langue : « CC1 » (CEA-608) ou « CC 1 (708) ». */
    fun ccLabel(sampleMimeType: String?, accessibilityChannel: Int): String? = when (sampleMimeType) {
        MimeTypes.APPLICATION_CEA608 -> if (accessibilityChannel > 0) "CC$accessibilityChannel" else "CC"
        MimeTypes.APPLICATION_CEA708 -> if (accessibilityChannel > 0) "CC $accessibilityChannel (708)" else "CC (708)"
        else -> null
    }
}
