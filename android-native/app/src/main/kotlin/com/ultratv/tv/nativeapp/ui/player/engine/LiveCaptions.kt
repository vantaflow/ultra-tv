package com.ultratv.tv.nativeapp.ui.player.engine

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory

/**
 * Sous-titres intégrés aux flux directs (MPEG-TS des fournisseurs IPTV).
 *
 * Dans un MPEG-TS, les sous-titres codés (CEA-608 / 708) ne sont pas une piste du conteneur : ils voyagent dans les
 * messages SEI de la vidéo H.264 et ne sont exposés par l'extracteur que si le lecteur les DÉCLARE. Les flux directs
 * n'annoncent presque jamais de descripteur de sous-titres dans la PMT : sans déclaration, ExoPlayer n'affichait aucune
 * piste, même quand la chaîne en diffuse. On déclare donc CC1 (CEA-608) et le service 1 (CEA-708) ; si la PMT contient
 * un descripteur de sous-titres, il l'emporte (c'est le comportement de l'extracteur, pas un forçage).
 *
 * [DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS] : repère les unités d'accès H.264 sans délimiteur explicite
 * (flux IPTV sans AUD), pour que les SEI de sous-titres soient rattachés à la bonne image.
 */
@UnstableApi
object LiveCaptions {
    const val TS_FLAGS: Int = DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS

    /** Voies déclarées (repli quand la PMT n'en annonce aucune). */
    val CLOSED_CAPTION_FORMATS: List<Format> = listOf(
        Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA608).setAccessibilityChannel(1).build(),
        Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA708).setAccessibilityChannel(1).build(),
    )

    /** Fabrique d'extracteurs : celle de Media3, avec la déclaration ci-dessus pour le MPEG-TS (direct comme VOD). */
    fun extractorsFactory(): ExtractorsFactory = DefaultExtractorsFactory()
        .setTsExtractorFlags(TS_FLAGS)
        .setTsSubtitleFormats(CLOSED_CAPTION_FORMATS)

    /** Libellé d'une piste de sous-titres codés sans nom ni langue : « CC1 » (CEA-608) ou « CC 1 (708) ». */
    fun ccLabel(sampleMimeType: String?, accessibilityChannel: Int): String? = when (sampleMimeType) {
        MimeTypes.APPLICATION_CEA608 -> if (accessibilityChannel > 0) "CC$accessibilityChannel" else "CC"
        MimeTypes.APPLICATION_CEA708 -> if (accessibilityChannel > 0) "CC $accessibilityChannel (708)" else "CC (708)"
        else -> null
    }
}
