package com.ultratv.tv.nativeapp.ui.screentime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ultratv.tv.nativeapp.data.profile.ProfileEntity
import com.ultratv.tv.nativeapp.data.screentime.ScreenTime
import com.ultratv.tv.nativeapp.data.screentime.ScreenTimeRules
import com.ultratv.tv.nativeapp.data.screentime.TimeWindow
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.design.ChoiceDialog

/** Étape de l'éditeur : menu, limite quotidienne, un jour, début puis fin d'une plage. */
private sealed interface Stage {
    data object Menu : Stage
    data object Limit : Stage
    data class Day(val d: Int) : Stage
    data class Start(val d: Int) : Stage
    data class End(val d: Int, val start: Int) : Stage
}

private const val STEP = 30

/**
 * Éditeur des horaires de visionnage et de la limite quotidienne d'un profil, en boîtes de choix successives (télécommande).
 * Le code parental a déjà été vérifié par l'appelant ; chaque changement est enregistré aussitôt ([onSave], texte codé).
 */
@Composable
fun ScreenTimeEditor(profile: ProfileEntity, initial: String?, pinSet: Boolean, onSave: (String) -> Unit, onClose: () -> Unit) {
    val lang = LocalDs.current.lang
    val S = ScreenTimeStrings(lang)
    var rules by remember { mutableStateOf(ScreenTime.decode(initial)) }
    var stage by remember { mutableStateOf<Stage>(Stage.Menu) }
    fun commit(r: ScreenTimeRules) { rules = r; onSave(ScreenTime.encode(r)); stage = Stage.Menu }
    fun setDay(d: Int, w: List<TimeWindow>?) = commit(rules.copy(days = if (w == null) rules.days - d else rules.days + (d to w)))

    when (val st = stage) {
        Stage.Menu -> {
            val opts = buildList {
                if (!pinSet) add("hint" to S.needCode)
                add("limit" to "${S.limit} : ${rules.dailyLimitMin?.let { S.minutes(it) } ?: S.limitNone}")
                for (d in 1..7) add("d$d" to "${ScreenTimeText.dayName(d, lang)} : ${ScreenTimeText.dayValue(rules, d, S)}")
                if (!rules.isEmpty) add("clear" to S.clear)
            }
            ChoiceDialog("${S.title} · ${profile.name}", opts, null, onPick = { k ->
                when {
                    k == "limit" -> stage = Stage.Limit
                    k == "clear" -> commit(ScreenTimeRules())
                    k.startsWith("d") -> stage = Stage.Day(k.drop(1).toInt())
                }
            }, onDismiss = onClose)
        }
        Stage.Limit -> ChoiceDialog(
            S.limit, listOf(0 to S.limitNone) + ScreenTime.LIMIT_CHOICES.map { it to S.minutes(it) }, rules.dailyLimitMin ?: 0,
            onPick = { commit(rules.copy(dailyLimitMin = it.takeIf { m -> m > 0 })) }, onDismiss = { stage = Stage.Menu },
        )
        is Stage.Day -> {
            val cur = rules.days[st.d]
            ChoiceDialog(
                ScreenTimeText.dayName(st.d, lang),
                listOf("free" to S.free, "blocked" to S.blockedDay, "hours" to S.pickHours),
                when { cur == null -> "free"; cur.isEmpty() -> "blocked"; else -> "hours" },
                onPick = { k -> when (k) { "free" -> setDay(st.d, null); "blocked" -> setDay(st.d, emptyList()); else -> stage = Stage.Start(st.d) } },
                onDismiss = { stage = Stage.Menu },
            )
        }
        is Stage.Start -> ChoiceDialog(
            "${ScreenTimeText.dayName(st.d, lang)} · ${S.start}", (0 until 1440 step STEP).map { it to ScreenTimeText.hhmm(it) }, rules.days[st.d]?.firstOrNull()?.startMin,
            onPick = { stage = Stage.End(st.d, it) }, onDismiss = { stage = Stage.Menu },
        )
        is Stage.End -> ChoiceDialog(
            "${ScreenTimeText.dayName(st.d, lang)} · ${S.end}", ((st.start + STEP)..1440 step STEP).map { it to ScreenTimeText.hhmm(it) }, rules.days[st.d]?.firstOrNull()?.endMin,
            onPick = { setDay(st.d, listOf(TimeWindow(st.start, it))) }, onDismiss = { stage = Stage.Menu },
        )
    }
}
