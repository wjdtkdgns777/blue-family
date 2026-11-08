package com.example.bluefamily.ui

import ai.pleos.playground.vehicle.constant.area.SeatValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bluefamily.Code
import com.example.bluefamily.Drive
import com.example.bluefamily.Mileage
import com.example.bluefamily.MileageRule
import com.example.bluefamily.R
import com.example.bluefamily.Store
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

internal val wide = 1100.dp

internal fun Int.grouped(): String = NumberFormat.getIntegerInstance(Locale.KOREA).format(this)

@Composable
internal fun shortDate(date: LocalDate) = stringResource(R.string.date_short, date.monthValue, date.dayOfMonth)

@Composable
fun BlueFamilyApp(driving: Boolean, onAgree: () -> Unit, onLater: () -> Unit) {
    val palette = LocalPalette.current
    val page = when {
        !Store.settings.consented -> Page.CONSENT
        driving -> Page.DRIVING
        else -> Page.PARKED
    }
    AnimatedContent(
        targetState = page,
        modifier = Modifier.fillMaxSize().glowBackground(palette),
        transitionSpec = {
            if (targetState == Page.DRIVING) EnterTransition.None togetherWith ExitTransition.None
            else fadeIn(tween(Motion.MEDIUM2, easing = Motion.easing)) togetherWith fadeOut(tween(Motion.SHORT1))
        },
        label = "page",
    ) {
        when (it) {
            Page.CONSENT -> ConsentScreen(onAgree, onLater)
            Page.DRIVING -> DrivingScreen()
            Page.PARKED -> ParkedScreen()
        }
    }
}

private enum class Page { CONSENT, DRIVING, PARKED }

@Composable
private fun ConsentScreen(onAgree: () -> Unit, onLater: () -> Unit) {
    val palette = LocalPalette.current
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.xxl), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 1040.dp)) {
            Text(stringResource(R.string.consent_title), Type.title, palette.text, Modifier.enter(0))
            Spacer(Modifier.height(Space.l))
            Glass {
    listOf(
                    R.string.consent_vehicle to R.string.consent_vehicle_detail,
                    R.string.consent_location to R.string.consent_location_detail,
                ).forEachIndexed { index, (title, detail) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).enter(index + 4), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(title), Type.bodyStrong, palette.text, Modifier.width(200.dp))
                        Text(stringResource(detail), Type.body, palette.textSub, Modifier.weight(1f))
                    }
                    if (index < 1) Hairline()
                }
            }
            Spacer(Modifier.height(Space.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.enter(7)) {
                ActionButton(stringResource(R.string.consent_agree), onAgree, primary = true)
                ActionButton(stringResource(R.string.consent_later), onLater)
            }
        }
    }
}

@Composable
private fun DrivingScreen() {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxSize().padding(Space.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.driving_title), Type.headline, palette.text, align = TextAlign.Center)
    }
}

@Composable
private fun ParkedScreen() {
    val palette = LocalPalette.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.tab_mileage, R.string.tab_drive, R.string.tab_family, R.string.tab_settings)
    Column(Modifier.fillMaxSize().padding(horizontal = Space.xxl, vertical = Space.xl)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.product_name), Type.title, palette.text, Modifier.weight(1f))
            Segments(tabs.map { stringResource(it) }, tab, { tab = it }, itemWidth = 160.dp)
        }
        Spacer(Modifier.height(Space.xl))
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(Motion.MEDIUM1, Motion.SHORT1, Motion.easing)) + slideInVertically(tween(Motion.LONG1, easing = Motion.easing)) { it / 24 }) togetherWith
                    fadeOut(tween(Motion.SHORT1))
            },
            label = "tab",
        ) { page ->
            when (page) {
                0 -> MileagePage()
                1 -> DrivePage()
                2 -> FamilyPage()
                else -> SettingsPage()
            }
        }
    }
}

@Composable
internal fun TwoColumns(left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    AdaptiveColumns(
        wideFrom = wide,
        wide = {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(0.42f).verticalScroll(rememberScrollState()), content = left)
                Spacer(Modifier.width(Space.xxl))
                Column(Modifier.weight(0.58f).verticalScroll(rememberScrollState()), content = right)
            }
        },
        narrow = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                left()
                Spacer(Modifier.height(Space.xl))
                right()
            }
        },
    )
}

