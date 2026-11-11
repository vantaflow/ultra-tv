package com.ultratv.tv.nativeapp.ui.player.timeshift

/**
 * « −mm:ss » ou « −h:mm:ss ». Le badge « EN DIFFÉRÉ », la barre et les sauts −30 / +30 du différé
 * sont désormais ceux de l'OSD du Direct (ui/player/osd).
 */
fun behindLabel(sec: Int): String {
    val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
    return "−" + if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
