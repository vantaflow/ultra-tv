package com.ultratv.tv.nativeapp.data.tv

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.TextPaint
import android.text.TextUtils
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Image composée par l'appli pour l'écran d'accueil Android TV / Google TV : le lanceur remplit (et rogne) la vignette
 * au format annoncé, un logo de chaîne donné tel quel y apparaissait agrandi et coupé. L'image publiée est donc déjà au
 * bon format, la source entière centrée dedans.
 */
sealed interface TvArtRequest {
    val title: String

    /** Chaîne du direct : vignette 16:9, logo entier centré ; sans logo (ou introuvable), le nom de la chaîne. */
    data class ChannelLogo(val logo: String?, override val title: String) : TvArtRequest

    /** Film ou série sans affiche : son fond (paysage) entier dans une vignette 2:3, le titre dessous. */
    data class Backdrop(val backdrop: String, override val title: String) : TvArtRequest
}

/** Règles pures des images composées (testables sans Android). */
object TvArtPlan {
    /** Version de la composition : la changer régénère les images et republie les chaînes (signatures). */
    const val VERSION = 1

    const val DIR = "tv_home_art"
    const val META_DIR = "tv_home_art_meta"
    const val PATH = "art"

    /** Vignette 16:9 d'une chaîne. */
    const val CHANNEL_W = 640
    const val CHANNEL_H = 360
    /** Vignette 2:3 d'un film ou d'une série. */
    const val POSTER_W = 400
    const val POSTER_H = 600

    /** Logo : au plus 60 % de la largeur et 70 % de la hauteur de la vignette. */
    const val LOGO_MAX_W = 0.60f
    const val LOGO_MAX_H = 0.70f
    /** Un petit logo n'est pas agrandi au-delà (il deviendrait flou). */
    const val MAX_UPSCALE = 4f

    /** Fond neutre sombre, et clair pour un logo sombre sur fond transparent (sinon invisible). */
    const val DARK = 0xFF15181E.toInt()
    const val LIGHT = 0xFFE9ECF1.toInt()

    /** Plafond de générations par fenêtre (rien de lourd d'un coup) et pause entre deux. */
    const val MAX_PER_WINDOW = 30
    const val WINDOW_MS = 60_000L
    const val GAP_MS = 120L
    /** Image de repli (logo introuvable) : réessayée après ce délai. */
    const val RETRY_FALLBACK_MS = 6L * 3_600_000
    /** Nombre d'images gardées sur le disque (les plus anciennes partent). */
    const val MAX_FILES = 400

    private val NAME = Regex("^[0-9a-f]{64}\\.jpg$")

    /** Rectangle entier (pixels). */
    data class Box(val left: Int, val top: Int, val width: Int, val height: Int) {
        val right get() = left + width
        val bottom get() = top + height
    }

    fun canvas(req: TvArtRequest): Pair<Int, Int> = when (req) {
        is TvArtRequest.ChannelLogo -> CHANNEL_W to CHANNEL_H
        is TvArtRequest.Backdrop -> POSTER_W to POSTER_H
    }

    /** Zone où l'image source est inscrite (centrée dedans). */
    fun area(req: TvArtRequest): Box = when (req) {
        is TvArtRequest.ChannelLogo -> {
            val w = (CHANNEL_W * LOGO_MAX_W).toInt()
            val h = (CHANNEL_H * LOGO_MAX_H).toInt()
            Box((CHANNEL_W - w) / 2, (CHANNEL_H - h) / 2, w, h)
        }
        // Fond 16:9 dans le haut de la vignette 2:3, le titre en dessous.
        is TvArtRequest.Backdrop -> Box((POSTER_W * 0.04f).toInt(), (POSTER_H * 0.12f).toInt(), (POSTER_W * 0.92f).toInt(), (POSTER_H * 0.58f).toInt())
    }

    /** Source [srcW]×[srcH] mise à l'échelle « fit » (proportions gardées, jamais rognée) et centrée dans [area]. */
    fun fit(srcW: Int, srcH: Int, area: Box, maxUpscale: Float = MAX_UPSCALE): Box {
        if (srcW <= 0 || srcH <= 0) return Box(area.left + area.width / 2, area.top + area.height / 2, 0, 0)
        val scale = minOf(area.width.toFloat() / srcW, area.height.toFloat() / srcH, maxUpscale)
        val w = Math.round(srcW * scale).coerceIn(1, area.width)
        val h = Math.round(srcH * scale).coerceIn(1, area.height)
        return Box(area.left + (area.width - w) / 2, area.top + (area.height - h) / 2, w, h)
    }

