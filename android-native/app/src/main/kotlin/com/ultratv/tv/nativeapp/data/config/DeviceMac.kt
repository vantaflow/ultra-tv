package com.ultratv.tv.nativeapp.data.config

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable, device-unique pseudo-MAC derived from Settings.Secure.ANDROID_ID.
 *
 * Real WiFi MAC is hidden on Android 6+ for privacy. ANDROID_ID is a stable
 * 64-bit hex per (device, app signing key) pair — same value across reinstalls
 * of this app, different per box. We hash it and take the first 6 bytes to get
 * a MAC-format identifier that's:
 *
 *   - stable across reinstalls / updates
 *   - unique per TV box
 *   - private (admin must enter it on the dashboard to provision)
 *
 * No PII or hardware MAC is ever sent off-device.
 */
@Singleton
class DeviceMac @Inject constructor(@ApplicationContext private val ctx: Context) {
    val mac: String by lazy { compute() }

    private fun compute(): String {
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        // Include the app's signing-key namespace so the same physical device
        // hashed by different apps yields different MACs — mirrors how Android
        // already scopes ANDROID_ID.
        val seed = "ultratv-native:$androidId"
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        // Take 6 bytes → format as colon-separated MAC. Force locally-administered
        // bit (set bit 1 of first byte) so it doesn't collide with any real OUI.
        val b0 = (digest[0].toInt() and 0xFC) or 0x02
        val out = StringBuilder()
        out.append("%02x".format(b0))
        for (i in 1..5) {
            out.append(':').append("%02x".format(digest[i].toInt() and 0xFF))
        }
        return out.toString()
    }
}
