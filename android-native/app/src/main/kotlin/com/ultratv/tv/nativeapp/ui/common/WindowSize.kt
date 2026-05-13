package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Three-bucket form-factor used by the adaptive nav / layout switches. */
enum class FormFactor { Compact, Medium, Expanded }

/**
 * Reads the current width and maps it to Material 3 window-size buckets.
 *
 *  - Compact  <  600 dp → phone portrait or split-screen — bottom-bar nav
 *  - Medium   <  840 dp → phone landscape / tablet portrait — top-bar or nav rail
 *  - Expanded ≥  840 dp → tablet landscape / TV — left sidebar
 *
 * We read the configuration directly rather than pulling in the
 * material3-window-size-class artifact: one less dependency, the bucketing
 * is trivial.
 */
@Composable
fun rememberFormFactor(): FormFactor {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w < 600 -> FormFactor.Compact
        w < 840 -> FormFactor.Medium
        else -> FormFactor.Expanded
    }
}
