package io.vinicius.sak.view.theme

import androidx.compose.material3.Typography

// TODO: customise the type scale with the project's font family
// For now this uses the Material3 default type scale as a starting point

internal val SakTypography = Typography()
// To use a custom font, load it via GoogleFonts or a bundled font resource:
//
// private val SakFontFamily = FontFamily(
//     Font(R.font.inter_regular, FontWeight.Normal),
//     Font(R.font.inter_medium,  FontWeight.Medium),
//     Font(R.font.inter_bold,    FontWeight.Bold),
// )
//
// internal val SakTypography = Typography(
//     displayLarge  = TextStyle(fontFamily = SakFontFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp),
//     headlineLarge = TextStyle(fontFamily = SakFontFamily, fontWeight = FontWeight.Normal, fontSize = 32.sp),
//     titleLarge    = TextStyle(fontFamily = SakFontFamily, fontWeight = FontWeight.Bold,   fontSize = 22.sp),
//     bodyLarge     = TextStyle(fontFamily = SakFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
//     labelLarge    = TextStyle(fontFamily = SakFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
// )
