package com.ultratv.tv.nativeapp.ui.account

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.xtream.XtreamAccount
import com.ultratv.tv.nativeapp.data.xtream.XtreamClient
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Ux
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.I18n

/** Textes de l'écran « Abonnement » (EN / FR / ES / AR). */
class AccountStrings(private val lang: AppLang) {
    private fun tx(id: Int, vararg args: Any): String = I18n.string(lang, id, *args)
    private fun tp(id: Int, qty: Int, vararg args: Any): String = I18n.plural(lang, id, qty, *args)
    val title get() = tx(R.string.acct_title)
    val sub get() = tx(R.string.acct_sub)
    val iptv get() = tx(R.string.acct_iptv)
    val source get() = tx(R.string.acct_source)
    val account get() = tx(R.string.acct_account)
    val expires get() = tx(R.string.acct_expires)
    val daysLeft get() = tx(R.string.acct_days_left)
    val conns get() = tx(R.string.acct_conns)
    val created get() = tx(R.string.acct_created)
    val trial get() = tx(R.string.acct_trial)
    val yes get() = tx(R.string.acct_yes)
    val server get() = tx(R.string.acct_server)
    val never get() = tx(R.string.acct_never)
    val unknown get() = tx(R.string.acct_unknown)
    val refresh get() = tx(R.string.acct_refresh)
    val loading get() = tx(R.string.acct_loading)
    val offline get() = tx(R.string.acct_offline)
    val noSource get() = tx(R.string.acct_no_source)
    val noXtream get() = tx(R.string.acct_no_xtream)
}

data class AccountState(val sourceName: String? = null, val xtream: Boolean = false, val account: XtreamAccount? = null, val failed: Boolean = false, val loading: Boolean = true)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val providers: ProviderRepository,
    private val xtream: XtreamClient,
    private val stalker: com.ultratv.tv.nativeapp.data.stalker.StalkerClient,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state

    private var job: kotlinx.coroutines.Job? = null

    // Suit la source ACTIVE : changer de source (ou ses identifiants) recharge l'abonnement tout seul. Avant, il n'était
    // lu qu'à la création de l'écran, gardé en mémoire par la navigation : il fallait « Actualiser ».
    init {
        viewModelScope.launch {
            providers.observeProviders()
                .map { l -> l.firstOrNull { it.active } ?: l.firstOrNull() }
                .distinctUntilChanged { a, b -> a?.id == b?.id && a?.kind == b?.kind && a?.baseUrl == b?.baseUrl && a?.username == b?.username && a?.password == b?.password }
                .collect { load(it) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val list = providers.observeProviders().first()
            load(list.firstOrNull { it.active } ?: list.firstOrNull())
        }
    }

    private fun load(p: com.ultratv.tv.nativeapp.data.db.ProviderEntity?) {
        job?.cancel()
        _state.value = _state.value.copy(loading = true)
        job = viewModelScope.launch {
            // Portail Stalker : échéance lue par account_info/get_main_info, présentée comme un compte Xtream.
            val isX = p?.kind == "XTREAM" || p?.kind == "STALKER"
            val acc = when {
                p == null -> null
                p.kind == "XTREAM" -> xtream.fetchAccount(p)
                p.kind == "STALKER" -> stalker.fetchAccount(p)
                else -> null
            }
            _state.value = AccountState(p?.name, isX, acc, failed = isX && acc == null, loading = false)
        }
    }
}

private val OK = Color(0xFF1F8A4C)

@Composable
fun AccountRow(k: String, v: String, color: Color = Ux.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = Ux.Muted, fontFamily = com.ultratv.tv.nativeapp.ui.design.Manrope, fontSize = 17.sp)
        Text(v, color = color, fontFamily = com.ultratv.tv.nativeapp.ui.design.Manrope, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AccountCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().background(Ux.Surface, RoundedCornerShape(20.dp)).padding(24.dp)) {
        Text(title, color = Ux.Text, fontFamily = com.ultratv.tv.nativeapp.ui.design.Sora, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

/** Contenu de la carte « Abonnement IPTV » (réutilisée par l'édition Pro). */
@Composable
fun IptvAccountCard(st: AccountState, s: AccountStrings) {
    val date = DateFormat.getDateInstance(DateFormat.LONG)
    fun fmt(ms: Long) = date.format(Date(ms))
    AccountCard(s.iptv) {
        val a = st.account
        when {
            st.loading && a == null -> Text(s.loading, color = Ux.Muted, fontFamily = com.ultratv.tv.nativeapp.ui.design.Manrope, fontSize = 17.sp)
            st.sourceName == null -> Text(s.noSource, color = Ux.Muted, fontFamily = com.ultratv.tv.nativeapp.ui.design.Manrope, fontSize = 17.sp)
            !st.xtream -> { AccountRow(s.source, st.sourceName); Text(s.noXtream, color = Ux.Muted, fontFamily = com.ultratv.tv.nativeapp.ui.design.Manrope, fontSize = 16.sp) }
            else -> {
                AccountRow(s.source, st.sourceName)
                AccountRow(s.account, a?.status ?: s.unknown, if (a?.status.equals("Active", true)) OK else if (a?.status != null) Ux.AccentInk else Ux.Text)
                AccountRow(s.expires, when {
                    a == null -> "—"
                    a.expiresAt == null -> s.never
                    else -> "${fmt(a.expiresAt)} · ${((a.expiresAt - System.currentTimeMillis()).coerceAtLeast(0) + 86_399_999L) / 86_400_000L} ${s.daysLeft}"
                })
                // Portail Stalker : le nombre de connexions n'est pas publié, la ligne est omise.
                if (a == null || a.activeConnections != null || a.maxConnections != null) AccountRow(s.conns, if (a != null) "${a.activeConnections ?: 0} / ${a.maxConnections ?: 1}" else "—")
                a?.createdAt?.let { AccountRow(s.created, fmt(it)) }
                if (a?.trial == true) AccountRow(s.trial, s.yes)
                a?.server?.let { AccountRow(s.server, it) }
                if (st.failed) Text(s.offline, color = Ux.Muted, fontFamily = com.ultratv.tv.nativeapp.ui.design.Manrope, fontSize = 15.sp)
            }
        }
    }
}

/** Écran « Abonnement » : informations du compte IPTV de la source active, lues en direct chez le fournisseur. */
@Composable
fun AccountScreen(vm: AccountViewModel = hiltViewModel()) {
    val st by vm.state.collectAsState()
    val s = AccountStrings(LocalDs.current.lang)
    Box(Modifier.fillMaxSize().background(Ux.Bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(s.title, color = Ux.Text, fontFamily = com.ultratv.tv.nativeapp.ui.design.Sora, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(s.sub, color = Ux.Text2, fontFamily = com.ultratv.tv.nativeapp.ui.design.Manrope, fontSize = 18.sp)
            IptvAccountCard(st, s)
            PillButton(if (st.loading) s.loading else s.refresh, onClick = { vm.refresh() })
        }
    }
}
