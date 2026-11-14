package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import com.ultratv.tv.nativeapp.i18n.locale
import com.ultratv.tv.nativeapp.i18n.channelLogos
import com.ultratv.tv.nativeapp.i18n.sportSectionHint
import com.ultratv.tv.nativeapp.i18n.sportSectionLabel
import com.ultratv.tv.nativeapp.i18n.kindStalker
import com.ultratv.tv.nativeapp.i18n.guideCheckMessage
import com.ultratv.tv.nativeapp.i18n.guideCustomLabel
import com.ultratv.tv.nativeapp.i18n.guideCustomOption
import com.ultratv.tv.nativeapp.i18n.guideRemoved
import com.ultratv.tv.nativeapp.i18n.guideSaved
import com.ultratv.tv.nativeapp.i18n.sourceGuide
import com.ultratv.tv.nativeapp.i18n.cardPhone
import com.ultratv.tv.nativeapp.i18n.remoteHint
import com.ultratv.tv.nativeapp.i18n.remoteTitle
import com.ultratv.tv.nativeapp.i18n.phoneReceived
import com.ultratv.tv.nativeapp.i18n.stalkerFailed
import com.ultratv.tv.nativeapp.ui.common.responsiveWidth
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import com.ultratv.tv.nativeapp.ui.mobile.openInBrowser
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.data.prefs.DefaultPlayer
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.dataSources
import com.ultratv.tv.nativeapp.i18n.tmdbAttribution
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.AppViewModel
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.ChoiceDialog
import com.ultratv.tv.nativeapp.ui.design.AccentChoice
import com.ultratv.tv.nativeapp.ui.design.TextSize
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.i18n.accentColor
import com.ultratv.tv.nativeapp.i18n.accentName
import com.ultratv.tv.nativeapp.i18n.homeRows
import com.ultratv.tv.nativeapp.i18n.homeRowsSummary
import com.ultratv.tv.nativeapp.i18n.personalization
import com.ultratv.tv.nativeapp.i18n.startScreenName
import com.ultratv.tv.nativeapp.i18n.textSizeLabel
import com.ultratv.tv.nativeapp.i18n.textSizeName
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.GroupLabel
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PaneTitle
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.PrefRow
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Switch
import com.ultratv.tv.nativeapp.ui.design.SwitchPrefRow
import com.ultratv.tv.nativeapp.ui.design.SettingsCard
import com.ultratv.tv.nativeapp.ui.design.settingsRowRing
import com.ultratv.tv.nativeapp.ui.design.settingsRowShape
import com.ultratv.tv.nativeapp.i18n.setAgo
import com.ultratv.tv.nativeapp.i18n.setDescAbout
import com.ultratv.tv.nativeapp.i18n.shortcutsTitle
import com.ultratv.tv.nativeapp.i18n.setDescDisplay
import com.ultratv.tv.nativeapp.i18n.setDescLanguages
import com.ultratv.tv.nativeapp.i18n.setDescMessages
import com.ultratv.tv.nativeapp.i18n.setDescParental
import com.ultratv.tv.nativeapp.i18n.setDescPlayback
import com.ultratv.tv.nativeapp.i18n.setSourcesCount
import com.ultratv.tv.nativeapp.i18n.setSourcesNone
import com.ultratv.tv.nativeapp.i18n.setSportSummary
import com.ultratv.tv.nativeapp.i18n.setSubParental
import com.ultratv.tv.nativeapp.i18n.setSubPlayback
import com.ultratv.tv.nativeapp.i18n.setVersionSummary
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import com.ultratv.tv.nativeapp.ui.profile.TechnicalGate

private enum class OpenDialog { NONE, ADD_CHOOSER, XTREAM, M3U_URL, STALKER, CLOUD, PHONE, REMOTE, WORKER, SOURCE_ACTIONS, SOURCE_GUIDE, WORKER_URL, SHARE, RENAME_DEVICE }
private enum class Rub(val icon: String) {
    SOURCES("M3 5h18v12H3zM8 21h8M12 17v4"), SYNC("M20 12a8 8 0 1 1-2.3-5.7M20 4v5h-5"), CATEGORIES("M4 6h16M4 12h16M4 18h10"),
    DISPLAY("M4 6h16M4 12h16M4 18h16"), PLAYBACK("M7 4v16l13-8z"), PARENTAL("M6 10V8a6 6 0 0 1 12 0v2M5 10h14v11H5z"),
    LANGUAGES("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3 12h18"), PROFILES("M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0"),
    MEMORY("M4 6c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3zM4 6v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"), MESSAGES("M4 5h16v11H8l-4 4z"), ABOUT("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v6M12 7h.01"),
}

