package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.ui.unit.dp
import com.ultratv.tv.nativeapp.ui.home.ChannelCardWidth
import com.ultratv.tv.nativeapp.ui.home.ChannelScale
import com.ultratv.tv.nativeapp.ui.home.HomePosterScale
import com.ultratv.tv.nativeapp.ui.home.PosterRingReach
import com.ultratv.tv.nativeapp.ui.home.PosterWidth
import com.ultratv.tv.nativeapp.ui.home.ResumeCardWidth
import com.ultratv.tv.nativeapp.ui.home.ResumeScale
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeMarginTest {
    @Test fun `marge des affiches - 31 unites design`() = assertEquals(31f / 2f, edgeMarginFor(HomePosterScale, PosterWidth, PosterRingReach).value, 0.01f)
    @Test fun `marge de Reprendre - 24 unites design`() = assertEquals(24f / 2f, edgeMarginFor(ResumeScale, ResumeCardWidth, PosterRingReach).value, 0.01f)
    @Test fun `marge de En direct - 35 unites design`() = assertEquals(35f / 2f, edgeMarginFor(ChannelScale, ChannelCardWidth, PosterRingReach).value, 0.01f)
    @Test fun `sans agrandissement la marge est l'anneau`() = assertEquals(5f, edgeMarginFor(1f, 200.dp, 5.dp).value, 0.01f)
}
