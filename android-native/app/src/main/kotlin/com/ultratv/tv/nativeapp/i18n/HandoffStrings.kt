package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Reprise de la lecture d'un appareil à l'autre (« Envoyer vers… » / « Reprendre sur cet appareil ? »). */
val DesignStrings.handoffTitle get() = tx(R.string.handoff_title)
fun DesignStrings.handoffBody(from: String, title: String) = tx(R.string.handoff_body, from, title)
val DesignStrings.handoffYes get() = tx(R.string.handoff_yes)
val DesignStrings.handoffNo get() = tx(R.string.handoff_no)
val DesignStrings.handoffSend get() = tx(R.string.handoff_send)
fun DesignStrings.handoffSent(device: String) = tx(R.string.handoff_sent, device)
val DesignStrings.handoffNoSource get() = tx(R.string.handoff_no_source)
val DesignStrings.handoffFailed get() = tx(R.string.handoff_failed)
val DesignStrings.handoffNotFound get() = tx(R.string.handoff_not_found)
val DesignStrings.handoffNoDevices get() = tx(R.string.handoff_no_devices)