/** Réglages (maquettes Reglages / Synchro / ReglagesAffichage / Diagnostic) : rubriques à gauche, volet à droite. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(onNavigate: (String) -> Unit = {}, vm: SettingsViewModel = hiltViewModel(), panes: SettingsPanesViewModel = hiltViewModel(), app: AppViewModel = hiltViewModel()) {
    val D = LocalDs.current
    var rub by rememberSaveable { mutableStateOf(0) }
    val dbg by com.ultratv.tv.nativeapp.StartupNav.debugRub.collectAsState()
    var showPane by rememberSaveable { mutableStateOf(false) }
    val pairRequested by com.ultratv.tv.nativeapp.StartupNav.startPairing.collectAsState()
    androidx.compose.runtime.LaunchedEffect(pairRequested) { if (pairRequested) { rub = Rub.SOURCES.ordinal; showPane = true } }
    val rubFocus = remember { List(Rub.entries.size) { androidx.compose.ui.focus.FocusRequester() } }
    val overlays = com.ultratv.tv.nativeapp.ui.common.LocalOverlayHost.current
    // Débogage (captures) : ouvre la rubrique demandée ET y place le focus (la rubrique suit le focus), une fois le focus initial posé.
    androidx.compose.runtime.LaunchedEffect(dbg) {
        dbg?.let { kotlinx.coroutines.delay(1500); val i = it.coerceIn(0, Rub.entries.lastIndex); rub = i; showPane = true; runCatching { rubFocus[i].requestFocus() }; com.ultratv.tv.nativeapp.StartupNav.debugRub.value = null }
    }
    val labels = listOf(D.rubSources, D.rubSync, D.rubCategories, D.rubDisplay, D.rubPlayback, D.rubParental, D.rubLanguages, com.ultratv.tv.nativeapp.ui.profile.ProfileStrings(D.lang).settingsTitle, com.ultratv.tv.nativeapp.ui.memory.MemoryStrings(D.lang).title, D.rubMessages, D.rubAbout)
    // Résumé d'état sous chaque rubrique de la liste (une ligne).
    val S = LocalStrings.current
    val providers by vm.providers.collectAsState()
    val pp by panes.state.collectAsState()
    val syncProvider by panes.provider.collectAsState()
    val catTotals by panes.categoryTotals.collectAsState()
    val kindNames = providers.mapNotNull { kindName(S.wiz, it.kind) ?: (if (it.kind == "STALKER") D.kindStalker else null) }.distinct()
    val lastSync = syncProvider?.let { maxOf(it.lastLiveSyncAt, it.lastVodSyncAt, it.lastSeriesSyncAt, it.lastEpgSyncAt) } ?: 0L
    val subs = listOf(
        if (providers.isEmpty()) D.setSourcesNone else D.setSourcesCount(providers.size) + (if (kindNames.isEmpty()) "" else " · " + kindNames.joinToString(", ")),
        if (lastSync <= 0L) D.neverSynced else D.setAgo(ago(D, System.currentTimeMillis() - lastSync)),
        D.activeDisabled.format(catTotals.enabled, catTotals.disabled),
        themeLabel(D, pp.theme) + " · " + D.setSportSummary(if (pp.sportSection) D.on else D.offState),
        D.setSubPlayback,
        D.setSubParental,
        D.langName(AppLang.fromCode(pp.language)),
        com.ultratv.tv.nativeapp.ui.profile.ProfileStrings(D.lang).settingsSub,
        com.ultratv.tv.nativeapp.ui.memory.MemoryStrings(D.lang).subtitle,
        D.setDescMessages,
        D.setVersionSummary(com.ultratv.tv.nativeapp.BuildConfig.VERSION_NAME),
    )
    val pane: @Composable () -> Unit = {
            when (Rub.entries[rub]) {
                Rub.SOURCES -> TechnicalGate(onDeny = { rub = Rub.DISPLAY.ordinal }) { SourcesPane(vm, panes) }
                Rub.SYNC -> TechnicalGate(onDeny = { rub = Rub.DISPLAY.ordinal }) { SyncPane(vm, panes, onNavigate) }
                Rub.CATEGORIES -> CategoriesPane(panes, onNavigate)
                Rub.DISPLAY -> DisplayPane(vm, panes, app)
                Rub.PLAYBACK -> TechnicalGate(onDeny = { rub = Rub.DISPLAY.ordinal }) { PlaybackPane(panes, app) }
                Rub.PARENTAL -> { PaneTitle(D.rubParental, D.setDescParental); com.ultratv.tv.nativeapp.ui.parental.ParentalSection(onManageLockedChannels = { onNavigate("locked-channels") }) }
                Rub.LANGUAGES -> LanguagesPane(panes, app)
                Rub.PROFILES -> com.ultratv.tv.nativeapp.ui.profile.ProfilesPane()
                Rub.MEMORY -> TechnicalGate(onDeny = { rub = Rub.DISPLAY.ordinal }) { com.ultratv.tv.nativeapp.ui.memory.MemoryPane() }
                Rub.MESSAGES -> com.ultratv.tv.nativeapp.ui.common.AnnouncementHistoryPane()
                Rub.ABOUT -> TechnicalGate(onDeny = { rub = Rub.DISPLAY.ordinal }) { AboutPane(vm, panes, app, onNavigate) }
            }
    }
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    val compact = touch && com.ultratv.tv.nativeapp.ui.common.LocalUiWidthDp.current < 600f
    if (compact) {
        // Téléphone : liste de rubriques, puis la rubrique choisie en plein écran (Retour système = retour à la liste).
        androidx.activity.compose.BackHandler(enabled = showPane) { showPane = false }
        if (!showPane) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(D.settingsTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.sp, maxLines = 1)
                com.ultratv.tv.nativeapp.ui.mobile.SearchAction()
            }
            Rub.entries.forEachIndexed { i, r ->
                FocusSurface(onClick = { rub = i; showPane = true }, shape = RoundedCornerShape(20.dp), bg = Ux.SurfaceDeep, modifier = Modifier.fillMaxWidth().height(68.dp)) { _ ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DIcon(r.icon, 22.dp, Ux.Text2)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(labels[i], color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(subs[i], color = Ux.Text3, fontFamily = Manrope, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DIcon("M9 6l6 6-6 6", 18.dp, Ux.Text3, strokeWidth = 2.5f)
                    }
                }
            }
        } else Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                com.ultratv.tv.nativeapp.ui.mobile.IconCircle(com.ultratv.tv.nativeapp.ui.mobile.MobileIcons.Back, com.ultratv.tv.nativeapp.ui.mobile.LocalMobileStrings.current.a11yBack, Ux.Surface, Ux.Text, { showPane = false })
                Text(labels[rub], color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { pane() }
        }
        return
    }
    SettingsTvLayout(
        title = D.settingsTitle, rubs = Rub.entries.mapIndexed { i, r -> SettingsRubUi(r.icon, labels[i], subs[i]) },
        selected = rub, onSelect = { rub = it }, rubFocus = rubFocus, modalOpen = overlays?.hasModal == true, pane = pane,
    )
}

/** Une rubrique de la liste de gauche : icône (chemin SVG), libellé et résumé d'état d'une ligne. */
internal class SettingsRubUi(val icon: String, val label: String, val sub: String)

/**
 * Disposition TV / tablette des Réglages (maquette Reglages) : colonne de gauche (fond « rail », grand titre, rubriques avec
 * résumé d'état ; la rubrique ouverte porte les couleurs de focus) et volet de droite ([pane]).
 * Sans ViewModel : le rendu de test l'alimente avec des données fictives.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun SettingsTvLayout(
    title: String,
    rubs: List<SettingsRubUi>,
    selected: Int,
    onSelect: (Int) -> Unit,
    rubFocus: List<androidx.compose.ui.focus.FocusRequester>,
    modalOpen: Boolean,
    pane: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        // ← depuis le volet : le focus revient sur la rubrique OUVERTE, pas sur celle qui se trouve en face de la ligne
        // focalisée (« À propos » depuis le bas d'Affichage) — et comme la rubrique suit le focus, le volet changeait.
        Column(
            Modifier.width(520.design).fillMaxHeight().background(Ux.Rail)
                .focusProperties { enter = { rubFocus[selected] } }.focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(start = 72.design, end = 32.design, top = 48.design, bottom = 32.design),
            verticalArrangement = Arrangement.spacedBy(6.design),
        ) {
            Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 48.spx, maxLines = 1, modifier = Modifier.padding(bottom = 14.design))
            rubs.forEachIndexed { i, r ->
                val sel = selected == i
                FocusSurface(
                    onClick = { onSelect(i) }, shape = RoundedCornerShape(20.design), bg = if (sel) Ux.Cta else Color.Transparent, ringWidth = 0.design, focusedScale = 1.0f,
                    modifier = Modifier.fillMaxWidth().height(76.designT).focusRequester(rubFocus[i]).onFocusChanged { if (it.isFocused && rubFollowsFocus(modalOpen)) onSelect(i) },
                ) { f ->
                    val on = f || sel
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.design), verticalAlignment = Alignment.CenterVertically) {
                        DIcon(r.icon, 28.design, if (on) Ux.TextOnLight else Ux.Text3)
                        Spacer(Modifier.width(18.design))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.design)) {
                            Text(r.label, color = if (on) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.sub, color = if (on) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 18.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        // → depuis une rubrique : le focus entre sur la PREMIÈRE ligne du volet (et le volet revient en haut), pas sur la
        // ligne en face de la rubrique (« Numéros de chaînes » depuis « Affichage », titre du volet sorti de l'écran).
        val firstSlot = remember(selected) { com.ultratv.tv.nativeapp.ui.common.FirstFocusSlot() }
        val paneScroll = rememberScrollState()
        val paneScope = rememberCoroutineScope()
        Column(
            Modifier.weight(1f).fillMaxHeight()
                // Seulement à l'arrivée par la flèche horizontale depuis les rubriques : un focus RENDU au volet (fenêtre de choix
                // fermée après une validation) revenait sur la première ligne, volet remonté en haut (retour Reddit).
                .focusProperties { enter = { if (SettingsFocus.enteringFromRubrics()) { paneScope.launch { paneScroll.scrollTo(0) }; firstSlot.target() } else androidx.compose.ui.focus.FocusRequester.Default } }.focusGroup()
                .verticalScroll(paneScroll).padding(start = 72.design, end = 88.design, top = 54.design, bottom = 54.design),
            verticalArrangement = Arrangement.spacedBy(24.design),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(com.ultratv.tv.nativeapp.ui.common.LocalFirstFocusSlot provides firstSlot) { pane() }
        }
    }
}

/**
 * La rubrique suit le focus quand l'utilisateur l'y a amené (flèches), ou hors de toute touche récente quand aucune modale
 * n'est ouverte (focus initial de l'écran).
 * Un focus arrivé juste après OK / Retour est un focus « rendu » par Compose (élément focalisé disparu : choix fait dans une
 * modale, modale fermée) : il ne doit pas changer de rubrique, sinon le volet (et la modale qu'il porte) disparaît.
 */
