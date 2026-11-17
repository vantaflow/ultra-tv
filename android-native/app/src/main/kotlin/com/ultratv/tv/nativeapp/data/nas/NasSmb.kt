package com.ultratv.tv.nativeapp.data.nas

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.auth.NtlmAuthenticator
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accès SMB 2/3 (bibliothèque smbj, Apache-2.0) : listage d'un dossier et lecture d'un fichier par plages. Aucune logique de
 * catalogue ici (voir [Nas]) : seulement la connexion, gardée par source et rouverte si elle tombe.
 *
 * SMB 1 n'est pas pris en charge (protocole abandonné, désactivé par défaut sur les NAS récents). Authentification NTLM
 * (identifiant, mot de passe, domaine éventuel « DOMAINE\identifiant ») ou invité si l'identifiant est vide.
 * Identifiants jamais journalisés : les exceptions remontées ne portent ni hôte ni identifiant.
 */
@Singleton
class NasSmb @Inject constructor() {
    private class Held(val key: String, val client: SMBClient, val connection: Connection, val session: Session, val share: DiskShare) {
        fun close() { runCatching { share.close() }; runCatching { session.close() }; runCatching { connection.close() }; runCatching { client.close() } }
    }

    private val held = HashMap<Long, Held>()

    // smbj ne s'appuie pas sur le fournisseur « BC » d'Android (API légère de Bouncy Castle) ; NTLM seul : Kerberos / SPNEGO
    // utilisent org.ietf.jgss, absent d'Android.
    private fun config(): SmbConfig = SmbConfig.builder()
        .withTimeout(20, TimeUnit.SECONDS)
        .withSoTimeout(30, TimeUnit.SECONDS)
        .withAuthenticators(NtlmAuthenticator.Factory())
        .withMultiProtocolNegotiate(true)
        .withBufferSize(1 shl 20)
        .build()

    private fun open(a: Nas.Address, user: String, pass: String): Held {
        val client = SMBClient(config())
        var conn: Connection? = null
        try {
            conn = client.connect(a.host, a.port)
            val u = user.trim()
            val ctx = when {
                u.isEmpty() -> AuthenticationContext.guest()
                u.contains('\\') -> AuthenticationContext(u.substringAfter('\\'), pass.toCharArray(), u.substringBefore('\\'))
                u.contains('@') -> AuthenticationContext(u.substringBefore('@'), pass.toCharArray(), u.substringAfter('@'))
                else -> AuthenticationContext(u, pass.toCharArray(), "")
            }
            val session = conn.authenticate(ctx)
            val share = session.connectShare(a.share) as? DiskShare ?: throw IOException("nas: not a disk share")
            return Held("${a.url()}|$user|${pass.hashCode()}", client, conn, session, share)
        } catch (e: IOException) {
            runCatching { conn?.close() }; runCatching { client.close() }
            throw IOException("nas: connect failed")
        } catch (e: RuntimeException) {
            // SMBRuntimeException : refus d'authentification, partage introuvable, protocole non pris en charge…
            runCatching { conn?.close() }; runCatching { client.close() }
            throw IOException("nas: ${e.javaClass.simpleName}")
        }
    }

    @Synchronized
    private fun connect(p: ProviderEntity): Pair<Held, Nas.Address> {
        val a = Nas.parseAddress(p.baseUrl) ?: throw IOException("nas: bad address")
        val key = "${a.url()}|${p.username}|${p.password.hashCode()}"
        held[p.id]?.let { if (it.key == key && it.connection.isConnected) return it to a else { it.close(); held.remove(p.id) } }
        val h = open(a, p.username, p.password)
        held[p.id] = h
        return h to a
    }

    @Synchronized
    private fun drop(providerId: Long) { held.remove(providerId)?.close() }

    @Synchronized
    fun closeAll() { held.values.forEach { it.close() }; held.clear() }

    private fun smbPath(a: Nas.Address, path: List<String>): String = (a.path + path).joinToString("\\")

    private fun <T> withShare(p: ProviderEntity, block: (DiskShare, Nas.Address) -> T): T {
        try {
            val (h, a) = connect(p)
            return block(h.share, a)
        } catch (e: IOException) {
            drop(p.id)
            // Une seule nouvelle tentative : connexion tombée en veille.
            val (h, a) = connect(p)
            return try { block(h.share, a) } catch (e2: RuntimeException) { drop(p.id); throw IOException("nas: ${e2.javaClass.simpleName}") }
        } catch (e: RuntimeException) {
            drop(p.id)
            throw IOException("nas: ${e.javaClass.simpleName}")
        }
    }

    /** Vérifie une adresse et des identifiants (ajout d'une source) sans rien enregistrer ; lève [IOException] en cas d'échec. */
    fun test(a: Nas.Address, user: String, pass: String) {
        val h = open(a, user, pass)
        try {
            if (a.path.isNotEmpty() && !h.share.folderExists(a.path.joinToString("\\"))) throw IOException("nas: folder not found")
            h.share.list(a.path.joinToString("\\"))
        } catch (e: RuntimeException) {
            throw IOException("nas: ${e.javaClass.simpleName}")
        } finally {
            h.close()
        }
    }

    /** Listeur du dossier de départ de la source [p]. */
    fun lister(p: ProviderEntity): Nas.Lister = Nas.Lister { path ->
        withShare(p) { share, a ->
            share.list(smbPath(a, path)).mapNotNull { i ->
                val name = i.fileName
                if (name == "." || name == "..") return@mapNotNull null
                val dir = (i.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                Nas.Entry(name, dir, i.endOfFile, runCatching { i.lastWriteTime.toEpochMillis() / 1000 }.getOrDefault(0L))
            }
        }
    }

    /**
     * Copie [src] sous [dir]/[name] dans le dossier de départ de la source [p] (dossiers créés au besoin, fichier existant
     * remplacé). Retourne le chemin relatif écrit. Lève [IOException] en cas d'échec.
     */
    fun upload(p: ProviderEntity, dir: List<String>, name: String, src: java.io.File): List<String> = withShare(p) { share, a ->
        val acc = mutableListOf<String>()
        for (d in dir) {
            acc += d
            val sp = smbPath(a, acc)
            if (!share.folderExists(sp)) share.mkdir(sp)
        }
        val target = dir + name
        val f = share.openFile(
            smbPath(a, target), EnumSet.of(AccessMask.GENERIC_WRITE), null, EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OVERWRITE_IF, null,
        )
        try { f.outputStream.use { out -> src.inputStream().use { it.copyTo(out, 1 shl 16) } } } finally { runCatching { f.close() } }
        target
    }

    /** Supprime un fichier de la source [p] (enregistrement copié sur le partage). */
    fun delete(p: ProviderEntity, path: List<String>) = withShare(p) { share, a -> share.rm(smbPath(a, path)) }

    /** Ouvre un fichier de la source [p] en lecture (à fermer par l'appelant). */
    fun openFile(p: ProviderEntity, path: List<String>): NasFile = withShare(p) { share, a ->
        val f = share.openFile(
            smbPath(a, path), EnumSet.of(AccessMask.GENERIC_READ), null, EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_DELETE),
            SMB2CreateDisposition.FILE_OPEN, null,
        )
        val size = f.getFileInformation(FileStandardInformation::class.java).endOfFile
        object : NasFile {
            override val size: Long = size
            override fun read(offset: Long, buf: ByteArray, off: Int, len: Int): Int = f.read(buf, offset, off, len)
            override fun close() { runCatching { f.close() } }
        }
    }
}
