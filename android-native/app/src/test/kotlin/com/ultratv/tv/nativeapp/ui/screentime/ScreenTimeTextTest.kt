package com.ultratv.tv.nativeapp.ui.screentime

import com.ultratv.tv.nativeapp.data.screentime.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTimeTextTest {
    @Test fun hhmm_et_fin_de_journee() {
        assertEquals("00:00", ScreenTimeText.hhmm(0))
        assertEquals("08:30", ScreenTimeText.hhmm(510))
        assertEquals("24:00", ScreenTimeText.hhmm(1440))
    }

    @Test fun plages_jointes() {
        assertEquals("08:00–12:00, 14:00–20:30", ScreenTimeText.windows(listOf(TimeWindow(480, 720), TimeWindow(840, 1230))))
    }
}
