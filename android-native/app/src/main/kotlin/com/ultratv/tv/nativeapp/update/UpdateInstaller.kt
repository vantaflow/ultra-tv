package com.ultratv.tv.nativeapp.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.ultratv.tv.nativeapp.RemoteLog
import java.io.File

/**
 * Installation de la mise à jour AVEC son profil de démarrage (.dm) : session PackageInstaller (base.apk + base.dm), seule
 * voie qui accepte le .dm. Android compile alors l'application d'avance dès l'installation (démarrage 1,1 s → 0,6 s sur box).
 *
 * Certaines box refusent les sessions des applications non système (« cannot automatically move to internal storage ») :
 * tout échec bascule sur l'installeur classique ([UpdateChecker.installApk], APK seul) et l'appareil est mémorisé pour
 * ne plus retenter la session.
 */
object UpdateInstaller {
    private const val TAG = "update"
    private const val PREFS = "update_installer"
    private const val KEY_NO_SESSION = "session_refused"
    internal const val ACTION_STATUS = "com.ultratv.tv.nativeapp.UPDATE_INSTALL_STATUS"
    internal const val EXTRA_APK = "apk"

    /** Vrai si la session est lancée (le résultat arrive dans [UpdateInstallReceiver]) ; faux : installer autrement. */
    fun trySession(ctx: Context, apk: File, dm: File): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NO_SESSION, false)) return false
        return runCatching {
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { s ->
                write(s, "base.apk", apk)
                write(s, "base.dm", dm)
                val intent = Intent(ctx, UpdateInstallReceiver::class.java).setAction(ACTION_STATUS).putExtra(EXTRA_APK, apk.absolutePath)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                s.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).intentSender)
            }
            RemoteLog.info(TAG, "session install committed (apk + dm)")
            true
        }.getOrElse {
            RemoteLog.warn(TAG, "session refused: ${it.javaClass.simpleName} ${it.message}")
            markRefused(ctx)
            false
        }
    }

    private fun write(s: PackageInstaller.Session, name: String, f: File) {
        f.inputStream().use { input ->
            s.openWrite(name, 0, f.length()).use { out -> input.copyTo(out); s.fsync(out) }
        }
    }

    internal fun markRefused(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_NO_SESSION, true).apply()
    }
}

/** Résultat de la session : confirmation à demander à l'utilisateur, succès, ou échec → installeur classique. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != UpdateInstaller.ACTION_STATUS) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm: Intent? =
                    if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { ctx.startActivity(it) } }
            }
            PackageInstaller.STATUS_SUCCESS -> RemoteLog.info("update", "session install succeeded")
            // Refus de l'utilisateur : on n'insiste pas (l'invite reviendra au prochain lancement).
            PackageInstaller.STATUS_FAILURE_ABORTED -> RemoteLog.info("update", "session install cancelled by user")
            else -> {
                RemoteLog.warn("update", "session install failed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                UpdateInstaller.markRefused(ctx)
                intent.getStringExtra(UpdateInstaller.EXTRA_APK)?.let { File(it) }?.takeIf { it.isFile }
                    ?.let { runCatching { UpdateChecker.installApk(ctx, it) } }
            }
        }
    }
}
