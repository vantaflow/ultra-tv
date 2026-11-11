package com.ultratv.tv.nativeapp.ui.player.engine

import android.media.MediaDrm
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.ultratv.tv.nativeapp.data.m3u.ClearKeyPair
import com.ultratv.tv.nativeapp.data.m3u.DrmScheme
import com.ultratv.tv.nativeapp.data.m3u.StreamProps
import java.util.Base64
import java.util.UUID

/**
 * Clés ClearKey locales (pas de serveur de licence) : réponse de licence JSON déjà construite, portée par l'étiquette
 * du MediaItem. Lue par le fournisseur de sessions DRM d'[ExoEngine] (`LocalMediaDrmCallback`). SECRET : jamais journalisé.
 */
class LocalClearKey(val licenseJson: ByteArray) {
    override fun toString() = "LocalClearKey(<masqué>)"
}

/**
 * Configuration Media3 des flux protégés (pure : testable sans lecteur). Widevine : `MediaItem.DrmConfiguration` avec
 * l'URL de licence et ses en-têtes. ClearKey avec URL : même chose, UUID ClearKey. ClearKey avec clés locales : pas
 * d'URL, la réponse de licence est construite ici ([clearKeyLicenseJson]).
 */
object DrmMedia {
    /** Réponse de licence ClearKey : `{"keys":[{"kty":"oct","k":"<base64url>","kid":"<base64url>"}],"type":"temporary"}`. */
    fun clearKeyLicenseJson(keys: List<ClearKeyPair>): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val items = keys.joinToString(",") {
            """{"kty":"oct","k":"${enc.encodeToString(hex(it.key))}","kid":"${enc.encodeToString(hex(it.kid))}"}"""
        }
        return """{"keys":[$items],"type":"temporary"}"""
    }

    private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    fun uuid(scheme: DrmScheme): UUID? = when (scheme) {
        DrmScheme.WIDEVINE -> C.WIDEVINE_UUID
        DrmScheme.CLEARKEY -> C.CLEARKEY_UUID
        DrmScheme.UNSUPPORTED -> null
    }

    /** Type de manifeste : `manifest_type` de la playlist, sinon l'extension de l'URL ; null = laisser Media3 deviner. */
    fun mimeType(props: StreamProps, url: String): String? = when (props.manifestType) {
        "mpd" -> MimeTypes.APPLICATION_MPD
        "hls" -> MimeTypes.APPLICATION_M3U8
        else -> {
            val path = url.substringBefore('#').substringBefore('?').lowercase()
            when {
                path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
                path.endsWith(".m3u8") || path.endsWith(".m3u") -> MimeTypes.APPLICATION_M3U8
                else -> null
            }
        }
    }

    /** En-têtes des requêtes de flux (manifeste, segments) : user-agent et referrer de la playlist. */
    fun streamHeaders(props: StreamProps): Map<String, String> = buildMap {
        props.userAgent?.let { put("User-Agent", it) }
        props.referrer?.let { put("Referer", it) }
    }

    /**
     * Vérifie, AVANT d'ouvrir un lecteur, que la configuration est complète et que l'appareil connaît le système.
     * null = on peut lire ; sinon la classe d'erreur à afficher (jamais de repli vers un autre moteur).
     */
    fun precheck(props: StreamProps, deviceSupports: (UUID) -> Boolean = ::deviceSupports): PlayErrorKind? {
        val scheme = props.scheme ?: return null
        val uuid = uuid(scheme) ?: return PlayErrorKind.DRM_UNSUPPORTED
        when (scheme) {
            DrmScheme.WIDEVINE -> if (props.licenseUrl.isNullOrBlank()) return PlayErrorKind.DRM_INVALID
            DrmScheme.CLEARKEY -> if (props.clearKeys.isEmpty() && props.licenseUrl.isNullOrBlank()) return PlayErrorKind.DRM_INVALID
            DrmScheme.UNSUPPORTED -> return PlayErrorKind.DRM_UNSUPPORTED
        }
        return if (deviceSupports(uuid)) null else PlayErrorKind.DRM_UNSUPPORTED
    }

    fun deviceSupports(uuid: UUID): Boolean = runCatching { MediaDrm.isCryptoSchemeSupported(uuid) }.getOrDefault(false)

    /** MediaItem d'un flux protégé. [liveOffsetMs] non nul = direct. */
    fun mediaItem(url: String, props: StreamProps, liveOffsetMs: Long?): MediaItem {
        val scheme = requireNotNull(props.scheme) { "flux non protégé" }
        val uuid = requireNotNull(uuid(scheme)) { "système non pris en charge" }
        val b = MediaItem.Builder().setUri(url)
        mimeType(props, url)?.let { b.setMimeType(it) }
        val drm = MediaItem.DrmConfiguration.Builder(uuid)
        val local = scheme == DrmScheme.CLEARKEY && props.licenseUrl.isNullOrBlank() && props.clearKeys.isNotEmpty()
        if (!props.licenseUrl.isNullOrBlank()) {
            drm.setLicenseUri(Uri.parse(props.licenseUrl))
            if (props.licenseHeaders.isNotEmpty()) drm.setLicenseRequestHeaders(props.licenseHeaders)
        }
        b.setDrmConfiguration(drm.build())
        if (local) b.setTag(LocalClearKey(clearKeyLicenseJson(props.clearKeys).toByteArray(Charsets.UTF_8)))
        if (liveOffsetMs != null) b.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(liveOffsetMs).build())
        return b.build()
    }
}
