package com.usctest.app.ui.theme

import androidx.compose.ui.graphics.Color

// Design tokens from the Stitch "US Citizenship Test — iOS Native" design system:
// single accent blue, mostly-neutral surfaces, status colors reserved for feedback moments.

// Light
val AccentBlue = Color(0xFF007AFF)
val OnAccentBlue = Color(0xFFFFFFFF)
val AccentBlueContainer = Color(0xFFD6E8FF)
val OnAccentBlueContainer = Color(0xFF003C7A)

val NeutralSecondary = Color(0xFF6B6B70)
val OnNeutralSecondary = Color(0xFFFFFFFF)
val NeutralSecondaryContainer = Color(0xFFE5E5EA)
val OnNeutralSecondaryContainer = Color(0xFF3A3A3C)

val BackgroundLight = Color(0xFFF9F9FB)
val OnBackgroundLight = Color(0xFF1C1C1E)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF1C1C1E)
val SurfaceVariantLight = Color(0xFFF2F2F7)
val OnSurfaceVariantLight = Color(0xFF6B6B70)
val OutlineLight = Color(0xFFD1D1D6)
val OutlineVariantLight = Color(0xFFE5E5EA)

val ErrorRedLight = Color(0xFFFF3B30)
val OnErrorRedLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFE5E3)
val OnErrorContainerLight = Color(0xFF7A1410)

// Dark
val AccentBlueDark = Color(0xFF4DA3FF)
val OnAccentBlueDark = Color(0xFF00274D)
val AccentBlueContainerDark = Color(0xFF004A94)
val OnAccentBlueContainerDark = Color(0xFFD6E8FF)

val NeutralSecondaryDark = Color(0xFFAEAEB2)
val OnNeutralSecondaryDark = Color(0xFF1C1C1E)
val NeutralSecondaryContainerDark = Color(0xFF3A3A3C)
val OnNeutralSecondaryContainerDark = Color(0xFFE5E5EA)

val BackgroundDark = Color(0xFF0B0B0D)
val OnBackgroundDark = Color(0xFFF2F2F7)
val SurfaceDark = Color(0xFF1C1C1E)
val OnSurfaceDark = Color(0xFFF2F2F7)
val SurfaceVariantDark = Color(0xFF2C2C2E)
val OnSurfaceVariantDark = Color(0xFFAEAEB2)
val OutlineDark = Color(0xFF48484A)
val OutlineVariantDark = Color(0xFF3A3A3C)

val ErrorRedDark = Color(0xFFFF6961)
val OnErrorRedDark = Color(0xFF3A0A08)
val ErrorContainerDark = Color(0xFF5C1712)
val OnErrorContainerDark = Color(0xFFFFE5E3)

/** Status color for correct answers / mastery, shown only in feedback moments —
 * not theme-reactive since callers reference it directly rather than via MaterialTheme. */
val SuccessGreen = Color(0xFF34C759)