internal fun rubFollowsFocus(modalOpen: Boolean): Boolean =
    com.ultratv.tv.nativeapp.ui.common.InputClock.recentNavKey() || (!modalOpen && !com.ultratv.tv.nativeapp.ui.common.InputClock.recentKey())

/** « scheme://hôte » seulement : jamais le chemin (qui peut contenir des identifiants). */
internal fun hostOnly(baseUrl: String): String = runCatching { java.net.URI(baseUrl).let { "${it.scheme}://${it.host}" } }.getOrDefault("")

/** Types de source pris en charge ; un type hérité inconnu reste affichable et supprimable. */
internal val SUPPORTED_KINDS = setOf("XTREAM", "M3U", "M3U_LOCAL", "STALKER")

private const val CUSTOM_GUIDE_KEY = "__custom__"

private fun kindName(S: com.ultratv.tv.nativeapp.i18n.WizardStrings, kind: String) = when (kind) { "XTREAM" -> S.kindXtream; "M3U" -> S.kindM3u; "M3U_LOCAL" -> S.kindM3uFile; else -> null }

// ───────────────────────── Sources ─────────────────────────

@Composable
private fun SourcesPane(vm: SettingsViewModel, panes: SettingsPanesViewModel) {
    val D = LocalDs.current
    val S = LocalStrings.current
    val providers by vm.providers.collectAsState()
    val counts by panes.counts.collectAsState()
    val paired by vm.paired.collectAsState()
    val pairingUi by vm.pairing.collectAsState()
    val workerBase by vm.workerBaseUrl.collectAsState()
    // Accueil vide › « Depuis le cloud » : ouvre directement l'appairage.
    val cloudReq by com.ultratv.tv.nativeapp.StartupNav.cloudPairRequest.collectAsState()
    androidx.compose.runtime.LaunchedEffect(cloudReq) { if (cloudReq) { com.ultratv.tv.nativeapp.StartupNav.cloudPairRequest.value = false; vm.startPairing() } }
    val webSendUi by vm.webSendUi.collectAsState()
    val phoneReceived = D.phoneReceived
    val stalkerFailed = D.stalkerFailed
    // Accueil vide › « Depuis le téléphone » : ouvre directement la fenêtre d'envoi.
    val phoneReq by com.ultratv.tv.nativeapp.StartupNav.phoneSendRequest.collectAsState()
    androidx.compose.runtime.LaunchedEffect(phoneReq) { if (phoneReq) { com.ultratv.tv.nativeapp.StartupNav.phoneSendRequest.value = false; vm.startWebSend(phoneReceived, stalkerFailed) } }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf(OpenDialog.NONE) }
    var selected by remember { mutableStateOf<ProviderEntity?>(null) }
    val C = rememberCloudStrings()
    val cloud by vm.cloud.collectAsState()
    val offer by vm.offerShare.collectAsState()
    var connWarn by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(cloud, providers) { connWarn = vm.connectionWarning() }
    var backupPwd by remember { mutableStateOf("") }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = vm.consumeBackup()
        if (uri != null && text != null) scope.launch(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
                .onSuccess { com.ultratv.tv.nativeapp.ui.common.Toaster.ok(S.toastBackupSaved) }.onFailure { com.ultratv.tv.nativeapp.ui.common.Toaster.err(S.toastSaveFailed) }
        }
    }
    val loadBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val txt = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.toString(Charsets.UTF_8).orEmpty() }.getOrNull()
            if (txt.isNullOrBlank()) com.ultratv.tv.nativeapp.ui.common.Toaster.err(S.toastEmptyFile)
            else vm.restoreBackup(txt, S.toastRestoredTemplate, S.toastRestoreFailed, backupPwd.takeIf { it.isNotEmpty() })
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val (label, text) = withContext(Dispatchers.IO) {
                val display = runCatching { ctx.contentResolver.query(uri, null, null, null, null)?.use { c -> val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (i >= 0 && c.moveToFirst()) c.getString(i) else uri.lastPathSegment } }.getOrNull() ?: "Local"
                display to (ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.toString(Charsets.UTF_8).orEmpty())
            }
            vm.addM3uLocal("", label, text)
        }
    }

    PaneTitle(D.rubSources, D.sourcesSubtitle)
    Column(verticalArrangement = Arrangement.spacedBy(14.design)) {
        providers.forEach { p ->
            FocusSurface(onClick = { selected = p; dialog = OpenDialog.SOURCE_ACTIONS }, shape = RoundedCornerShape(26.design), bg = Ux.SurfaceDeep, ringWidth = 0.design, focusedScale = 1.0f, modifier = Modifier.fillMaxWidth().height(112.design)) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 32.design), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(60.design).clip(RoundedCornerShape(16.design)).background(if (p.active) Ux.Accent else Ux.Surface2), contentAlignment = Alignment.Center) {
                        DIcon(when (p.kind) { "XTREAM", "STALKER" -> Icons.Monitor; "M3U_LOCAL" -> Icons.File; else -> Icons.List }, 30.design, if (p.active) Ux.OnAccent else Ux.White)
                    }
                    Spacer(Modifier.width(24.design))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.design)) {
                        Text(p.name, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 28.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val cloudTag = when {
                            p.kind != "XTREAM" && p.kind != "M3U" && p.kind != "STALKER" -> null
                            vm.cloudIdOf(p.id) != null -> C.cloudShared(vm.sharedWith(p.id).coerceAtLeast(1))
                            paired -> C.localPrivate
                            else -> null
                        }
                        val meta = listOfNotNull(kindName(S.wiz, p.kind) ?: (if (p.kind == "STALKER") D.kindStalker else D.unsupportedSource), cloudTag, if (p.active) D.active else null, if (p.active && counts.live > 0) D.channels(counts.live) else null).joinToString(" · ")
                        Text(meta, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(D.edit, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1)
                }
            }
        }
        // « Ajouter une source » : carte en pointillés de la maquette.
        FocusSurface(onClick = { dialog = OpenDialog.ADD_CHOOSER }, shape = RoundedCornerShape(22.design), bg = Color.Transparent, ringWidth = 5.design, focusedScale = 1.0f, modifier = Modifier.fillMaxWidth().height(96.design).border(2.design, Ux.Line, RoundedCornerShape(22.design))) { f ->
            Row(Modifier.fillMaxSize().padding(horizontal = 32.design), verticalAlignment = Alignment.CenterVertically) {
                DIcon("M12 5v14M5 12h14", 28.design, if (f) Ux.TextOnLight else Ux.Text2, strokeWidth = 2.5f)
                Spacer(Modifier.width(20.design))
                Text(D.addSource, color = if (f) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1)
            }
        }
    }
    GroupLabel(D.cloudSync)
    SettingsCard {
        if (paired) PrefRow(D.paired, D.unpair, hint = hostOnly(workerBase)) { vm.unpair() }
        else PrefRow(D.cloudSync, "", hint = D.cloudSyncHint) { vm.startPairing() }
        if (paired) {
            val minutes = if (cloud.lastSyncAt > 0) (System.currentTimeMillis() - cloud.lastSyncAt) / 60_000 else -1L
            val status = when {
                cloud.failed -> C.syncFailed
                minutes >= 0 -> C.syncedWithCloud + " · " + C.ago(minutes) + (if (cloud.deviceCount > 0) " · " + C.devicesCount(cloud.deviceCount) else "")
                else -> C.notSynced
            }
            PrefRow(C.syncNow, "", hint = status) { vm.syncFromCloud() }
            PrefRow(C.thisDevice, cloud.selfName, hint = C.deviceNameHint) { dialog = OpenDialog.RENAME_DEVICE }
            val up by panes.state.collectAsState()
            SwitchPrefRow(C.syncDisplayPrefs, up.syncDisplayPrefs, hint = C.syncDisplayPrefsHint) { panes.setSyncDisplayPrefs(it) }
            if (cloud.devices.isNotEmpty()) PrefRow(C.accountDevices, "", hint = cloud.devices.joinToString(" · ") { com.ultratv.tv.nativeapp.data.config.CloudSyncLogic.deviceDisplayName(it) }) { }
        } else PrefRow(D.cloudSyncImport, "", hint = null) { vm.syncFromCloud() }
        PrefRow(D.workerUrlTitle, hostOnly(workerBase).substringAfter("://")) { dialog = OpenDialog.WORKER_URL }
        if (com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) PrefRow(com.ultratv.tv.nativeapp.ui.mobile.LocalMobileStrings.current.openDashboard, "", hint = hostOnly(workerBase).substringAfter("://")) { ctx.openInBrowser(workerBase) }
    }
    if (paired && connWarn) Text("⚠ " + C.connectionWarning, color = Ux.Err, fontFamily = Manrope, fontSize = 22.spx, lineHeight = 30.spx, modifier = Modifier.padding(horizontal = 8.design, vertical = 6.design))
    // « Tableau de bord » : adresse en clair + QR à scanner avec le téléphone (le Worker choisi par l'utilisateur, sinon celui par défaut).
    if (!com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.design)).background(Ux.SurfaceDeep).padding(24.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.design)) {
            com.ultratv.tv.nativeapp.ui.design.QrCode(workerBase.trimEnd('/'), 132.design)
            Column(verticalArrangement = Arrangement.spacedBy(6.design)) {
                Text(D.dashboardTitle, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.spx, maxLines = 1)
                Text(hostOnly(workerBase).substringAfter("://"), color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(D.dashboardScan, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
            }
        }
    }
    // Recherche et télécommande depuis le téléphone (réseau local) : pour TV et box seulement.
    if (!com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) {
        SettingsCard {
            PrefRow(D.remoteTitle, "", hint = D.remoteHint) { dialog = OpenDialog.REMOTE }
        }
    }
    GroupLabel(D.backup)
    SettingsCard {
        PrefRow(D.exportBackup, "") { vm.prepareBackup(S.toastBackupReady, password = backupPwd.takeIf { it.isNotEmpty() }); saveBackup.launch("ultra-tv-backup-${System.currentTimeMillis()}.json") }
        PrefRow(D.importBackup, "") { loadBackup.launch(arrayOf("application/json", "*/*")) }
    }

    when (dialog) {
        OpenDialog.ADD_CHOOSER -> ChoiceDialog(D.addSource, listOf(OpenDialog.XTREAM to S.wiz.cardXtream, OpenDialog.STALKER to D.kindStalker, OpenDialog.M3U_URL to S.wiz.cardM3uUrl, OpenDialog.NONE to S.wiz.cardM3uFile, OpenDialog.CLOUD to (if (com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) com.ultratv.tv.nativeapp.ui.mobile.LocalMobileStrings.current.fromCloud else D.cardCloud))
            // « Depuis le téléphone » : pour TV et box seulement (sur un téléphone, on est déjà sur l'appareil qui saisirait).
            + (if (com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) emptyList() else listOf(OpenDialog.PHONE to D.cardPhone)), null,
            onPick = { k -> if (k == OpenDialog.NONE) { dialog = OpenDialog.NONE; pickFile.launch(arrayOf("*/*")) } else dialog = k }, onDismiss = { dialog = OpenDialog.NONE })
        OpenDialog.CLOUD -> { dialog = OpenDialog.NONE; vm.startPairing() }
        OpenDialog.REMOTE -> PhoneRemoteDialog(hiltViewModel(), onClose = { dialog = OpenDialog.NONE })
        OpenDialog.PHONE -> { dialog = OpenDialog.NONE; vm.startWebSend(phoneReceived, stalkerFailed) }
        OpenDialog.XTREAM -> XtreamDialog({ dialog = OpenDialog.NONE }) { n, u, user, pw -> vm.addAndSync(n, u, user, pw); dialog = OpenDialog.NONE }
        OpenDialog.M3U_URL -> M3uDialog({ dialog = OpenDialog.NONE }) { n, u -> vm.addM3uAndSync(n, u); dialog = OpenDialog.NONE }
        OpenDialog.STALKER -> {
            // La connexion au portail est vérifiée avant l'ajout : le dialogue reste ouvert (« Connexion au portail… ») jusqu'au verdict.
            var checking by remember { mutableStateOf(false) }
            // Télémétrie : ouverture / fermeture avec sa cause (« other » = disparu sans action de l'utilisateur).
            val closeReason = remember { arrayOf<String?>(null) }
            androidx.compose.runtime.DisposableEffect(Unit) {
                StalkerTelemetry.dialogOpened()
                onDispose { StalkerTelemetry.dialogClosed(closeReason[0] ?: "other") }
            }
            StalkerDialog(
                onDismiss = { closeReason[0] = "cancel"; dialog = OpenDialog.NONE },
                busy = checking,
                onBack = { closeReason[0] = "back"; dialog = OpenDialog.NONE },
            ) { n, u, mac, login, pw ->
                checking = true
                vm.addStalkerAndSync(n, u, mac, login, pw, D.stalkerFailed) { ok -> checking = false; if (ok) { closeReason[0] = "submit"; dialog = OpenDialog.NONE } }
            }
        }
        OpenDialog.WORKER_URL -> WorkerUrlDialog(workerBase, onSave = { vm.saveWorkerBase(it); dialog = OpenDialog.NONE }, onDismiss = { dialog = OpenDialog.NONE })
        OpenDialog.SOURCE_ACTIONS -> selected?.let { p ->
            val shareable = paired && (p.kind == "XTREAM" || p.kind == "M3U" || p.kind == "STALKER")
            val linked = vm.cloudIdOf(p.id) != null
            val supported = p.kind in SUPPORTED_KINDS
            val opts = buildList {
                if (supported) { add("default" to D.setDefault); add("sync" to D.syncNow); add("guide" to D.sourceGuide) }
                if (supported && shareable && !linked) add("share" to C.shareSource)
                if (supported && shareable && linked) { add("share" to C.editSharing); add("stop" to C.stopSharing) }
                add("delete" to D.deleteSource)
            }
            ChoiceDialog(p.name, opts, if (supported) null else D.unsupportedSourceHint,
                onPick = { k -> when (k) { "default" -> vm.setDefault(p.id); "sync" -> vm.resync(p.id); "delete" -> vm.delete(p.id); "stop" -> vm.stopSharing(p.id) }; dialog = when (k) { "share" -> OpenDialog.SHARE; "guide" -> OpenDialog.SOURCE_GUIDE; else -> OpenDialog.NONE } }, onDismiss = { dialog = OpenDialog.NONE })
        }
        // « Guide de cette source » : adresse XMLTV propre à la source (remplace ou complète celui du fournisseur), vérifiée avant l'enregistrement.
        OpenDialog.SOURCE_GUIDE -> selected?.let { p ->
            val cur = vm.customGuide(p.id)
            GuideAddressDialog(
                initialUrl = cur?.url.orEmpty(), perSource = true, initialMode = cur?.mode ?: com.ultratv.tv.nativeapp.data.xmltv.GuideMode.COMPLETE,
                onVerify = { vm.verifyGuide(it) },
                onSave = { url, mode -> vm.setCustomGuide(p.id, url, mode); com.ultratv.tv.nativeapp.ui.common.Toaster.ok(if (url.isBlank()) D.guideRemoved else D.guideSaved); dialog = OpenDialog.NONE },
                onDismiss = { dialog = OpenDialog.NONE },
            )
        }
        OpenDialog.SHARE -> selected?.let { p ->
            ShareDevicesDialog(cloud.devices, cloud.selfId, null, onConfirm = { sel -> vm.share(p.id, sel); dialog = OpenDialog.NONE }, onDismiss = { dialog = OpenDialog.NONE })
        }
        OpenDialog.RENAME_DEVICE -> RenameDeviceDialog(cloud.selfName, onSave = { vm.renameThisDevice(it); dialog = OpenDialog.NONE }, onDismiss = { dialog = OpenDialog.NONE })
        else -> Unit
    }
    // Après l'ajout d'une source sur un appareil appairé : proposition (jamais d'envoi automatique).
    offer?.let { id ->
        OfferShareDialog(onShare = { vm.dismissOffer(); scope.launch { selected = vm.providerById(id) }; dialog = OpenDialog.SHARE }, onLater = { vm.dismissOffer() })
    }
    // Sources retirées du cloud pour cet appareil, avec des favoris ou enregistrements : confirmation.
    if (cloud.pending.isNotEmpty()) RemovalDialog(cloud.pending, onRemove = { vm.confirmRemovals(it) }, onKeep = { vm.keepLocal(it) })
    // Accueil vide (tactile) : « Depuis le cloud » ouvre directement l'appairage.
    val wantPairing by com.ultratv.tv.nativeapp.StartupNav.startPairing.collectAsState()
    androidx.compose.runtime.LaunchedEffect(wantPairing) { if (wantPairing) { com.ultratv.tv.nativeapp.StartupNav.startPairing.value = false; vm.startPairing() } }
    CloudPairingDialog(pairingUi, onCancel = { vm.cancelPairing() }, onRetry = { vm.startPairing() })
    PhoneSendDialog(webSendUi, onCancel = { vm.cancelWebSend() }, onRetry = { vm.startWebSend(phoneReceived, stalkerFailed) })
}

