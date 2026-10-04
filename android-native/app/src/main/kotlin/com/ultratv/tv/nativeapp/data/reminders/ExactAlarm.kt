package com.ultratv.tv.nativeapp.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build

/**
 * Alarme « exacte » partagée (rappels, enregistrements programmés) : exacte quand la permission
 * SCHEDULE_EXACT_ALARM est accordée (ou inutile avant Android 12), sinon repli inexact —
 * l'alarme part alors avec un léger retard plutôt que de ne pas partir.
 * Renvoie true si l'alarme est exacte.
 */
object ExactAlarm {
    fun canExact(am: AlarmManager): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    fun set(am: AlarmManager, triggerAtMs: Long, pi: PendingIntent): Boolean {
        val exact = canExact(am)
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        return exact
    }
}
