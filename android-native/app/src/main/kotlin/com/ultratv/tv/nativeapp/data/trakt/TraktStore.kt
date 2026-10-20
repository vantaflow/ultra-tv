package com.ultratv.tv.nativeapp.data.trakt

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Persistance disque de la bibliothèque Trakt et des rangées calculées (petits fichiers : lecture quasi instantanée). */
interface TraktStore {
    fun readLibrary(): String?
    fun writeLibrary(json: String)
    fun readRows(): String?
    fun writeRows(json: String)
    /** Oublie tout (appareil désappairé ou compte Trakt non lié). */
    fun clear()
    /** Réglages Trakt de l'appareil (marquer vu à la fin, listes masquées) : gardés même si le compte est délié. */
    fun readPrefs(): String? = null
    fun writePrefs(json: String) {}
    /** Reprises Trakt déjà importées dans l'historique (une pause n'est importée qu'une fois). */
    fun readImported(): String? = null
    fun writeImported(json: String) {}
}

/** Aucune persistance (tests, valeur par défaut). */
object NoTraktStore : TraktStore {
    override fun readLibrary(): String? = null
    override fun writeLibrary(json: String) {}
    override fun readRows(): String? = null
    override fun writeRows(json: String) {}
    override fun clear() {}
}

/** Fichiers dans le répertoire privé de l'appli ; écriture atomique (fichier temporaire puis renommage). */
@Singleton
class FileTraktStore(private val dir: File) : TraktStore {
    @Inject constructor(@ApplicationContext ctx: Context) : this(ctx.filesDir)

    private val lib get() = File(dir, "trakt-library.json")
    private val rows get() = File(dir, "trakt-rows.json")
    private val prefs get() = File(dir, "trakt-prefs.json")
    private val imported get() = File(dir, "trakt-imported.json")

    override fun readLibrary() = read(lib)
    override fun writeLibrary(json: String) = write(lib, json)
    override fun readRows() = read(rows)
    override fun writeRows(json: String) = write(rows, json)
    override fun readPrefs() = read(prefs)
    override fun writePrefs(json: String) = write(prefs, json)
    override fun readImported() = read(imported)
    override fun writeImported(json: String) = write(imported, json)
    override fun clear() { runCatching { lib.delete() }; runCatching { rows.delete() }; runCatching { imported.delete() } }

    private fun read(f: File): String? = try { if (f.isFile) f.readText().takeIf { it.isNotBlank() } else null } catch (_: Exception) { null }

    private fun write(f: File, text: String) {
        try {
            dir.mkdirs()
            val tmp = File(dir, f.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        } catch (_: Exception) { /* cache seulement : un échec d'écriture n'est jamais bloquant */ }
    }
}
