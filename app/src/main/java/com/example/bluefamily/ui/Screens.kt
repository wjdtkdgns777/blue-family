package com.example.bluefamily.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bluefamily.Conditions
import com.example.bluefamily.Drive
import com.example.bluefamily.MileageRule
import com.example.bluefamily.R
import com.example.bluefamily.Store
import com.example.bluefamily.Sun
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
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
                    R.string.consent_mic to R.string.consent_mic_detail,
                ).forEachIndexed { index, (title, detail) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).enter(index + 4), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(title), Type.bodyStrong, palette.text, Modifier.width(200.dp))
                        Text(stringResource(detail), Type.body, palette.textSub, Modifier.weight(1f))
                    }
                    if (index < 2) Hairline()
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
    val tabs = listOf(R.string.tab_mileage, R.string.tab_before, R.string.tab_family, R.string.tab_settings)
    Column(Modifier.fillMaxSize().padding(horizontal = Space.xxl, vertical = Space.xl)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.product_name), Type.headline.copy(brush = palette.accentBrush), Color.Unspecified, Modifier.weight(1f))
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
                1 -> BeforePage()
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
private fun BeforePage() {
    TwoColumns(
        left = { ConditionsEditor(Store.conditions, Store::update) },
        right = {
            RouteStatus()
            Spacer(Modifier.height(Space.xl))
            RestPlaces()
        },
    )
}

@Composable
private fun ConditionsEditor(conditions: Conditions, onChange: (Conditions) -> Unit) {
    val palette = LocalPalette.current
    val trips = Store.trips.filter { it.endedAt.toLocalDate() == LocalDate.now() }
    val kept = stringResource(R.string.condition_kept)
    val notKept = stringResource(R.string.condition_broken)
    // 오늘 운전했으면 켠 조건 아래에 지켰는지 보여 줍니다
    fun result(active: Boolean, broken: Boolean) = if (active && trips.isNotEmpty()) (if (broken) notKept else kept) else null
    SectionHeader(stringResource(R.string.conditions_title), null)
    Glass {
    SwitchRow(0, stringResource(R.string.condition_night), result(conditions.noNight, trips.any { it.nightM > 0 }), conditions.noNight) {
            onChange(conditions.copy(noNight = it))
        }
        Hairline()
        SwitchRow(1, stringResource(R.string.condition_highway), result(conditions.avoidHighway, trips.any { it.highwayM > 0 }), conditions.avoidHighway) {
            onChange(conditions.copy(avoidHighway = it))
        }
        Hairline()
        SwitchRow(2, stringResource(R.string.conditions_rest_title), result(conditions.rest, trips.any { it.overLimit }), conditions.rest) {
            onChange(conditions.copy(rest = it))
        }
        Row(Modifier.fillMaxWidth().padding(start = Space.m, bottom = Space.m).enter(3), verticalAlignment = Alignment.CenterVertically) {
            val enabled = conditions.rest
            Text(stringResource(R.string.conditions_rest_limit), Type.body, palette.textSub, Modifier.weight(1f))
            StepButton("−", stringResource(R.string.decrease), enabled) {
                onChange(conditions.copy(restMinutes = (conditions.restMinutes - MileageRule.REST_STEP).coerceAtLeast(MileageRule.REST_MIN)))
            }
            Box(Modifier.width(180.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingNumber(conditions.restMinutes, Type.title, if (enabled) palette.text else palette.textSub)
                    Text(stringResource(R.string.unit_minute), Type.body, palette.textSub, Modifier.padding(start = 4.dp, bottom = 3.dp))
                }
            }
            StepButton("+", stringResource(R.string.increase), enabled) {
                onChange(conditions.copy(restMinutes = (conditions.restMinutes + MileageRule.REST_STEP).coerceAtMost(MileageRule.REST_MAX)))
            }
        }
    }
}

@Composable
private fun RouteStatus() {
    val palette = LocalPalette.current
    val route = Drive.route
    val conditions = Store.conditions
    val sunset = Sun.sunset(LocalDate.now(), Sun.SEOUL_LAT, Sun.SEOUL_LON, ZoneId.systemDefault())
    val dash = stringResource(R.string.dash)
    SectionHeader(stringResource(R.string.route_title), null)
    val rows = listOfNotNull(
        stringResource(R.string.route_destination) to (route.destination ?: stringResource(R.string.route_none)),
        (stringResource(R.string.route_option) to when {
            route.freeRoad == true -> stringResource(R.string.route_free)
            route.freeRoad == false -> stringResource(R.string.route_free_failed)
            route.freeRequested -> stringResource(R.string.route_free_requested)
            else -> dash
        }).takeIf { conditions.avoidHighway && route.destination != null },
        route.arrival?.let { stringResource(R.string.route_arrival) to it.format(clock) },
        (stringResource(R.string.route_sunset) to (sunset?.format(clock) ?: dash)).takeIf { conditions.noNight },
        (stringResource(R.string.route_continuous) to stringResource(R.string.route_continuous_value, (Drive.continuousMs / 60_000).toInt(), conditions.restMinutes)).takeIf { conditions.rest },
    )
    Glass {
    rows.forEachIndexed { index, (label, value) ->
            Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).enter(index), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Type.body, palette.textSub, Modifier.weight(1f))
                Text(value, Type.bodyStrong, palette.text, align = TextAlign.End)
            }
            if (index < rows.lastIndex) Hairline()
        }
    }
    if (conditions.noNight && route.arrivesAfterSunset) {
        Spacer(Modifier.height(Space.m))
        Text(stringResource(R.string.route_after_sunset), Type.body, palette.text, Modifier.enter(rows.size))
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