// ───────────────────────── Synchronisation ─────────────────────────

@Composable
private fun SyncPane(vm: SettingsViewModel, panes: SettingsPanesViewModel, onNavigate: (String) -> Unit) {
    val D = LocalDs.current
    val p by panes.state.collectAsState()
    val provider by panes.provider.collectAsState()
    val counts by panes.counts.collectAsState()
    val totals by panes.categoryTotals.collectAsState()
    var purgeFor by remember { mutableStateOf<String?>(null) }
    var extraOpen by remember { mutableStateOf(false) }
    var customOpen by remember { mutableStateOf(false) }
    val webSendUi by vm.webSendUi.collectAsState()
    val phoneReceived = D.phoneReceived
    val stalkerFailed = D.stalkerFailed
    val guideSaved = D.guideSaved
    val isCustom = p.extraEpg.isNotBlank() && com.ultratv.tv.nativeapp.data.xmltv.ExtraEpg.labelOf(p.extraEpg) == null
    val extraLabel = com.ultratv.tv.nativeapp.data.xmltv.ExtraEpg.labelOf(p.extraEpg)
        ?: if (isCustom) D.guideCustomLabel(com.ultratv.tv.nativeapp.data.xmltv.GuideAddress.hostOf(p.extraEpg).ifBlank { "…" }) else D.extraGuideOff
    val last = provider?.let { maxOf(it.lastLiveSyncAt, it.lastVodSyncAt, it.lastSeriesSyncAt, it.lastEpgSyncAt) } ?: 0L
    val subtitle = if (last <= 0L) D.neverSynced else D.lastUpdate.format(ago(D, System.currentTimeMillis() - last), nextText(p.syncMode, p.syncHour, p.syncIntervalHours, last))

    // Téléphone : la barre du haut porte déjà le titre ; état sur toute la largeur puis bouton dessous, et les quatre modes
    // en liste (libellé et description en entier) au lieu de quatre cartes côte à côte tronquées.
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    if (touch) {
        Text(subtitle, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx)
        PillButton(D.syncNow, { panes.syncNow() }, heightPx = 64, hPadPx = 32, fontPx = 22, weight = FontWeight.Bold, iconPath = "M20 12a8 8 0 1 1-2.3-5.7M20 4v5h-5", bg = Ux.Surface2)
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) { PaneTitle(D.rubSync, subtitle) }
        PillButton(D.syncNow, { panes.syncNow() }, heightPx = 64, hPadPx = 32, fontPx = 22, weight = FontWeight.Bold, iconPath = "M20 12a8 8 0 1 1-2.3-5.7M20 4v5h-5", bg = Ux.Surface2)
    }
    GroupLabel(D.whenToUpdate)
    val modes = listOf("auto" to (D.modeAuto to D.modeAutoDesc), "launch" to (D.modeLaunch to D.modeLaunchDesc), "scheduled" to (D.modeScheduled to D.modeScheduledDesc(p.syncHour)), "manual" to (D.modeManual to D.modeManualDesc))
    // Modes en liste sur TOUS les appareils : libellé et description en entier (les quatre cartes côte à côte coupaient
    // leurs textes, même sur un grand téléviseur).
    SettingsCard {
        modes.forEach { (id, pair) -> com.ultratv.tv.nativeapp.ui.design.ChoiceRow(pair.first, p.syncMode == id, { panes.setMode(id) }, hint = null, heightPx = 100, sub = pair.second) }
    }
    if (p.syncMode == "scheduled") SettingsCard { PrefRow(D.modeScheduled, com.ultratv.tv.nativeapp.ui.common.EpgClock.hourLabel(p.syncHour)) { panes.setHour((p.syncHour + 1) % 24) } }
    GroupLabel(D.contentToSync)
    SettingsCard {
        val ttl = com.ultratv.tv.nativeapp.data.sync.SyncPolicy.ttl(p.syncIntervalHours)
        val live = "${D.channels(counts.live)} · ${D.everyHours((ttl.liveMs / 3_600_000L).toInt())}"
        ContentRow(Icons.Live, D.liveTv, live, p.syncLive) { if (!it) purgeFor = "live" else panes.setPart("live", true, false) }
        ContentRow(Icons.Guide, D.stepGuide, D.guideWindow(2, 24) + " · " + D.everyNight, p.syncEpg) { panes.setPart("epg", it, false) }
        if (p.syncEpg) PrefRow(D.extraGuide, extraLabel, hint = D.extraGuideHint) { extraOpen = true }
        ContentRow(Icons.Movies, D.stepMovies, D.moviesOf(counts.movies) + " · " + D.everyDay, p.syncVod) { if (!it) purgeFor = "vod" else panes.setPart("vod", true, false) }
        ContentRow(Icons.Series, D.stepSeries, D.seriesOf(counts.series) + " · " + D.everyDay, p.syncSeries) { if (!it) purgeFor = "series" else panes.setPart("series", true, false) }
    }
    SettingsCard {
        SwitchPrefRow(D.wifiOnly, p.syncUnmeteredOnly) { panes.setUnmetered(it) }
        PrefRow(D.manageCategories, D.activeDisabled.format(totals.enabled, totals.disabled)) { onNavigate("categories") }
    }
    if (extraOpen) ChoiceDialog(
        D.extraGuide,
        listOf("" to D.extraGuideOff) + com.ultratv.tv.nativeapp.data.xmltv.ExtraEpg.OPTIONS + (CUSTOM_GUIDE_KEY to D.guideCustomOption),
        if (isCustom) CUSTOM_GUIDE_KEY else p.extraEpg,
        onPick = { if (it == CUSTOM_GUIDE_KEY) customOpen = true else panes.setExtraEpg(it); extraOpen = false },
        onDismiss = { extraOpen = false },
    )
    // Adresse personnalisée : saisie à la télécommande ou envoyée depuis le téléphone, vérifiée avant l'enregistrement.
    if (customOpen) GuideAddressDialog(
        initialUrl = if (isCustom) p.extraEpg else "", perSource = false,
        onVerify = { panes.verifyGuide(it) },
        onSave = { url, _ -> panes.setExtraEpg(url); com.ultratv.tv.nativeapp.ui.common.Toaster.ok(guideSaved); customOpen = false },
        onDismiss = { customOpen = false },
        onPhone = if (com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) null else ({ customOpen = false; vm.startWebSend(phoneReceived, stalkerFailed) { c -> D.guideCheckMessage(c) ?: guideSaved } }),
    )
    PhoneSendDialog(webSendUi, onCancel = { vm.cancelWebSend() }, onRetry = { vm.startWebSend(phoneReceived, stalkerFailed) { c -> D.guideCheckMessage(c) ?: guideSaved } })
    purgeFor?.let { part ->
        ChoiceDialog(D.purgeTitle, listOf(false to D.keepData, true to D.purgeData), null, onPick = { purge -> panes.setPart(part, false, purge); purgeFor = null }, onDismiss = { purgeFor = null })
    }
}

