package com.ultratv.tv.nativeapp.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.ChannelGroupEntity
import com.ultratv.tv.nativeapp.data.repo.ChannelGroupRepository
import com.ultratv.tv.nativeapp.data.repo.ChannelGroups
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.Toaster
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.ChoiceRow
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.settings.AddProviderDialog
import com.ultratv.tv.nativeapp.ui.settings.FormField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Libellés des groupes de chaînes personnels, à part de [com.ultratv.tv.nativeapp.i18n.DesignStrings] (relu par le golden) :
 * même principe que [ChannelOrderStrings].
 */
class ChannelGroupStrings(val lang: AppLang) {
    private fun tx(id: Int, vararg args: Any): String = I18n.string(lang, id, *args)

    val groups get() = tx(R.string.grp_groups)
    val newGroup get() = tx(R.string.grp_new)
    val newEllipsis get() = tx(R.string.grp_new_ellipsis)
    val name get() = tx(R.string.grp_name)
    val rename get() = tx(R.string.grp_rename)
    val delete get() = tx(R.string.grp_delete)
    fun deleteSure(name: String) = tx(R.string.grp_delete_sure, name)
    val addTo get() = tx(R.string.grp_add_to)
    fun removeFrom(name: String) = tx(R.string.grp_remove_from, name)
    fun added(name: String) = tx(R.string.grp_added, name)
    fun removed(name: String) = tx(R.string.grp_removed, name)
    val empty get() = tx(R.string.grp_empty)
    val button get() = tx(R.string.grp_button)
    val menu get() = tx(R.string.grp_menu)
    val limit get() = tx(R.string.grp_limit)
    val save get() = tx(R.string.grp_save)
    val cancel get() = tx(R.string.grp_cancel)
    val move get() = tx(R.string.grp_move)
}

/** Groupes du profil courant pour une chaîne : lecture de l'appartenance, ajout / retrait, création avec la chaîne. */
@HiltViewModel
class ChannelGroupsViewModel @Inject constructor(private val repo: ChannelGroupRepository) : ViewModel() {
    suspend fun groups(pid: Long): List<ChannelGroupEntity> = repo.groups(pid)
    suspend fun membership(c: ChannelEntity): Set<Long> = repo.groupsOf(c.providerId, c.remoteId)
    suspend fun add(gid: Long, c: ChannelEntity) = repo.add(gid, c.remoteId)
    suspend fun remove(gid: Long, c: ChannelEntity) = repo.remove(gid, c.remoteId)
    suspend fun create(pid: Long, name: String, c: ChannelEntity?): Long? = repo.create(pid, name, c?.remoteId)
    suspend fun rename(gid: Long, name: String) = repo.rename(gid, name)
    suspend fun delete(gid: Long) = repo.delete(gid)
}

/** Saisie du nom d'un groupe (création ou renommage) : 40 caractères au plus, nom vide refusé. */
@Composable
fun GroupNameDialog(title: String, initial: String = "", onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    val lang = LocalDs.current.lang
    val G = remember(lang) { ChannelGroupStrings(lang) }
    var name by remember { mutableStateOf(initial) }
    AddProviderDialog(
        title = title,
        icon = com.ultratv.tv.nativeapp.ui.design.Icons.List,
        onDismiss = onDismiss,
        canSubmit = ChannelGroups.cleanName(name).isNotEmpty(),
        submitLabel = G.save,
        onSubmit = { onSubmit(ChannelGroups.cleanName(name)) },
    ) {
        FormField(G.name, name, { name = it.take(ChannelGroups.MAX_NAME) }, autoFocus = true, last = true, required = true, testTag = "field-group-name")
    }
}

/**
 * Menu « Ajouter à un groupe » d'une chaîne : une ligne par groupe (cochée = la chaîne y est ; OK ajoute ou retire), puis
 * « Nouveau groupe… » (créé avec cette chaîne). Partagé par le Direct, le Guide, la recherche et le lecteur.
 */
