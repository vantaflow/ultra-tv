package com.ultratv.tv.nativeapp.data.downloads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.coroutineContext

/**
 * Téléchargement HTTP d'UN fichier, avec reprise (en-tête `Range`), sans aucune dépendance Android (testé avec
 * MockWebServer) :
 *  - écrit dans `<final>.part`, puis renomme atomiquement en `<final>` une fois la taille attendue atteinte ;
 *  - un `.part` existant est repris là où il s'était arrêté (206 + Content-Range cohérent) ; si le serveur ignore la
 *    plage (200), on repart de zéro ; 416 sur un partiel déjà complet = terminé ;
 *  - erreurs passagères (réseau, 5xx, 408, 429, réponse tronquée) : nouvelles tentatives avec attente exponentielle ;
 *    erreurs définitives (4xx, plus de place, renommage impossible) : échec immédiat ;
 *  - [shouldStop] (pause / annulation demandée par l'utilisateur) est consulté au plus toutes les [checkEveryMs] :
 *    le partiel est conservé et [Outcome.Stopped] rendu.
 * Aucun message d'exception n'est remonté (il peut contenir l'URL, donc les identifiants) : seulement un code.
 */
class HttpRangeDownloader(
    private val client: OkHttpClient,
    private val freeBytes: (File) -> Long,
    private val userAgent: String = "UltraTV/1.0 (Android TV)",
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val now: () -> Long = System::currentTimeMillis,
    private val checkEveryMs: Long = 1_000,
) {
    sealed interface Outcome {
        data class Completed(val bytes: Long) : Outcome
        data object Stopped : Outcome
        data class Failed(val error: String, val retryable: Boolean) : Outcome
    }

    suspend fun download(
        url: String,
        finalFile: File,
        maxAttempts: Int = 4,
        baseBackoffMs: Long = 2_000,
        onProgress: suspend (done: Long, total: Long) -> Unit = { _, _ -> },
        shouldStop: suspend () -> Boolean = { false },
    ): Outcome {
        var attempt = 0
        while (true) {
            val r = attemptOnce(url, finalFile, onProgress, shouldStop)
            if (r !is Outcome.Failed || !r.retryable || attempt >= maxAttempts - 1) return r
            sleep(baseBackoffMs shl attempt.coerceAtMost(6))
            if (shouldStop()) return Outcome.Stopped
            attempt++
        }
    }

    private suspend fun attemptOnce(
        url: String,
        finalFile: File,
        onProgress: suspend (Long, Long) -> Unit,
        shouldStop: suspend () -> Boolean,
    ): Outcome = withContext(Dispatchers.IO) {
        val part = DownloadPlan.partFile(finalFile)
        val dir = finalFile.parentFile ?: return@withContext Outcome.Failed(DownloadError.STORAGE, false)
        if (!dir.exists() && !dir.mkdirs()) return@withContext Outcome.Failed(DownloadError.STORAGE, false)
        val have = if (part.exists()) part.length() else 0L
        val req = Request.Builder().url(url).header("User-Agent", userAgent).apply {
            if (have > 0) header("Range", "bytes=$have-")
        }.build()
        try {
            client.newCall(req).execute().use { resp ->
                val code = resp.code
                if (code == 416 && have > 0) {
                    // Plage hors fichier : soit le partiel est déjà complet (Content-Range: bytes */TOTAL), soit il est
                    // plus long que le fichier distant (fichier remplacé) — on repart de zéro à la tentative suivante.
                    val total = parseContentRange(resp.header("Content-Range"))?.total
                    if (total != null && total == have) return@withContext finalize(part, finalFile, have)
                    part.delete()
                    return@withContext Outcome.Failed(DownloadError.HTTP, retryable = true)
                }
                if (!resp.isSuccessful) {
                    return@withContext Outcome.Failed(DownloadError.HTTP, retryable = code >= 500 || code == 408 || code == 429)
                }
                val body = resp.body ?: return@withContext Outcome.Failed(DownloadError.NETWORK, true)
                val append: Boolean
                val total: Long
                if (code == 206 && have > 0) {
                    val cr = parseContentRange(resp.header("Content-Range"))
                    if (cr == null || cr.start != have) {
                        // Réponse partielle incohérente avec ce qu'on a : on ne mélange jamais deux morceaux.
                        part.delete()
                        return@withContext Outcome.Failed(DownloadError.HTTP, retryable = true)
                    }
                    append = true
                    total = cr.total ?: body.contentLength().let { if (it >= 0) have + it else -1L }
                } else {
                    // 200 : le serveur ignore la plage (ou premier essai) → tout depuis le début.
                    append = false
                    total = body.contentLength()
                }
                var done = if (append) have else 0L
                val remaining = if (total > 0) total - done else -1L
                if (!DownloadPlan.hasRoom(freeBytes(dir), remaining)) return@withContext Outcome.Failed(DownloadError.NO_SPACE, false)
                onProgress(done, total)
                var lastCheck = now()
                FileOutputStream(part, append).use { out ->
                    val input = body.byteStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val t = now()
                        if (t - lastCheck >= checkEveryMs) {
                            lastCheck = t
                            onProgress(done, total)
                            if (shouldStop()) { out.flush(); return@withContext Outcome.Stopped }
                        }
                    }
                    out.flush()
                    runCatching { out.fd.sync() }
                }
                val len = part.length()
                // Réponse tronquée (connexion coupée sans erreur) : passager, la tentative suivante reprendra au Range.
                if (total > 0 && len < total) return@withContext Outcome.Failed(DownloadError.NETWORK, true)
                if (total > 0 && len > total) { part.delete(); return@withContext Outcome.Failed(DownloadError.HTTP, true) }
                onProgress(len, if (total > 0) total else len)
                finalize(part, finalFile, len)
            }
        } catch (e: IOException) {
            // Volume plein en cours de route : définitif (l'utilisateur doit libérer de la place).
            if (!DownloadPlan.hasRoom(freeBytes(dir), 1)) Outcome.Failed(DownloadError.NO_SPACE, false)
            else Outcome.Failed(DownloadError.NETWORK, true)
        } catch (e: IllegalArgumentException) {
            Outcome.Failed(DownloadError.NOT_DOWNLOADABLE, false)
        }
    }

    /** `.part` → fichier final : renommage dans le même dossier (atomique), le final précédent éventuel est remplacé. */
    private fun finalize(part: File, finalFile: File, bytes: Long): Outcome {
        val ok = runCatching {
            Files.move(part.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            true
        }.getOrElse { (finalFile.exists() && finalFile.delete() || !finalFile.exists()) && part.renameTo(finalFile) }
        return if (ok) Outcome.Completed(bytes) else Outcome.Failed(DownloadError.STORAGE, false)
    }

    data class ContentRange(val start: Long, val total: Long?)

    companion object {
        private val RANGE = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)
        private val UNSAT = Regex("""bytes\s+\*/(\d+)""", RegexOption.IGNORE_CASE)

        /** « bytes 100-199/1000 » → (100, 1000) ; réponse 416 (« bytes », étoile, « /1000 ») → (0, 1000) ; total inconnu (étoile) → null. */
        fun parseContentRange(h: String?): ContentRange? {
            if (h == null) return null
            RANGE.find(h)?.let { m -> return ContentRange(m.groupValues[1].toLong(), m.groupValues[3].toLongOrNull()) }
            UNSAT.find(h)?.let { m -> return ContentRange(0, m.groupValues[1].toLong()) }
            return null
        }
    }
}
