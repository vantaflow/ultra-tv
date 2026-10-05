package com.ultratv.tv.nativeapp.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ultratv.tv.nativeapp.data.subtitles.SubBackground
import com.ultratv.tv.nativeapp.data.subtitles.SubColor
import com.ultratv.tv.nativeapp.data.subtitles.SubOutline
import com.ultratv.tv.nativeapp.data.subtitles.SubPosition
import com.ultratv.tv.nativeapp.data.subtitles.SubSize
import com.ultratv.tv.nativeapp.data.subtitles.SubtitleStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.subtitleDs by preferencesDataStore(name = "subtitle_prefs")

/** Langues préférées : listes ORDONNÉES de codes ISO 639-1 (la première l'emporte). */
data class LanguagePrefs(val audio: List<String> = emptyList(), val text: List<String> = emptyList())

/** [autoOn] : sous-titres activés automatiquement seulement si l'utilisateur les a activés la dernière fois (défaut : non). */
data class SubtitleSettings(val style: SubtitleStyle = SubtitleStyle.DEFAULT, val languages: LanguagePrefs = LanguagePrefs(), val autoOn: Boolean = false)

/** Style des sous-titres et langues préférées (DataStore : pas de migration Room). */
@Singleton
class SubtitlePrefsStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private object K {
        val size = stringPreferencesKey("size"); val color = stringPreferencesKey("color"); val bg = stringPreferencesKey("bg")
        val outline = stringPreferencesKey("outline"); val pos = stringPreferencesKey("pos"); val delay = intPreferencesKey("delay")
        val audio = stringPreferencesKey("lang_audio"); val text = stringPreferencesKey("lang_text")
        val autoOn = androidx.datastore.preferences.core.booleanPreferencesKey("auto_on")
    }

    val flow: Flow<SubtitleSettings> = ctx.subtitleDs.data.map { p ->
        SubtitleSettings(
            SubtitleStyle(
                size = enumOr(p[K.size], SubSize.MEDIUM), color = enumOr(p[K.color], SubColor.WHITE), background = enumOr(p[K.bg], SubBackground.SEMI),
                outline = enumOr(p[K.outline], SubOutline.THIN), position = enumOr(p[K.pos], SubPosition.BOTTOM), delayMs = p[K.delay] ?: 0,
            ),
            LanguagePrefs(parseLangs(p[K.audio]), parseLangs(p[K.text])),
            autoOn = p[K.autoOn] ?: false,
        )
    }

    suspend fun saveStyle(s: SubtitleStyle) = ctx.subtitleDs.edit {
        it[K.size] = s.size.name; it[K.color] = s.color.name; it[K.bg] = s.background.name
        it[K.outline] = s.outline.name; it[K.pos] = s.position.name; it[K.delay] = s.delayMs
    }

    suspend fun saveAutoOn(on: Boolean) = ctx.subtitleDs.edit { it[K.autoOn] = on }

    suspend fun saveLanguages(l: LanguagePrefs) = ctx.subtitleDs.edit { it[K.audio] = l.audio.joinToString(","); it[K.text] = l.text.joinToString(",") }

    companion object {
        inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E = enumValues<E>().firstOrNull { it.name == name } ?: default
        fun parseLangs(raw: String?): List<String> = raw?.split(',')?.map { it.trim().lowercase() }?.filter { it.length in 2..3 }?.distinct().orEmpty()
    }
}
