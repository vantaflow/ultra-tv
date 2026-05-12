package com.ultratv.tv.nativeapp.ui.common

/**
 * Display-only cleanup for IPTV category names. Many providers wrap section
 * titles in decorative chars like "### FRANCE ###" or "=== KIDS ===". We strip
 * those for the UI while keeping the raw value in the DB (some users actually
 * use the prefix to sort categories alphabetically, so we must not mutate
 * underlying data).
 */
fun prettyCategoryName(raw: String): String {
    if (raw.isBlank()) return raw
    // Trim leading/trailing decorative chars: # = - * _ < > | and whitespace.
    val trimmed = raw.trim { it.isWhitespace() || it in "#=-*_<>|·•‧" }
    return trimmed.ifBlank { raw }
}