@Composable
private fun ContentRow(icon: String, label: String, meta: String, on: Boolean, onChange: (Boolean) -> Unit) {
    FocusSurface(onClick = { onChange(!on) }, shape = settingsRowShape(), bg = Ux.SurfaceDeep, ringWidth = settingsRowRing(), focusedScale = 1.0f, modifier = Modifier.fillMaxWidth().height(84.designT)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 28.design), verticalAlignment = Alignment.CenterVertically) {
            DIcon(icon, 28.design, if (f) Ux.TextOnLight else Ux.Text)
            Spacer(Modifier.width(20.design))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.design)) {
                Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1)
                Text(meta, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(on, inverted = f)
        }
    }
}

private fun ago(D: DesignStrings, ms: Long): String {
    val m = ms / 60_000
    return when { m < 1 -> D.minutesShort.format(1); m < 60 -> D.minutesShort.format(m.toInt()); m < 48 * 60 -> D.hoursShort.format((m / 60).toInt()); else -> D.hoursShort.format((m / 60).toInt()) }
}

private fun nextText(mode: String, hour: Int, intervalH: Int, last: Long): String = when (mode) {
    "scheduled" -> com.ultratv.tv.nativeapp.ui.common.EpgClock.hourLabel(hour)
    "manual" -> "—"
    else -> com.ultratv.tv.nativeapp.ui.common.EpgClock.wall(last + (if (intervalH > 0) intervalH else 6) * 3_600_000L)
}

