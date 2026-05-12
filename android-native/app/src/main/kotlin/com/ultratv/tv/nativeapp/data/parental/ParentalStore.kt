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
    suspend fun check(pin: String): Boolean = current()?.let { it == sha256(pin) } ?: true

    private suspend fun current(): String? = ctx.parentalDs.data.first()[PIN_HASH]

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
