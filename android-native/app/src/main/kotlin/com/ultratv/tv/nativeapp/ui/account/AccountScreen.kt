package com.ultratv.tv.nativeapp.ui.account

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

/** Textes de l'écran « Abonnement » (EN / FR / ES / AR). */
class AccountStrings(private val lang: AppLang) {
    private fun t(en: String, fr: String, es: String, ar: String) = when (lang) {
        AppLang.French -> fr
        AppLang.Spanish -> es
        AppLang.Arabic -> ar
        else -> en
    }
    val title get() = t("Subscription", "Abonnement", "Suscripción", "الاشتراك")
    val sub get() = t("Your IPTV subscription, as reported by your provider.", "Votre abonnement IPTV, tel que l'indique votre fournisseur.", "Tu suscripción IPTV, según tu proveedor.", "اشتراك IPTV الخاص بك كما يُبلغ عنه مزوّدك.")
    val iptv get() = t("IPTV subscription", "Abonnement IPTV", "Suscripción IPTV", "اشتراك IPTV")
    val source get() = t("Source", "Source", "Fuente", "المصدر")
    val account get() = t("Account status", "État du compte", "Estado de la cuenta", "حالة الحساب")
    val expires get() = t("Expires", "Expire le", "Caduca el", "ينتهي في")
    val daysLeft get() = t("days left", "jours restants", "días restantes", "يوم متبقٍ")
    val conns get() = t("Connections in use", "Connexions utilisées", "Conexiones en uso", "الاتصالات المستخدمة")
    val created get() = t("Created", "Créé le", "Creada el", "أُنشئ في")
    val trial get() = t("Trial account", "Compte d'essai", "Cuenta de prueba", "حساب تجريبي")
    val yes get() = t("Yes", "Oui", "Sí", "نعم")
    val server get() = t("Server", "Serveur", "Servidor", "الخادم")
    val never get() = t("No expiry date", "Pas de date d'expiration", "Sin fecha de caducidad", "بدون تاريخ انتهاء")
    val unknown get() = t("Unknown", "Inconnu", "Desconocido", "غير معروف")
    val refresh get() = t("Refresh", "Actualiser", "Actualizar", "تحديث")
    val loading get() = t("Loading…", "Chargement…", "Cargando…", "جارٍ التحميل…")
    val offline get() = t("Could not reach the provider. Try again later.", "Fournisseur injoignable. Réessayez plus tard.", "No se pudo contactar con el proveedor. Inténtalo más tarde.", "تعذّر الوصول إلى المزوّد. حاول لاحقًا.")
    val noSource get() = t("No source configured.", "Aucune source configurée.", "No hay ninguna fuente configurada.", "لا يوجد مصدر.")
    val noXtream get() = t("This source type does not provide subscription details.", "Ce type de source ne fournit pas d'informations d'abonnement.", "Este tipo de fuente no proporciona datos de suscripción.", "هذا النوع من المصادر لا يوفّر معلومات الاشتراك.")
}

data class AccountState(val sourceName: String? = null, val xtream: Boolean = false, val account: XtreamAccount? = null, val failed: Boolean = false, val loading: Boolean = true)

@HiltViewModel
class AccountViewModel @Inject constructor(private val providers: ProviderRepository, private val xtream: XtreamClient) : ViewModel() {
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val list = providers.observeProviders().first()
            val p = list.firstOrNull { it.active } ?: list.firstOrNull()
            val isX = p?.kind == "XTREAM"
            val acc = if (p != null && isX) xtream.fetchAccount(p) else null
            _state.value = AccountState(p?.name, isX, acc, failed = isX && acc == null, loading = false)
        }
    }
}

private val OK = Color(0xFF1F8A4C)

@Composable
fun AccountRow(k: String, v: String, color: Color = Ux.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = Ux.Muted, fontSize = 17.sp)
        Text(v, color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AccountCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().background(Ux.Surface, RoundedCornerShape(20.dp)).padding(24.dp)) {
        Text(title, color = Ux.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
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
            st.loading && a == null -> Text(s.loading, color = Ux.Muted, fontSize = 17.sp)
            st.sourceName == null -> Text(s.noSource, color = Ux.Muted, fontSize = 17.sp)
            !st.xtream -> { AccountRow(s.source, st.sourceName); Text(s.noXtream, color = Ux.Muted, fontSize = 16.sp) }
            else -> {
                AccountRow(s.source, st.sourceName)
                AccountRow(s.account, a?.status ?: s.unknown, if (a?.status.equals("Active", true)) OK else if (a?.status != null) Ux.Accent else Ux.Text)
                AccountRow(s.expires, when {
                    a == null -> "—"
                    a.expiresAt == null -> s.never
                    else -> "${fmt(a.expiresAt)} · ${((a.expiresAt - System.currentTimeMillis()).coerceAtLeast(0) + 86_399_999L) / 86_400_000L} ${s.daysLeft}"
                })
                AccountRow(s.conns, if (a != null) "${a.activeConnections ?: 0} / ${a.maxConnections ?: 1}" else "—")
                a?.createdAt?.let { AccountRow(s.created, fmt(it)) }
                if (a?.trial == true) AccountRow(s.trial, s.yes)
                a?.server?.let { AccountRow(s.server, it) }
                if (st.failed) Text(s.offline, color = Ux.Muted, fontSize = 15.sp)
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
            Text(s.title, color = Ux.Text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(s.sub, color = Ux.Text2, fontSize = 18.sp)
            IptvAccountCard(st, s)
            PillButton(if (st.loading) s.loading else s.refresh, onClick = { vm.refresh() })
        }
    }
}
