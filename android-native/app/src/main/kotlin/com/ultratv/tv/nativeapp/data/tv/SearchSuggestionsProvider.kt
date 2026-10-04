package com.ultratv.tv.nativeapp.data.tv

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.nav.DeepLink
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Suggestions de la recherche globale Android TV (« Hey Google, cherche … » / barre de recherche du lanceur).
 * Lecture seule ; chaque suggestion porte un lien profond `ultratv://…` résolu dans le catalogue local.
 */
class SearchSuggestionsProvider : ContentProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun catalog(): CatalogRepository
        fun providers(): ProviderRepository
    }

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        val q = (uri.lastPathSegment?.takeIf { it != SearchManager.SUGGEST_URI_PATH_QUERY } ?: selectionArgs?.firstOrNull()).orEmpty().trim()
        if (q.length < 2) return cursor
        val deps = EntryPointAccessors.fromApplication(context!!.applicationContext, Deps::class.java)
        runCatching {
            runBlocking {
                val ps = deps.providers().observeProviders().first()
                val pid = (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id ?: return@runBlocking
                val r = deps.catalog().search(pid, q, limit = 8)
                var id = 0L
                r.movies.forEach { m -> cursor.addRow(row(id++, m.title, "Film", m.poster, DeepLink.movie(m.providerId, m.remoteId))) }
                r.series.forEach { s -> cursor.addRow(row(id++, s.title, "Série", s.poster, DeepLink.series(s.providerId, s.remoteId))) }
                r.channels.forEach { c -> cursor.addRow(row(id++, c.title, "Direct", c.logo, DeepLink.live(c.providerId, c.remoteId))) }
            }
        }
        return cursor
    }

    private fun row(id: Long, title: String, subtitle: String, image: String?, link: String): Array<Any?> =
        arrayOf(id, title, subtitle, image, link, "video/*")

    override fun getType(uri: Uri): String? = SearchManager.SUGGEST_MIME_TYPE
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private companion object {
        val COLUMNS = arrayOf(
            BaseColumns._ID, SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE, SearchManager.SUGGEST_COLUMN_INTENT_DATA, SearchManager.SUGGEST_COLUMN_CONTENT_TYPE,
        )
    }
}
