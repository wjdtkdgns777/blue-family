package com.example.bluefamily.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** 블루 패밀리 색. 바탕은 어둡고, 강조는 밝은 파랑에서 짙은 파랑으로 흐르는 빛 하나만 씁니다. */
@Immutable
data class Palette(
    val background: Color,
    val glow: Color,
    val glowHot: Color,
    val glass: Color,
    val rim: Color,
    val line: Color,
    val text: Color,
    val textSub: Color,
    val accent: Color,
    val accentEnd: Color,
    val onAccent: Color,
    val touchLight: Color,
) {
    /** 도장, 고른 탭, 주 버튼을 칠하는 빛. 흰 글자와 대비 4.5:1 이상입니다. */
    val accentBrush get() = Brush.linearGradient(listOf(accent, accentEnd))

    /** 큰 숫자. 위는 글자색, 아래로 갈수록 파란 빛이 섞입니다. */
    val heroBrush get() = Brush.verticalGradient(0.35f to text, 1f to accent)
}

private val DarkPalette = Palette(
    background = Color(0xFF121317),
    glow = Color(0x992F6BFF),
    glowHot = Color(0x4038BDF8),
    glass = Color(0x14FFFFFF),
    rim = Color(0x80FFFFFF),
    line = Color(0x1FFFFFFF),
    text = Color(0xFFEEF0F3),
    textSub = Color(0xFFA3A8B4),
    accent = Color(0xFF2F6BFF),
    accentEnd = Color(0xFF1D4ED8),
    onAccent = Color(0xFFFFFFFF),
    touchLight = Color(0x4DFFFFFF),
)

private val LightPalette = Palette(
    background = Color(0xFFF4F5F9),
    glow = Color(0x4D2F6BFF),
    glowHot = Color(0x2638BDF8),
    glass = Color(0xB3FFFFFF),
    rim = Color(0xFFFFFFFF),
    line = Color(0x1A16171B),
    text = Color(0xFF16171B),
    textSub = Color(0xFF5B5F6B),
    accent = Color(0xFF2F6BFF),
    accentEnd = Color(0xFF1D4ED8),
    onAccent = Color(0xFFFFFFFF),
    touchLight = Color(0x332F6BFF),
)

/** 차 화면에서 읽히도록 크기를 키웠습니다. 큰 숫자는 자간을 좁힙니다. */
object Type {
    private val numbers = "tnum"
    private val keepAll = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)
    val hero = TextStyle(fontSize = 112.sp, lineHeight = 120.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = numbers, letterSpacing = (-0.04).em)
    val headline = TextStyle(fontSize = 56.sp, lineHeight = 68.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em, lineBreak = keepAll)
    val title = TextStyle(fontSize = 36.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = numbers, letterSpacing = (-0.02).em)
    val titleSmall = TextStyle(fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = numbers, letterSpacing = (-0.02).em)
    val body = TextStyle(fontSize = 28.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = numbers, lineBreak = keepAll)
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
    val label = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = numbers, lineBreak = keepAll)
    val labelStrong = label.copy(fontWeight = FontWeight.SemiBold)
}

object Space {
    val xs = 8.dp
    val s = 16.dp
    val m = 24.dp
    val l = 32.dp
    val xl = 48.dp
    val xxl = 64.dp
    val touch = 88.dp
    val hairline = 1.dp
    val radius = 32.dp
}

/** 움직임. 누름과 이동은 스프링으로 살짝 튕기고, 주행 중에는 모두 멈춥니다. */
object Motion {
    val easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    const val SHORT1 = 100
    const val SHORT2 = 150
    const val MEDIUM1 = 250
    const val MEDIUM2 = 300
    const val LONG1 = 400
    const val LONG2 = 450
    const val STAGGER = 40
    const val PRESSED_SCALE = 0.95f
    val press = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
    val slide = spring<Dp>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
}

val LocalPalette = staticCompositionLocalOf { DarkPalette }
val LocalStill = staticCompositionLocalOf { false }

@Composable
fun BlueFamilyTheme(still: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPalette provides if (isSystemInDarkTheme()) DarkPalette else LightPalette,
        LocalStill provides still,
        content = content,
    )
}
