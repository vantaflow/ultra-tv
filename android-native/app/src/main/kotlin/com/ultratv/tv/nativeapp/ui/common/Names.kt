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
    val parsed = com.ultratv.tv.nativeapp.data.repo.ChannelNameParser.parseCategory(raw).label
    // Symboles décoratifs (☼ ☀ ✦ …) puis séparateurs résiduels en bord de nom (« AFRICA / », « DSTV | »).
    val noSymbols = parsed.filterNot { Character.getType(it) == Character.OTHER_SYMBOL.toInt() || it in "☼☀★☆" }
    val trimmed = com.ultratv.tv.nativeapp.data.repo.TitleCleaner.stripDecorations(noSymbols).trim { it.isWhitespace() || it in "#=-*_<>|·•‧/\\:;,~" }
    // Le parseur retire les « + » de bord comme décoration : on rend celui qui est collé à un mot (« CANAL+ »).
    val core = raw.filterNot { Character.getType(it) == Character.OTHER_SYMBOL.toInt() || it in "☼☀★☆" }.trim { it.isWhitespace() || it in "#=-*_<>|·•‧/\\:;,~" }
    val keepPlus = core.length >= 2 && core.endsWith('+') && core[core.length - 2].isLetterOrDigit() && !trimmed.endsWith('+')
    return (if (keepPlus) "$trimmed+" else trimmed).ifBlank { raw }
}