@Composable
internal fun SectionHeader(title: String, trailing: String?) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = Space.s, bottom = Space.xs), verticalAlignment = Alignment.Bottom) {
        Text(title, Type.labelStrong, palette.text, Modifier.weight(1f))
        if (trailing != null) Text(trailing, Type.label, palette.textSub)
    }
}

@Composable
private fun DrivePage() {
    TwoColumns(left = { Seats() }, right = { EarnPreview() })
}

@Composable
private fun Seats() {
    val palette = LocalPalette.current
    SectionHeader(stringResource(R.string.seats_title), null)
    val seats = Drive.seatStates
    Glass {
        seats.forEachIndexed { index, seat ->
            Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).enter(index), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(seatName(seat.seat)), Type.body, palette.text, Modifier.weight(1f))
                Text(stringResource(if (seat.buckled) R.string.seat_buckled else R.string.seat_unbuckled), Type.bodyStrong, if (seat.buckled) palette.accent else palette.textSub)
            }
            if (index < seats.lastIndex) Hairline()
        }
    }
}

private fun seatName(seat: SeatValue) = when (seat) {
    SeatValue.ROW_1_LEFT -> R.string.seat_driver
    SeatValue.ROW_1_RIGHT -> R.string.seat_passenger
    SeatValue.ROW_2_LEFT -> R.string.seat_rear_left
    SeatValue.ROW_2_CENTER -> R.string.seat_rear_center
    else -> R.string.seat_rear_right
}

// 지금 탄 사람과 오늘 기록으로 이번 운전에서 받을 수 있는 마일리지를 보여 줍니다
@Composable
private fun EarnPreview() {
    val palette = LocalPalette.current
    val today = LocalDate.now()
    val seated = Drive.seatStates.size
    val signalGood = (Mileage.signalRate(Store.trips, today.minusDays(6)) ?: 0.0) >= MileageRule.SAFE_RATE
    val safeDayOpen = Store.ledger.none { it.code == Code.SAFE_DAY && it.at.toLocalDate() == today } &&
        Store.trips.filter { it.endedAt.toLocalDate() == today }.all(Mileage::safe)
    val rate = (Drive.familyRate() * 100).roundToInt()
    SectionHeader(stringResource(R.string.earn_title), null)
    val rows = listOfNotNull(
        stringResource(R.string.earn_belt) to stringResource(R.string.amount_plus, (MileageRule.BELT_TRIP + (seated - 1) * MileageRule.BELT_PASSENGER).toString()),
        stringResource(R.string.earn_turn) to stringResource(R.string.earn_turn_value, MileageRule.TURN_ON_TIME),
        stringResource(R.string.earn_distance) to stringResource(R.string.earn_distance_value, MileageRule.PER_KM * (if (signalGood) 2 else 1)),
        (stringResource(R.string.earn_safe_day) to stringResource(R.string.amount_plus, MileageRule.SAFE_DAY.toString())).takeIf { safeDayOpen },
        (stringResource(R.string.earn_family) to stringResource(R.string.earn_family_value, rate)).takeIf { rate > 0 },
    )
    Glass {
        rows.forEachIndexed { index, (label, value) ->
            Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).enter(index), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Type.body, palette.text, Modifier.weight(1f))
                Text(value, Type.bodyStrong, palette.text, align = TextAlign.End)
            }
            if (index < rows.lastIndex) Hairline()
        }
    }
}

@Composable
internal fun SwitchRow(index: Int, title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Pressable(
        onClick = { onChange(!checked) },
        modifier = Modifier.fillMaxWidth().enter(index).semantics { toggleableState = ToggleableState(checked) },
        shape = RoundedCornerShape(20.dp),
        role = Role.Switch,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(horizontal = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, Type.bodyStrong, palette.text)
                if (detail != null) Text(detail, Type.label, palette.textSub)
            }
            Toggle(checked)
        }
    }
}
