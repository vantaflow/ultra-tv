package com.ultratv.tv.nativeapp.data.recording

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.getSystemService
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.nas.Nas
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** Règles pures de l'emplacement des enregistrements (testables sans Android). */
object RecordingLocation {
    const val INTERNAL = "internal"
    const val DEVICE = "device"
    private const val USB = "usb:"
    private const val NAS = "nas:"

    fun usb(uuid: String) = "$USB$uuid"
    fun nas(providerId: Long) = "$NAS$providerId"
    fun usbUuid(key: String): String? = key.takeIf { it.startsWith(USB) }?.removePrefix(USB)?.takeIf { it.isNotBlank() }
    fun nasId(key: String): Long? = key.takeIf { it.startsWith(NAS) }?.removePrefix(NAS)?.toLongOrNull()

    /** Volume amovible monté (clé USB, disque) : identifiant stable du volume et dossier propre à l'appli. */
    data class Volume(val uuid: String, val dir: File, val label: String)

    /**
     * Dossier où ÉCRIRE l'enregistrement : clé USB choisie si elle est branchée, sinon stockage de l'appareil, sinon mémoire
     * interne. Partage NAS : écriture locale (stockage de l'appareil) puis copie sur le partage à la fin.
     * [fallback] vrai quand l'emplacement choisi n'est pas disponible (clé débranchée).
     */
    fun resolve(key: String, volumes: List<Volume>, device: File?, internal: File): Pair<File, Boolean> {
        val local = device ?: internal
        return when {
            key == INTERNAL -> internal to false
            usbUuid(key) != null -> volumes.firstOrNull { it.uuid == usbUuid(key) }?.let { it.dir to false } ?: (local to true)
            else -> local to false
        }
    }

    /** Dossier du partage NAS qui reçoit les enregistrements. */
    val NAS_DIR = listOf("Ultra TV")

    /** Nom de fichier sûr pour le partage (caractères interdits par SMB remplacés). */
    fun nasFileName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "recording.ts" }
}

/**
 * Emplacement des enregistrements choisi dans les réglages : mémoire interne, stockage de l'appareil, clé USB ou disque
 * branché, ou partage NAS déjà ajouté comme source. Les volumes amovibles sont ceux qu'Android expose à l'appli
 * (`getExternalFilesDirs`) : aucune permission de stockage nécessaire.
 */
@Singleton
class RecordingStorage @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val providers: ProviderDao,
) {
    private val sp = ctx.getSharedPreferences("recordings", Context.MODE_PRIVATE)
    private val _location = MutableStateFlow(sp.getString(KEY, RecordingLocation.DEVICE) ?: RecordingLocation.DEVICE)
    val location: StateFlow<String> = _location.asStateFlow()

    fun setLocation(key: String) { sp.edit().putString(KEY, key).apply(); _location.value = key }

    enum class Kind { INTERNAL, DEVICE, USB, NAS }
    data class Option(val key: String, val kind: Kind, val name: String)

    fun usbVolumes(): List<RecordingLocation.Volume> {
        val sm = ctx.getSystemService<StorageManager>()
        return ctx.getExternalFilesDirs(null).drop(1).filterNotNull().mapNotNull { dir ->
            if (Environment.getExternalStorageState(dir) != Environment.MEDIA_MOUNTED) return@mapNotNull null
            val v = runCatching { sm?.getStorageVolume(dir) }.getOrNull() ?: return@mapNotNull null
            if (!v.isRemovable) return@mapNotNull null
            RecordingLocation.Volume(v.uuid ?: dir.absolutePath, dir, v.getDescription(ctx) ?: "USB")
        }
    }

    /** Choix proposés : interne, appareil (si présent), volumes amovibles branchés, partages NAS ajoutés. */
    suspend fun options(): List<Option> = buildList {
        add(Option(RecordingLocation.INTERNAL, Kind.INTERNAL, ""))
        if (ctx.getExternalFilesDir(null) != null) add(Option(RecordingLocation.DEVICE, Kind.DEVICE, ""))
        usbVolumes().forEach { add(Option(RecordingLocation.usb(it.uuid), Kind.USB, it.label)) }
        providers.observeAll().first().filter { Nas.isNasKind(it.kind) }.forEach { add(Option(RecordingLocation.nas(it.id), Kind.NAS, it.name)) }
    }

    /** Dossier local d'écriture (créé au besoin). */
    fun localDir(): File {
        val (base, _) = RecordingLocation.resolve(_location.value, usbVolumes(), ctx.getExternalFilesDir(null), ctx.filesDir)
        return File(base, "recordings").apply { mkdirs() }
    }

    /** Partage NAS de destination, ou null si l'emplacement est local. */
    fun nasTarget(): Long? = RecordingLocation.nasId(_location.value)

    fun freeBytes(): Long = runCatching { android.os.StatFs(localDir().path).availableBytes }.getOrDefault(0L)

    private companion object { const val KEY = "location" }
}