// ───────────────────────── Catégories (résumé) ─────────────────────────

@Composable
private fun CategoriesPane(panes: SettingsPanesViewModel, onNavigate: (String) -> Unit) {
    val D = LocalDs.current
    val totals by panes.categoryTotals.collectAsState()
    PaneTitle(D.rubCategories, D.categoriesFooter)
    SettingsCard { PrefRow(D.manageCategories, D.activeDisabled.format(totals.enabled, totals.disabled)) { onNavigate("categories") } }
}

// ───────────────────────── Affichage ─────────────────────────

@Composable
private fun DisplayPane(vm: SettingsViewModel, panes: SettingsPanesViewModel, app: AppViewModel) {
    val D = LocalDs.current
    val p by panes.state.collectAsState()
    var choice by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val pickLogos = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) { runCatching { ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }; vm.setLocalLogosFolderUri(tree.toString()) }
    }
    val logosUri by vm.localLogosFolderUri.collectAsState()
    PaneTitle(D.rubDisplay, D.setDescDisplay)
    SettingsCard {
        // Mode simple : grandes tuiles à la place de l'accueil (profil courant) ; la sortie demande le code parental s'il existe.
        com.ultratv.tv.nativeapp.ui.simple.SimpleModeRow(p.simpleMode, onChange = { app.setSimpleMode(it) })
        PrefRow(D.theme, themeLabel(D, p.theme)) { choice = "theme" }
        PrefRow(D.language, D.langName(AppLang.fromCode(p.language))) { choice = "lang" }
        // (Réglage « Menu : barre latérale / barre du haut » retiré : depuis le nouveau menu latéral il n'avait plus d'effet.)
        // Téléphone : « Raccourcis » = les trois rubriques entre Accueil et Plus (les autres restent dans « Plus »), à choisir
        // et à ordonner dans un seul écran.
        if (com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) {
            val tabs = com.ultratv.tv.nativeapp.ui.mobile.bottomTabRoutes(p.bottomTabs, p.sportSection)
            PrefRow(D.shortcutsTitle, tabs.map { com.ultratv.tv.nativeapp.ui.mobile.bottomTabLabel(it) }.joinToString(" · ")) { choice = "shortcuts" }
        }
        SwitchPrefRow(D.channelNumbers, p.showChannelNumbers) { app.setShowChannelNumbers(it) }
        SwitchPrefRow(D.channelLogos, p.showChannelLogos) { app.setShowChannelLogos(it) }
        SwitchPrefRow(D.sportSectionLabel, p.sportSection, hint = D.sportSectionHint) { app.setSportSection(it) }
        // (« Colonne des chaînes du guide » retiré : largeur normale pour tous, un réglage de moins.)
        SwitchPrefRow(D.launchAtBoot, p.launchAtBoot) { app.setLaunchAtBoot(it) }
        val start = com.ultratv.tv.nativeapp.nav.StartScreen.parse(p.startScreen, p.autoPlayLastOnLaunch)
        PrefRow(D.openOn, D.startScreenName(start)) { choice = "open" }
        PrefRow(D.timeZone, timeZoneLabel(D, p.timeZone)) { choice = "tz" }
        PrefRow(D.timeFormat, timeFormatLabel(D, com.ultratv.tv.nativeapp.ui.common.TimeFormatPref.parse(p.timeFormat))) { choice = "tf" }
        PrefRow(D.logosFolder, if (logosUri.isBlank()) D.none else "…" + logosUri.takeLast(24), hint = null) { pickLogos.launch(null) }
    }
    // Personnalisation : accent, taille du texte, rangées de l'accueil.
    GroupLabel(D.personalization)
    SettingsCard {
        PrefRow(D.accentColor, D.accentName(AccentChoice.parse(p.accent))) { choice = "accent" }
        PrefRow(D.textSizeLabel, D.textSizeName(TextSize.parse(p.textSize))) { choice = "text" }
        PrefRow(D.animations, D.animationName(com.ultratv.tv.nativeapp.ui.design.AnimationLevel.parse(p.animations))) { choice = "anim" }
        val layout = remember(p.homeOrder, p.homeHidden) { com.ultratv.tv.nativeapp.ui.home.HomeLayout.parse(p.homeOrder, p.homeHidden) }
        PrefRow(D.homeRows, D.homeRowsSummary(layout.order.count { layout.isVisible(it) }, layout.order.size)) { choice = "home" }
    }
    when (choice) {
        "theme" -> ChoiceDialog(D.theme, AppTheme.entries.map { it to themeLabel(D, it) }, p.theme, { app.setTheme(it); choice = "" }, { choice = "" })
        "lang" -> ChoiceDialog(D.language, AppLang.entries.map { it.code to D.langName(it) }, p.language, { app.setLanguage(it); choice = "" }, { choice = "" })
        "open" -> ChoiceDialog(D.openOn, com.ultratv.tv.nativeapp.nav.StartScreen.entries.map { it to D.startScreenName(it) }, com.ultratv.tv.nativeapp.nav.StartScreen.parse(p.startScreen, p.autoPlayLastOnLaunch), { app.setStartScreen(it.key); choice = "" }, { choice = "" })
        "accent" -> AccentDialog(AccentChoice.parse(p.accent), { app.setAccent(it.key); choice = "" }, { choice = "" })
        "anim" -> ChoiceDialog(D.animations, com.ultratv.tv.nativeapp.ui.design.AnimationLevel.entries.map { it to D.animationName(it) }, com.ultratv.tv.nativeapp.ui.design.AnimationLevel.parse(p.animations), { app.setAnimations(it.key); choice = "" }, { choice = "" })
        "text" -> ChoiceDialog(D.textSizeLabel, TextSize.entries.map { it to D.textSizeName(it) }, TextSize.parse(p.textSize), { app.setTextSize(it.key); choice = "" }, { choice = "" })
        "home" -> HomeRowsDialog(com.ultratv.tv.nativeapp.ui.home.HomeLayout.parse(p.homeOrder, p.homeHidden), onSave = { app.setHomeLayout(it) }, onDismiss = { choice = "" })
        "shortcuts" -> ShortcutsDialog(
            com.ultratv.tv.nativeapp.ui.mobile.bottomTabRoutes(p.bottomTabs, p.sportSection), p.sportSection,
            onSave = { app.setBottomTabs(it) }, onDismiss = { choice = "" },
        )
        "tf" -> ChoiceDialog(D.timeFormat, com.ultratv.tv.nativeapp.ui.common.TimeFormatPref.entries.map { it to timeFormatLabel(D, it) }, com.ultratv.tv.nativeapp.ui.common.TimeFormatPref.parse(p.timeFormat), { app.setTimeFormat(it.key); choice = "" }, { choice = "" })
        "tz" -> ChoiceDialog(D.timeZone, TIME_ZONES.map { it to timeZoneLabel(D, it) }, p.timeZone, { app.setTimeZone(it); choice = "" }, { choice = "" })
    }
}

