package com.ultratv.tv.nativeapp.data.parental

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.parentalDs by preferencesDataStore(name = "parental")
private val PIN_HASH = stringPreferencesKey("pin_hash")

/**
 * Parental PIN store. We never persist the PIN itself — only a SHA-256 hash.
 *
 * - [pinHash] is a Flow that emits null when no PIN is set.
 * - [setPin] stores the hash; pass blank string to clear.
 * - [check] validates a typed PIN against the stored hash without exposing
 *   the hash to the UI.
 */
@Singleton
class ParentalStore @Inject constructor(@ApplicationContext private val ctx: Context) {

    val pinHash: Flow<String?> = ctx.parentalDs.data.map { it[PIN_HASH] }

    suspend fun setPin(pin: String) {
        ctx.parentalDs.edit { prefs ->
            if (pin.isBlank()) prefs.remove(PIN_HASH)
            else prefs[PIN_HASH] = sha256(pin)
        }
    }

    suspend fun isSet(): Boolean = current() != null

    /**
     * Validate a typed PIN. Rate-limited: after 3 wrong attempts in a row we
     * sleep an increasing amount (1s → 4s → 16s) before answering, defeating
     * the trivial 10 000-combo brute force on a 4-digit PIN. State is in
     * memory only — the lockout resets when the process restarts, which is
     * fine because that requires physical access too.
     */
    suspend fun check(pin: String): Boolean {
        val stored = current() ?: return true
        // Throttle BEFORE the comparison so a flood of wrong PINs can't even
        // keep the hash() loop busy.
        if (failedAttempts >= 3) {
            val penaltyMs = 1_000L shl ((failedAttempts - 3).coerceAtMost(4) * 2)
            kotlinx.coroutines.delay(penaltyMs)
        }
        val ok = stored == sha256(pin)
        if (ok) failedAttempts = 0
        else failedAttempts++
        return ok
    }

    @Volatile private var failedAttempts: Int = 0

    private suspend fun current(): String? = ctx.parentalDs.data.first()[PIN_HASH]

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
