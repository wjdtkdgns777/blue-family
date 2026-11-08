package com.example.bluefamily.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

private const val ROLLING_SLOTS = 8
private const val GLOW_FLATTEN = 0.45f

@Composable
fun Text(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    BasicText(text, modifier, style.copy(color = color, textAlign = align))
}

/** 누르면 스프링으로 살짝 눌리고, 손가락 닿은 자리에서 빛이 번집니다. 주행 중에는 크기가 그대로입니다. */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Space.radius),
    role: Role = Role.Button,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val still = LocalStill.current
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    var touch by remember { mutableStateOf(Offset.Zero) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(source) {
        source.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    touch = it.pressPosition
                    pressed = true
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }
    val scale by animateFloatAsState(if (pressed && !still) Motion.PRESSED_SCALE else 1f, Motion.press, label = "pressScale")
    val light by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) Motion.SHORT2 else Motion.MEDIUM2), label = "pressLight")
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(modifier)
            .clip(shape)
            .drawWithContent {
                drawContent()
                if (light > 0f) drawRect(Brush.radialGradient(listOf(palette.touchLight.copy(alpha = palette.touchLight.alpha * light), Color.Transparent), touch, size.maxDimension * 0.6f))
            }
            .clickable(source, indication = null, role = role) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** 유리 표면. 반투명 바탕에 왼쪽 위가 빛나는 1px 테두리를 두릅니다. */
fun Modifier.glass(palette: Palette, shape: Shape = RoundedCornerShape(Space.radius)) =
    background(palette.glass, shape).border(Space.hairline, Brush.linearGradient(0f to palette.rim, 0.4f to palette.line, 1f to palette.line), shape)

/** 파란 빛으로 칠하고 같은 색 그림자로 은은하게 번지게 합니다. */
fun Modifier.lit(palette: Palette, shape: Shape, glow: Dp = 16.dp) =
    shadow(glow, shape, ambientColor = palette.accent, spotColor = palette.accent).background(palette.accentBrush, shape)

/** 화면 위쪽에서 번지는 파란 불빛. 움직이지 않습니다. */
fun Modifier.glowBackground(palette: Palette) = drawBehind {
    drawRect(palette.background)
    scale(1f, GLOW_FLATTEN, pivot = Offset(size.width / 2, 0f)) {
        glowSpot(palette.glow, Offset(size.width * 0.45f, 0f), size.width * 0.5f)
        glowSpot(palette.glowHot, Offset(size.width * 0.75f, 0f), size.width * 0.28f)
    }
}

private fun DrawScope.glowSpot(color: Color, center: Offset, radius: Float) =
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(Space.hairline).background(LocalPalette.current.line))
}

@Composable
fun RollingNumber(value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier, prefix: String = "", fromZero: Boolean = false) {
    val still = LocalStill.current
    var shown by remember { mutableIntStateOf(if (fromZero && !still) 0 else value) }
    LaunchedEffect(value) {
        if (shown != value && !still) delay(Motion.SHORT2.toLong())
        shown = value
    }
    val text = prefix + NumberFormat.getIntegerInstance(Locale.KOREA).format(shown)
    Row(modifier) {
        for (slot in ROLLING_SLOTS downTo 1) {
            val char = text.getOrNull(text.length - slot)?.toString().orEmpty()
            key(slot) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        if (still) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val delay = (slot - 1) * Motion.STAGGER
                            (slideInVertically(tween(Motion.LONG1, delay, Motion.easing)) { it } + fadeIn(tween(Motion.SHORT2, delay))) togetherWith
                                (slideOutVertically(tween(Motion.LONG1, delay, Motion.easing)) { -it } + fadeOut(tween(Motion.SHORT2, delay)))
                        } using SizeTransform(clip = true) { _, _ -> tween(Motion.LONG1, easing = Motion.easing) }
                    },
                    label = "digit",
                ) { Text(it, style, color) }
            }
        }
    }
}

@Composable
fun Modifier.enter(index: Int): Modifier {
    if (LocalStill.current) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * Motion.STAGGER.toLong())
        progress.animateTo(1f, tween(Motion.LONG2, easing = Motion.easing))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

@Composable
fun Segments(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, itemWidth: Dp = 176.dp) {
    val palette = LocalPalette.current
    val inset = 6.dp
    val offset by animateDpAsState(itemWidth * selected, Motion.slide, label = "segment")
    Box(
        modifier
            .width(itemWidth * items.size + inset * 2)
            .height(Space.touch)
            .glass(palette, CircleShape)
            .padding(inset),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .width(itemWidth)
                .fillMaxHeight()
                .lit(palette, CircleShape),
        )
        Row {
            items.forEachIndexed { index, label ->
                val active = index == selected
                val color by animateColorAsState(if (active) palette.onAccent else palette.textSub, tween(Motion.SHORT2), label = "segmentText")
                Pressable(
                    onClick = { onSelect(index) },
                    modifier = Modifier.width(itemWidth).fillMaxHeight().semantics { this.selected = active },
                    shape = CircleShape,
                    role = Role.Tab,
                ) {
                    Text(label, if (active) Type.bodyStrong else Type.body, color)
                }
            }
        }
    }
}

@Composable
fun Toggle(checked: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val thumb by animateDpAsState(if (checked) 44.dp else 0.dp, Motion.slide, label = "toggleThumb")
    val on by animateFloatAsState(if (checked) 1f else 0f, tween(Motion.SHORT2), label = "toggleOn")
    Box(
        modifier
            .size(width = 100.dp, height = 56.dp)
            .glass(palette, CircleShape)
            .drawBehind { drawRoundRect(palette.accentBrush, cornerRadius = CornerRadius(size.height / 2), alpha = on) }
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .offset(x = thumb)
                .size(44.dp)
                .shadow(4.dp, CircleShape)
                .background(palette.onAccent, CircleShape),
        )
    }
}

@Composable
fun StepButton(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Pressable(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.size(Space.touch).glass(palette, CircleShape).semantics { contentDescription = description },
        shape = CircleShape,
    ) {
        Text(glyph, Type.title, if (enabled) palette.text else palette.line)
    }
}

/** 목록을 담는 유리 판. */
@Composable
fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass(LocalPalette.current).padding(horizontal = Space.m), content = content)
}

@Composable
fun Mark(on: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val dot = modifier.size(16.dp)
    Box(if (on) dot.lit(palette, CircleShape, Space.xs) else dot.border(2.dp, palette.line, CircleShape))
}

@Composable
fun AdaptiveColumns(wideFrom: Dp, wide: @Composable () -> Unit, narrow: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= wideFrom) wide() else narrow()
    }
}

@Composable
fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val palette = LocalPalette.current
    Pressable(
        onClick = onClick,
        modifier = modifier.height(Space.touch).widthIn(min = 200.dp).then(if (primary) Modifier.lit(palette, CircleShape) else Modifier.glass(palette, CircleShape)),
        shape = CircleShape,
    ) {
        Text(label, Type.bodyStrong, if (primary) palette.onAccent else palette.text, Modifier.padding(horizontal = Space.l))
    }
}