/** Fuseaux proposés : « appareil » par défaut ; GMT pour une box mal réglée (ex. GMT+1 au lieu de GMT). */
private val TIME_ZONES = listOf("", "GMT", "Africa/Casablanca", "Europe/London", "Europe/Paris", "Africa/Algiers", "Africa/Tunis", "Africa/Dakar", "Asia/Riyadh", "America/New_York")

/** Libellé avec le décalage ACTUEL (heure d'été comprise) : « Europe/Paris (GMT+2) ». */
internal fun timeZoneLabel(D: DesignStrings, id: String): String {
    val tz = if (id.isBlank()) java.util.TimeZone.getDefault() else java.util.TimeZone.getTimeZone(id)
    val off = tz.getOffset(System.currentTimeMillis()) / 60_000
    val gmt = if (off == 0) "GMT" else "GMT" + (if (off > 0) "+" else "−") + (kotlin.math.abs(off) / 60) + (if (off % 60 != 0) ":%02d".format(java.util.Locale.ROOT, kotlin.math.abs(off) % 60) else "")
    val name = when (id) { "" -> D.tzSystem; "GMT" -> return "GMT"; else -> id.substringAfter('/').replace('_', ' ') }
    return "$name ($gmt)"
}

private fun timeFormatLabel(D: DesignStrings, f: com.ultratv.tv.nativeapp.ui.common.TimeFormatPref) = when (f) {
    com.ultratv.tv.nativeapp.ui.common.TimeFormatPref.AUTO -> D.themeAuto
    com.ultratv.tv.nativeapp.ui.common.TimeFormatPref.H12 -> D.timeFormat12
    com.ultratv.tv.nativeapp.ui.common.TimeFormatPref.H24 -> D.timeFormat24
}
private fun qualityLabel(D: DesignStrings, q: String) = when (q) { "4k" -> "4K"; "fhd" -> "FHD"; "hd" -> "HD"; "sd" -> "SD"; else -> D.auto }
private fun themeLabel(D: DesignStrings, t: AppTheme) = when (t) {
    AppTheme.LIGHT -> D.themeLight; AppTheme.AUTO -> D.themeAuto; AppTheme.DARK -> D.themeDark; AppTheme.OLED -> D.themeOled
    AppTheme.MIDNIGHT -> D.themeMidnight; AppTheme.FOREST -> D.themeForest; AppTheme.PLUM -> D.themePlum
    AppTheme.SAND -> D.themeSand; AppTheme.CONTRAST -> D.themeContrast
}

// ───────────────────────── Lecture ─────────────────────────

@Composable
private fun PlaybackPane(panes: SettingsPanesViewModel, app: AppViewModel) {
    val D = LocalDs.current
    val p by panes.state.collectAsState()
    val ad by panes.adaptive.state.collectAsState()
    var choice by remember { mutableStateOf("") }
    val engineLabel = when (p.playerEngine) { "exo" -> D.engineExo; "vlc" -> D.engineVlc; else -> "${D.auto} · ${D.engineExo}" }
    val decLabel = when (p.decoderMode) { "hw" -> D.hardware; "sw" -> D.software; else -> "${D.auto} · ${D.hardware}" }
    val bufLabel = when (p.bufferPreset) { "low_latency" -> D.bufLow; "balanced" -> D.bufBalanced; "stable" -> D.bufStable; "custom" -> D.bufCustom; else -> "${D.auto} · ${ad.auto.bufferPreset.name.lowercase().replace('_', ' ')}" }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) { PaneTitle(D.rubPlayback, D.setDescPlayback) }
        if (p.playerEngine != "auto" || p.decoderMode != "auto" || p.bufferPreset != "auto") PillButton(D.backToAuto, { panes.backToAuto() }, bg = Ux.Surface2)
    }
    SettingsCard {
        PrefRow(D.playerRow, engineLabel) { choice = "engine" }
        PrefRow(D.decodingRow, decLabel) { choice = "dec" }
        PrefRow(D.bufferRow, bufLabel) { choice = "buf" }
        PrefRow(D.maxQuality, "${D.auto} · ${ad.auto.maxVideoHeight}p") { }
        PrefRow(D.preferredQuality, qualityLabel(D, p.preferredQuality), hint = D.preferredQualityHint) { choice = "pq" }
        PrefRow(D.externalPlayer, if (p.defaultPlayer == DefaultPlayer.EXTERNAL) D.on else D.none) { choice = "ext" }
        SwitchPrefRow(D.autoNext, p.autoPlayNextEpisode) { app.setAutoPlayNext(it) }
        SwitchPrefRow(D.resumePlayback, p.resumePlayback) { app.setResumePlayback(it) }
        SwitchPrefRow(D.autoFps, p.autoFrameRate) { app.setAutoFrameRate(it) }
        if (!com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) SwitchPrefRow(D.zapVertical, p.zapVertical, hint = D.zapVerticalHint) { app.setZapVertical(it) }
        SwitchPrefRow(D.compatVideo, com.ultratv.tv.nativeapp.ui.player.engine.useTextureView(p.videoSurface, com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current), hint = D.compatVideoHint) {
            app.setVideoSurface(if (it) "texture" else "surface")
        }
        // Aperçu vidéo du Direct (TV seulement : sans effet sur téléphone / tablette).
        if (!com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) com.ultratv.tv.nativeapp.ui.live.LivePreviewSettingsRows()
    }
    DnsSection()   // Réseau › DNS (DnsSection.kt)
    com.ultratv.tv.nativeapp.ui.downloads.DownloadSettingsSection()   // Téléchargements hors ligne (Wi-Fi seulement, emplacement)
    // Trakt (compte lié seulement) : marquer vu à la fin, listes personnelles à l'accueil.
    com.ultratv.tv.nativeapp.ui.trakt.TraktSettingsSection()
    when (choice) {
        "engine" -> ChoiceDialog(D.playerRow, listOf("auto" to D.auto, "exo" to D.engineExo, "vlc" to D.engineVlc), p.playerEngine, { panes.setPlayerEngine(it); choice = "" }, { choice = "" })
        "dec" -> ChoiceDialog(D.decodingRow, listOf("auto" to D.auto, "hw" to D.hardware, "sw" to D.software), p.decoderMode, { panes.setDecoder(it); choice = "" }, { choice = "" })
        "buf" -> ChoiceDialog(D.bufferRow, listOf("auto" to D.auto, "low_latency" to D.bufLow, "balanced" to D.bufBalanced, "stable" to D.bufStable), p.bufferPreset, { panes.setBufferPreset(it); choice = "" }, { choice = "" })
        "pq" -> ChoiceDialog(D.preferredQuality, listOf("auto" to D.auto, "4k" to "4K", "fhd" to "FHD", "hd" to "HD", "sd" to "SD"), p.preferredQuality, { panes.setPreferredQuality(it); choice = "" }, { choice = "" })
        "ext" -> ChoiceDialog(D.externalPlayer, listOf(DefaultPlayer.INTERNAL to D.none, DefaultPlayer.EXTERNAL to D.on), p.defaultPlayer, { app.setDefaultPlayer(it); choice = "" }, { choice = "" })
    }
}

