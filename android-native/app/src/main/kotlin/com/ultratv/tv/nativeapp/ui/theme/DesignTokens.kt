package com.ultratv.tv.nativeapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

// ─── Ultra TV — design tokens, mirrored from the design bundle (styles.css)
// All visual constants live here so screens stay consistent and a future theme
// switch can swap them in one place.

object UltraTokens {
    // Accent
    val Accent       = Color(0xFFFF3A2F)
    val Accent2      = Color(0xFFFF6A4A)
    val AccentGlow   = Color(0x8CFF3A2F)
    val AccentSoft   = Color(0x24FF3A2F)  // ~14% — pill highlights
    val AccentTint   = Color(0x1AFF3A2F)  // ~10% — gradient stops

    // Status
    val Live = Color(0xFFFF3A2F)
    val Hd   = Color(0xFF00E5A0)
    val Uhd  = Color(0xFFFFB547)
    val Ok   = Color(0xFF7FFFAF)
    val Warn = Color(0xFFFFB547)

    // Neutrals — AMOLED defaults; theme variants override Bg0/1/2 in Theme.kt
    val Fg  = Color(0xFFF5F5F7)
    val Fg2 = Color(0xFFC7C7CF)
    val Fg3 = Color(0xFF8A8A94)
    val Fg4 = Color(0xFF5A5A64)

    val Line  = Color(0x14FFFFFF)   // 8%
    val Line2 = Color(0x24FFFFFF)   // 14%

    // Surfaces — overlays on top of bg-0
    val Surface1      = Color(0x0AFFFFFF) // 4%
    val Surface2      = Color(0x0FFFFFFF) // 6%
    val Surface3      = Color(0x14FFFFFF) // 8%
    val SurfaceStrong = Color(0x24FFFFFF) // 14%
    val Scrim         = Color(0x73000000) // 45%
    val ScrimStrong   = Color(0xD9000000) // 85%

    // CTA
    val CtaBg        = Color(0xFFFFFFFF)
    val CtaFgOnCta   = Color(0xFF0A0A0D)

    // Radii
    val RadiusXs: Dp = 6.dp
    val RadiusSm: Dp = 10.dp
    val RadiusMd: Dp = 14.dp
    val RadiusLg: Dp = 20.dp
    val RadiusXl: Dp = 28.dp

    // Layout
    val SidebarCollapsed: Dp = 92.dp
    val SidebarExpanded:  Dp = 220.dp
    val TopBarHeight:     Dp = 76.dp
    val EdgeGutter:       Dp = 80.dp     // matches the prototype's 0 80px page padding
    val LeftEdge:         Dp = 92.dp     // content starts after the collapsed sidebar
}

object UltraFonts {
    // Geist / Geist Mono / Instrument Serif are bundled via downloadable fonts in
    // the prototype; on-device we fall back to system families until the assets
    // ship. Keeping the references centralised lets us swap them in one place.
    val Sans:  FontFamily = FontFamily.SansSerif
    val Mono:  FontFamily = FontFamily.Monospace
    val Serif: FontFamily = FontFamily.Serif
}

object UltraType {
    // Editorial titles
    val HeroDisplay = TextStyle(
        fontFamily = UltraFonts.Serif,
        fontSize = 84.sp,
        lineHeight = 84.sp,
        letterSpacing = (-2.1).sp,
        fontWeight = FontWeight.Normal,
    )
    val ScreenTitle = TextStyle(
        fontFamily = UltraFonts.Sans,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
        fontWeight = FontWeight.SemiBold,
    )
    val RailTitle = TextStyle(
        fontFamily = UltraFonts.Sans,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
        fontWeight = FontWeight.SemiBold,
    )
    val SerifTitle = TextStyle(
        fontFamily = UltraFonts.Serif,
        fontSize = 28.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
        fontStyle = FontStyle.Normal,
    )

    val Body  = TextStyle(fontFamily = UltraFonts.Sans, fontSize = 15.sp, lineHeight = 22.sp)
    val Body2 = TextStyle(fontFamily = UltraFonts.Sans, fontSize = 13.sp, lineHeight = 18.sp)
    val Meta  = TextStyle(fontFamily = UltraFonts.Sans, fontSize = 12.sp, lineHeight = 16.sp, color = UltraTokens.Fg3)
    val Eyebrow = TextStyle(
        fontFamily = UltraFonts.Sans,
        fontSize = 13.sp,
        letterSpacing = 2.3.sp,           // ≈ 0.18em
        fontWeight = FontWeight.Medium,
        color = UltraTokens.Fg3,
    )
    val Mono = TextStyle(fontFamily = UltraFonts.Mono, fontSize = 13.sp)
    val MonoSmall = TextStyle(fontFamily = UltraFonts.Mono, fontSize = 11.sp, color = UltraTokens.Fg3)
}