    /**
     * Fond d'un logo à partir de ses pixels (ARGB, échantillon réduit) : couleur dominante très atténuée vers le sombre ;
     * logo sombre sur fond transparent : fond clair ; rien d'opaque : sombre neutre.
     */
    fun background(pixels: IntArray): Int {
        var n = 0
        var transparent = 0
        var r = 0L
        var g = 0L
        var b = 0L
        for (p in pixels) {
            if ((p ushr 24) < 128) { transparent++; continue }
            n++
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
        }
        if (n == 0) return DARK
        val ar = (r / n).toInt()
        val ag = (g / n).toInt()
        val ab = (b / n).toInt()
        val hasAlpha = transparent > pixels.size / 10
        if (hasAlpha && luminance(ar, ag, ab) < 0.28) return LIGHT
        return mix(0xFF000000.toInt() or (ar shl 16) or (ag shl 8) or ab, DARK, 0.82f)
    }

    fun luminance(r: Int, g: Int, b: Int): Double = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0

    fun luminance(color: Int): Double = luminance((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF)

    /** Couleur du texte lisible sur [bg]. */
    fun textColor(bg: Int): Int = if (luminance(bg) > 0.5) 0xFF1B1E24.toInt() else 0xFFF2F4F7.toInt()

    /** Mélange : [t] = part de [b]. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = Math.round(((a shr s) and 0xFF) * (1 - t) + ((b shr s) and 0xFF) * t)
        return 0xFF000000.toInt() or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Empreinte de l'image (adresse de la source, ou nom si pas de logo) : jamais l'adresse elle-même dans l'URI publiée. */
    fun key(req: TvArtRequest): String {
        val raw = when (req) {
            is TvArtRequest.ChannelLogo -> req.logo?.trim()?.takeIf { it.isNotEmpty() }?.let { "logo|$it" } ?: "name|${req.title.trim()}"
            is TvArtRequest.Backdrop -> "backdrop|${req.backdrop.trim()}|${req.title.trim()}"
        }
        return sha256("v$VERSION|$raw")
    }

    fun fileName(key: String) = "$key.jpg"

    /** URI servie au lanceur : `content://<appli>.tvart/art/<empreinte>.jpg`, sans aucun identifiant de source. */
    fun uri(authority: String, key: String) = "content://$authority/$PATH/${fileName(key)}"

    /** Empreinte d'un nom de fichier demandé, ou null s'il n'a pas exactement la forme attendue (aucun autre fichier servi). */
    fun keyOf(fileName: String?): String? = fileName?.takeIf { NAME.matches(it) }?.removeSuffix(".jpg")

    /** Description gardée à côté de l'image pour la régénérer à la demande (cache vidé). */
    fun encode(req: TvArtRequest): String {
        fun clean(s: String?) = s.orEmpty().replace('\n', ' ').replace('\r', ' ')
        return when (req) {
            is TvArtRequest.ChannelLogo -> "logo\n${clean(req.logo)}\n${clean(req.title)}"
            is TvArtRequest.Backdrop -> "backdrop\n${clean(req.backdrop)}\n${clean(req.title)}"
        }
    }

    fun decode(text: String): TvArtRequest? {
        val parts = text.split('\n')
        if (parts.size < 3) return null
        return when (parts[0]) {
            "logo" -> TvArtRequest.ChannelLogo(parts[1].takeIf { it.isNotBlank() }, parts[2])
            "backdrop" -> parts[1].takeIf { it.isNotBlank() }?.let { TvArtRequest.Backdrop(it, parts[2]) }
            else -> null
        }
    }

    /** Attente avant la prochaine génération : plafond [max] par [windowMs] (horodatages des précédentes). */
    fun waitMs(recent: List<Long>, now: Long, max: Int = MAX_PER_WINDOW, windowMs: Long = WINDOW_MS): Long {
        val inWindow = recent.filter { now - it < windowMs }.sorted()
        if (inWindow.size < max) return 0L
        return (inWindow[inWindow.size - max] + windowMs - now).coerceAtLeast(0L)
    }

    /** Facteur de sous-échantillonnage au décodage : plus grand côté ramené sous [maxSide]. */
    fun sampleSize(w: Int, h: Int, maxSide: Int = 1024): Int {
        var s = 1
        while (maxOf(w, h) / (s * 2) >= maxSide) s *= 2
        return s
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Dessin des vignettes (Canvas). */
object TvArtComposer {
    fun compose(req: TvArtRequest, src: Bitmap?): Bitmap {
        val (w, h) = TvArtPlan.canvas(req)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (src == null || src.width <= 0 || src.height <= 0) {
            c.drawColor(TvArtPlan.DARK)
            drawTitle(c, req.title, w / 2f, h / 2f, w * 0.86f, if (req is TvArtRequest.ChannelLogo) 46f else 40f, TvArtPlan.textColor(TvArtPlan.DARK))
            return out
        }
        val bg = if (req is TvArtRequest.ChannelLogo) TvArtPlan.background(sample(src)) else TvArtPlan.DARK
        c.drawColor(bg)
        val box = TvArtPlan.fit(src.width, src.height, TvArtPlan.area(req))
        c.drawBitmap(src, null, RectF(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat()), paint)
        if (req is TvArtRequest.Backdrop) drawTitle(c, req.title, w / 2f, h * 0.84f, w * 0.88f, 30f, TvArtPlan.textColor(bg))
        return out
    }

    /** Échantillon réduit des pixels (couleur dominante). */
    private fun sample(src: Bitmap): IntArray {
        val small = Bitmap.createScaledBitmap(src, 24, 24, true)
        val px = IntArray(24 * 24)
        small.getPixels(px, 0, 24, 0, 0, 24, 24)
        if (small !== src) small.recycle()
        return px
    }

    /** Une ligne centrée, réduite jusqu'à 60 % puis tronquée (…). */
    private fun drawTitle(c: Canvas, title: String, cx: Float, cy: Float, maxWidth: Float, size: Float, color: Int) {
        val text = title.trim().ifEmpty { return }
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        while (p.measureText(text) > maxWidth && p.textSize > size * 0.6f) p.textSize -= 2f
        val line = TextUtils.ellipsize(text, p, maxWidth, TextUtils.TruncateAt.END).toString()
        val fm = p.fontMetrics
        c.drawText(line, cx, cy - (fm.ascent + fm.descent) / 2f, p)
    }
}

/**
 * Images composées sur le disque (cache de l'appli, par empreinte de l'adresse source), générées à la demande de la
 * synchro des chaînes d'accueil : une à la fois, plafonnées, jamais dans les deux minutes qui suivent le démarrage de la
 * box ; servies au lanceur par [TvArtProvider]. Vidées avec le cache d'images (« Tout nettoyer ») : la description gardée
 * à côté permet de les refaire quand le lanceur les redemande.
 */
@Singleton
class TvArtStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val http: dagger.Lazy<OkHttpClient>,
) {
    val authority: String = "${ctx.packageName}.tvart"
    private val dir get() = File(ctx.cacheDir, TvArtPlan.DIR)
    private val metaDir get() = File(ctx.filesDir, TvArtPlan.META_DIR)
    private val lock = Mutex()
    private val recent = ArrayList<Long>()
    @Volatile private var settled = false
    /** Attente après le démarrage de la box avant la première génération (tests : 0). */
    internal var settleMs: Long = TvChannelPlan.BOOT_SETTLE_MS

    /** Client sans l'intercepteur de mesure du débit (comme les images de l'interface). */
    private val client by lazy {
        http.get().newBuilder().apply { networkInterceptors().clear() }
            .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
    }

    private fun file(key: String) = File(dir, TvArtPlan.fileName(key))
    private fun marker(key: String) = File(dir, "$key.fallback")
    private fun meta(key: String) = File(metaDir, key)

    /** URI content:// de l'image (générée si besoin). */
    suspend fun uriFor(req: TvArtRequest): String = withContext(Dispatchers.IO) {
        val key = TvArtPlan.key(req)
        if (needsWork(key)) lock.withLock {
            if (needsWork(key)) {
                if (!settled) { delay(TvChannelPlan.bootWaitMs(android.os.SystemClock.elapsedRealtime(), settleMs)); settled = true }
                val wait = synchronized(recent) { TvArtPlan.waitMs(recent, System.currentTimeMillis()) }
                delay(maxOf(wait, TvArtPlan.GAP_MS))
                generate(req, key)
                synchronized(recent) { recent += System.currentTimeMillis(); recent.removeAll { System.currentTimeMillis() - it >= TvArtPlan.WINDOW_MS } }
            }
        }
        if (!meta(key).exists()) runCatching { writeMeta(key, req) }
        TvArtPlan.uri(authority, key)
    }

    private fun needsWork(key: String): Boolean {
        val f = file(key)
        if (!f.exists()) return true
        val m = marker(key)
        return m.exists() && System.currentTimeMillis() - m.lastModified() > TvArtPlan.RETRY_FALLBACK_MS
    }

    /** Fichier demandé par le lanceur ; refait depuis sa description s'il a été nettoyé. Null : inconnu. */
    fun fileFor(key: String): File? {
        val f = file(key)
        if (f.exists()) return f
        val req = runCatching { TvArtPlan.decode(meta(key).readText()) }.getOrNull() ?: return null
        runBlocking(Dispatchers.IO) { withTimeoutOrNull(12_000) { lock.withLock { if (!f.exists()) generate(req, key) } } }
        return f.takeIf { it.exists() }
    }

    /** Vide les images (pas leurs descriptions, minuscules). Octets libérés. */
    fun clear(): Long {
        val files = dir.listFiles().orEmpty()
        val size = files.sumOf { it.length() }
        files.forEach { it.delete() }
        return size
    }

    private fun generate(req: TvArtRequest, key: String) {
        val url = when (req) {
            is TvArtRequest.ChannelLogo -> req.logo?.takeIf { it.isNotBlank() }
            is TvArtRequest.Backdrop -> req.backdrop
        }
        val src = url?.let { runCatching { load(it) }.getOrNull() }
        val bmp = TvArtComposer.compose(req, src)
        src?.recycle()
        dir.mkdirs()
        val tmp = File(dir, "$key.tmp")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        val f = file(key)
        if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
        // Source introuvable : image de repli (nom), réessayée plus tard sous la même URI.
        if (url != null && src == null) marker(key).writeText("") else marker(key).delete()
        writeMeta(key, req)
        prune()
    }

    private fun writeMeta(key: String, req: TvArtRequest) {
        metaDir.mkdirs()
        meta(key).writeText(TvArtPlan.encode(req))
    }

    /** Image source décodée (sous-échantillonnée), ou null. Adresses http(s) et fichiers locaux seulement. */
    private fun load(url: String): Bitmap? {
        val bytes = when {
            url.startsWith("http://", true) || url.startsWith("https://", true) ->
                client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) return null
                    val body = r.body
                    if (body.contentLength() > MAX_BYTES) return null
                    body.byteStream().use { readCapped(it) } ?: return null
                }
            url.startsWith("file://", true) -> File(Uri.parse(url).path ?: return null).takeIf { it.length() in 1..MAX_BYTES }?.readBytes() ?: return null
            else -> return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = TvArtPlan.sampleSize(bounds.outWidth, bounds.outHeight); inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun readCapped(input: java.io.InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Au plus [TvArtPlan.MAX_FILES] images : les plus anciennes partent (et leurs descriptions). */
    private fun prune() {
        val images = dir.listFiles { f -> f.name.endsWith(".jpg") }.orEmpty()
        if (images.size <= TvArtPlan.MAX_FILES) return
        images.sortedBy { it.lastModified() }.take(images.size - TvArtPlan.MAX_FILES).forEach { f ->
            val key = f.name.removeSuffix(".jpg")
            f.delete(); marker(key).delete(); meta(key).delete()
        }
        val metas = metaDir.listFiles().orEmpty()
        if (metas.size > TvArtPlan.MAX_FILES * 2) metas.sortedBy { it.lastModified() }.take(metas.size - TvArtPlan.MAX_FILES * 2).forEach { it.delete() }
    }

    private companion object {
        const val MAX_BYTES = 4L * 1024 * 1024
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TvArtEntryPoint {
    fun tvArtStore(): TvArtStore
}

/**
 * Sert au lanceur Android TV / Google TV les images composées, en LECTURE SEULE : seuls des noms `<empreinte>.jpg` sous
 * `art/` sont acceptés, rien d'autre n'est lisible. Exporté sans permission : une autorisation d'URI accordée au lanceur
 * (grantUriPermission) ne survit pas au redémarrage de la box, et l'accueil resterait sans image.
 */
class TvArtProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun store(): TvArtStore =
        EntryPointAccessors.fromApplication(context!!.applicationContext, TvArtEntryPoint::class.java).tvArtStore()

    private fun keyOf(uri: Uri): String? =
        uri.pathSegments.takeIf { it.size == 2 && it[0] == TvArtPlan.PATH }?.let { TvArtPlan.keyOf(it[1]) }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read-only")
        val key = keyOf(uri) ?: throw FileNotFoundException(uri.toString())
        val f = store().fileFor(key) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = if (keyOf(uri) != null) "image/jpeg" else null

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val key = keyOf(uri) ?: return null
        val f = store().fileFor(key) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols, 1).apply {
            addRow(cols.map { when (it) { OpenableColumns.DISPLAY_NAME -> f.name; OpenableColumns.SIZE -> f.length(); else -> null } })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