@Composable
fun ChannelGroupsDialog(channel: ChannelEntity, onClose: () -> Unit, vm: ChannelGroupsViewModel = hiltViewModel()) {
    val lang = LocalDs.current.lang
    val G = remember(lang) { ChannelGroupStrings(lang) }
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<ChannelGroupEntity>?>(null) }
    var member by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var naming by remember { mutableStateOf(false) }
    LaunchedEffect(channel.providerId, channel.remoteId) { member = vm.membership(channel); groups = vm.groups(channel.providerId) }
    val list = groups ?: return
    if (naming) {
        GroupNameDialog(G.newGroup, onDismiss = onClose, onSubmit = { name ->
            scope.launch {
                if (vm.create(channel.providerId, name, channel) != null) Toaster.ok(G.added(name)) else Toaster.err(G.limit)
                onClose()
            }
        })
        return
    }
    ModalFocusScope(onBack = onClose, modifier = Modifier.background(Ux.Scrim)) {
        Column(Modifier.widthIn(min = 560.design, max = 760.design).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(40.design), verticalArrangement = Arrangement.spacedBy(12.design)) {
            Text(G.addTo, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(channel.title, color = Ux.Text3, fontFamily = Sora, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.design),
                style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
            LazyColumn(Modifier.heightIn(max = 640.design), verticalArrangement = Arrangement.spacedBy(10.design)) {
                items(list.size, key = { list[it].id }) { i ->
                    val g = list[i]
                    val inIt = g.id in member
                    ChoiceRow(g.name, inIt, {
                        scope.launch {
                            if (inIt) { if (vm.remove(g.id, channel)) Toaster.ok(G.removed(g.name)) }
                            else if (vm.add(g.id, channel)) Toaster.ok(G.added(g.name))
                            member = vm.membership(channel)
                        }
                    }, heightPx = 68, fontPx = 24, bg = Ux.Surface, selectedBg = Ux.Surface2, ringWidth = 5.design)
                }
                item(key = "new") {
                    ChoiceRow(G.newEllipsis, false, { if (list.size >= ChannelGroups.MAX_GROUPS) Toaster.err(G.limit) else naming = true }, heightPx = 68, fontPx = 24, bg = Ux.Surface, ringWidth = 5.design)
                }
                item(key = "close") {
                    ChoiceRow(LocalDs.current.close, false, onClose, heightPx = 68, fontPx = 24, bg = Ux.Surface, ringWidth = 5.design)
                }
            }
        }
    }
}

/**
 * Gestion d'un groupe depuis la liste des catégories (appui long OK ou touche Menu sur le groupe) : renommer, supprimer
 * (avec confirmation), créer un autre groupe. Sur une catégorie du fournisseur : seulement « Nouveau groupe… ».
 */
@Composable
fun GroupManageDialog(
    groupId: Long?, groupName: String?, providerId: Long, onClose: () -> Unit, onDeleted: (Long) -> Unit = {}, onCreated: (Long) -> Unit = {},
    vm: ChannelGroupsViewModel = hiltViewModel(),
) {
    val lang = LocalDs.current.lang
    val G = remember(lang) { ChannelGroupStrings(lang) }
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(if (groupId == null) "new" else "menu") }
    when (step) {
        "new" -> GroupNameDialog(G.newGroup, onDismiss = onClose, onSubmit = { name ->
            scope.launch { val id = vm.create(providerId, name, null); if (id == null) Toaster.err(G.limit) else onCreated(id); onClose() }
        })
        "rename" -> GroupNameDialog(G.rename, initial = groupName.orEmpty(), onDismiss = onClose, onSubmit = { name ->
            scope.launch { if (groupId != null) vm.rename(groupId, name); onClose() }
        })
        "delete" -> com.ultratv.tv.nativeapp.ui.design.ChoiceDialog(
            G.deleteSure(groupName.orEmpty()), listOf(true to G.delete, false to G.cancel), false,
            { yes -> if (yes && groupId != null) scope.launch { vm.delete(groupId); onDeleted(groupId); onClose() } else onClose() }, onClose,
        )
        else -> com.ultratv.tv.nativeapp.ui.design.ChoiceDialog(
            groupName.orEmpty(), listOf("rename" to G.rename, "delete" to G.delete, "new" to G.newEllipsis), null,
            { step = it }, onClose,
        )
    }
}