// ───────────────────────── Langues ─────────────────────────

@Composable
private fun LanguagesPane(panes: SettingsPanesViewModel, app: AppViewModel) {
    val D = LocalDs.current
    val p by panes.state.collectAsState()
    val counts by panes.langCounts.collectAsState()
    var picker by remember { mutableStateOf(false) }
    var uiLang by remember { mutableStateOf(false) }
    val selected = p.languages.split(',').filter { it.isNotBlank() }
    // Purge d'une langue masquée / resynchronisation d'une langue réactivée : message clair (voir LanguageManager).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        panes.langNotice.collect { o ->
            com.ultratv.tv.nativeapp.ui.common.Toaster.show(if (o.resync.isNotEmpty()) D.langNoticeResync else D.langNoticePurged)
        }
    }
    PaneTitle(D.rubLanguages, D.setDescLanguages)
    SettingsCard {
        PrefRow(D.appLanguage, D.langName(AppLang.fromCode(p.language))) { uiLang = true }
        PrefRow(D.contentLanguages, if (selected.isEmpty()) D.allLanguages else selected.joinToString(" · ") { it.uppercase() }) { picker = true }
        SwitchPrefRow(D.includeMulti, p.includeMulti) { panes.setIncludeMulti(it) }
        SwitchPrefRow(D.includeUnknown, p.includeUnknownLang) { panes.setIncludeUnknown(it) }
        TranslateHelpRow(D)
    }
    // Les langues masquées ne sont pas chargées : plus rapide, moins de mémoire.
    androidx.tv.material3.Text(D.langHiddenNotLoaded, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 32.spx, maxLines = 4)
    if (uiLang) ChoiceDialog(D.appLanguage, AppLang.entries.map { it.code to D.langName(it) }, p.language, { app.setLanguage(it); uiLang = false }, { uiLang = false })
    if (picker) LanguagePickerDialog(counts.filter { it.lang.isNotEmpty() && it.lang != "MULTI" }.sortedByDescending { it.n }.map { it.lang to it.n }, selected.toSet(), { panes.setLanguages(it.sorted().joinToString(",")) }, { picker = false })
}

/** Cases à cocher par langue détectée (avec le nombre de catégories) — « Toutes les langues » vide la sélection. */
@Composable
fun LanguagePickerDialog(languages: List<Pair<String, Int>>, selected: Set<String>, onChange: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val D = LocalDs.current
    val fallback = listOf("fr", "en", "ar", "es", "de", "pt", "it", "tr", "nl", "el").map { it to 0 }
    val items = languages.ifEmpty { fallback }
    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim)) {
        Column(Modifier.responsiveWidth(760).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(40.design), verticalArrangement = Arrangement.spacedBy(10.design)) {
            Text(D.contentLanguages, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 1, modifier = Modifier.padding(bottom = 8.design))
            Text(D.langHiddenNotLoaded, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, lineHeight = 30.spx, maxLines = 3, modifier = Modifier.padding(bottom = 8.design))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.height(600.design), verticalArrangement = Arrangement.spacedBy(10.design)) {
                item { LangRow(D.allLanguages, "", selected.isEmpty()) { onChange(emptySet()) } }
                items(items.size, key = { items[it].first }) { i ->
                    val (code, n) = items[i]
                    LangRow(java.util.Locale(code).getDisplayLanguage(D.locale).replaceFirstChar { it.uppercase() }, if (n > 0) "$n" else "", code in selected) { onChange(if (code in selected) selected - code else selected + code) }
                }
            }
            PillButton(D.close, onDismiss, bg = Ux.Surface)
        }
    }
}

@Composable
private fun LangRow(label: String, count: String, on: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(16.design), bg = Ux.Surface, ringWidth = 5.design, focusedScale = 1.0f, modifier = Modifier.fillMaxWidth().height(68.designT)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 28.design), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1, modifier = Modifier.weight(1f))
            if (count.isNotEmpty()) Text(count, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
            Spacer(Modifier.width(16.design))
            Switch(on, inverted = f)
        }
    }
}

// ───────────────────────── À propos ─────────────────────────

@Composable
private fun AboutPane(vm: SettingsViewModel, panes: SettingsPanesViewModel, app: AppViewModel, onNavigate: (String) -> Unit) {
    val D = LocalDs.current
    val S = LocalStrings.current
    val p by panes.state.collectAsState()
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    PaneTitle(D.rubAbout, D.setDescAbout)
    SettingsCard {
        // Dons (Ko-fi) : en tête de la rubrique, discret (une ligne comme les autres).
        SupportRow(D)
        PrefRow(D.version, com.ultratv.tv.nativeapp.BuildConfig.VERSION_NAME) { }
        PrefRow(D.checkUpdates, msg.orEmpty()) {
            scope.launch {
                val info = com.ultratv.tv.nativeapp.update.UpdateChecker.checkForUpdate()
                msg = if (info != null) S.settingsUpdateAvailableTemplate.format(info.versionName) else S.settingsUpToDateTemplate.format(com.ultratv.tv.nativeapp.BuildConfig.VERSION_NAME)
            }
        }
        PrefRow(D.diagnostic, "", hint = D.diagnosticHint) { onNavigate("diagnostic") }
        SwitchPrefRow(D.telemetry, p.telemetryEnabled, hint = D.telemetryHint) { app.setTelemetry(it) }
    }
    // Attribution exigée par les conditions d'utilisation de l'API TMDB.
    androidx.tv.material3.Text(D.tmdbAttribution, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 32.spx, maxLines = 3)
    // Sources des données : l'adresse est affichée en clair (sur TV un lien ne s'ouvre pas forcément) ; les lignes sont
    // focalisables, ce qui fait défiler la page à la télécommande.
    Spacer(Modifier.height(8.design))
    androidx.tv.material3.Text(D.dataSources, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.spx)
    SettingsCard {
        PrefRow("TMDB", "themoviedb.org") { }
        PrefRow("Fanart.tv", "fanart.tv") { }
        PrefRow("TVmaze (CC BY-SA 4.0)", "tvmaze.com", hint = "https://creativecommons.org/licenses/by-sa/4.0/") { }
        PrefRow("TheSportsDB", "thesportsdb.com") { }
        PrefRow("OMDb", "omdbapi.com") { }
        PrefRow("TheIntroDB", "theintrodb.org") { }
    }
    com.ultratv.tv.nativeapp.update.UpdateDialog()
}

/** Règle d'entrée dans le volet des Réglages (pure, testée). */
internal object SettingsFocus {
    /** Entrée depuis la liste des rubriques : flèche droite (ou gauche en arabe) pressée à l'instant. */
    fun enteringFromRubrics(lastKeyCode: Int = com.ultratv.tv.nativeapp.ui.common.InputClock.lastKeyCode, recent: Boolean = com.ultratv.tv.nativeapp.ui.common.InputClock.recentKey()): Boolean =
        recent && (lastKeyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT || lastKeyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT)
}
