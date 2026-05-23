package com.ultratv.tv.nativeapp.data.repo

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import java.util.concurrent.ConcurrentHashMap

/**
 * Looks up local channel-logo overrides from a user-picked SAF tree.
 *
 * The folder is expected to contain files named after the channel's
 * `tvg-id` / `epgChannelId` / channel name (any of these match). Supported
 * extensions: png / jpg / jpeg / webp / svg (SVG only renders via Coil's
 * coil-svg artifact — out of scope here, png/jpg cover 99 % of the cases).
 *
 * The map is rebuilt lazily the first time [resolve] is called after a
 * tree-URI change; subsequent lookups are O(1).
 */
object LocalLogos {

    /** Mirrors the localLogosFolderUri pref. UltraTvApp updates it from
     *  DataStore so [ChannelLogo] (which doesn't have prefs injected) can
     *  look up overrides cheaply. */
    @Volatile var treeUri: String = ""
        set(value) {
            if (value != field) {
                field = value
                invalidate()
            }
        }

    @Volatile private var loadedFor: String = ""
    private val index = ConcurrentHashMap<String, Uri>()

    /** Returns the override URI for ([epgChannelId], [name]) — composable-
     *  friendly entry point that doesn't require a ChannelEntity. */
    fun resolveByName(ctx: Context, epgChannelId: String?, name: String): Uri? {
        if (treeUri.isBlank()) return null
        ensureLoaded(ctx, treeUri)
        epgChannelId?.let { lookup(it) }?.let { return it }
        return lookup(name)
    }

    /** Returns the override URI for [channel] when the tree at [treeUri]
     *  contains a matching logo file, otherwise null. */
    fun resolve(ctx: Context, treeUri: String, channel: ChannelEntity): Uri? {
        if (treeUri.isBlank()) return null
        ensureLoaded(ctx, treeUri)
        // Try by xmltv id first (more stable across re-syncs), then by name.
        return channel.epgChannelId?.let { lookup(it) }
            ?: lookup(channel.name)
    }

    private fun lookup(key: String): Uri? {
        val norm = normalise(key)
        return index[norm]
    }

    private fun ensureLoaded(ctx: Context, treeUri: String) {
        if (loadedFor == treeUri) return
        synchronized(this) {
            if (loadedFor == treeUri) return
            index.clear()
            runCatching {
                val tree = DocumentFile.fromTreeUri(ctx, Uri.parse(treeUri)) ?: return@runCatching
                tree.listFiles().forEach { file ->
                    val name = file.name ?: return@forEach
                    if (!isImage(name)) return@forEach
                    val baseName = name.substringBeforeLast('.', name)
                    index[normalise(baseName)] = file.uri
                }
            }
            loadedFor = treeUri
        }
    }

    private fun isImage(name: String): Boolean = when (name.substringAfterLast('.', "").lowercase()) {
        "png", "jpg", "jpeg", "webp", "gif" -> true
        else -> false
    }

    /** Loose match: case-insensitive, strip non-alphanumerics so
     *  "TF1 HD" matches "tf1-hd.png" and "tf1_hd.png" alike. */
    private fun normalise(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }

    /** Clears the cache — call from prefs collector when the URI changes. */
    fun invalidate() {
        loadedFor = ""
        index.clear()
    }
}
