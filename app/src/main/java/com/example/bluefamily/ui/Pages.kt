package com.example.bluefamily.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bluefamily.Code
import com.example.bluefamily.Drive
import com.example.bluefamily.Family
import com.example.bluefamily.Mileage
import com.example.bluefamily.MileageRule
import com.example.bluefamily.R
import com.example.bluefamily.Store
import com.example.bluefamily.Tier
import com.example.bluefamily.Turn
import com.example.bluefamily.Tx
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
internal fun MileagePage() {
    TwoColumns(left = { Balance() }, right = { Ledger() })
}

@Composable
private fun Balance() {
    val palette = LocalPalette.current
    val still = LocalStill.current
    val today = LocalDate.now()
    val balance = Store.balance
    val tier = Mileage.tier(Store.ledger, Store.trips, today)
    Text(stringResource(R.string.balance_label), Type.body, palette.textSub)
    Row(verticalAlignment = Alignment.Bottom) {
        RollingNumber(balance, Type.hero.copy(brush = palette.heroBrush), Color.Unspecified, fromZero = true)
        Spacer(Modifier.width(Space.s))
        Text(stringResource(R.string.unit_mile), Type.title, palette.text, Modifier.padding(bottom = 18.dp))
    }
    Text(stringResource(R.string.today_earned, Store.earnedOn(today).grouped()), Type.bodyStrong, palette.accent)
    Spacer(Modifier.height(Space.xl))
    val (name, next, goal) = when (tier) {
        Tier.GREEN -> listOf(R.string.tier_green, R.string.tier_silver_short, MileageRule.SILVER)
        Tier.SILVER -> listOf(R.string.tier_silver, R.string.tier_gold_short, MileageRule.GOLD)
        Tier.GOLD -> listOf(R.string.tier_gold, null, null)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(stringResource(name as Int), Type.titleSmall, palette.text, Modifier.weight(1f))
        if (next != null && goal != null) {
            Text(stringResource(R.string.tier_to_next, stringResource(next as Int), ((goal as Int) - balance).coerceAtLeast(0).grouped()), Type.body, palette.textSub)
        }
    }
    Spacer(Modifier.height(Space.s))
    val target = if (goal == null) 1f else (balance / (goal as Int).toFloat()).coerceIn(0f, 1f)
    val fill = remember { Animatable(if (still) target else 0f) }
    LaunchedEffect(target) { fill.animateTo(target, tween(Motion.LONG2 * 2, Motion.MEDIUM1, Motion.easing)) }
    Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(palette.line)) {
        Box(Modifier.fillMaxWidth(fill.value).fillMaxHeight().clip(CircleShape).background(palette.accentBrush))
    }
}

@Composable
private fun Ledger() {
    val palette = LocalPalette.current
    SectionHeader(stringResource(R.string.ledger_title), null)
    val entries = Store.ledger.sortedByDescending { it.at }
    if (entries.isEmpty()) Text(stringResource(R.string.ledger_empty), Type.body, palette.textSub, Modifier.padding(vertical = Space.m))
    var index = 0
    entries.groupBy { it.at.toLocalDate() }.forEach { (date, txs) ->
        Text(shortDate(date), Type.label, palette.textSub, Modifier.padding(top = Space.m, bottom = Space.xs).enter(index++))
        Glass {
            txs.forEachIndexed { i, tx ->
                Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).enter(index++), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tx.title(), Type.body, palette.text)
                        tx.turnDetail()?.let { Text(it, Type.label, palette.textSub) }
                    }
                    Text(stringResource(R.string.amount_plus, tx.miles.grouped()), Type.bodyStrong, palette.text)
                }
                if (i < txs.lastIndex) Hairline()
            }
        }
    }
}

// 회전 깜빡이 적립 줄 아래에 그 운전에서 늦게 켠 회전을 보여 줍니다
@Composable
private fun Tx.turnDetail(): String? {
    if (code != Code.TURN && code != Code.TURN_UNGUIDED) return null
    val trip = Store.trips.firstOrNull { "${it.id}-$code" == id } ?: return null
    val late = trip.turns.filter { it.guided == (code == Code.TURN) && !it.onTime }
    if (late.isEmpty()) return null
    return late.map { stringResource(R.string.turn_late, stringResource(if (it.turn == Turn.LEFT) R.string.turn_left else R.string.turn_right)) }.joinToString(", ")
}

@Composable
private fun Tx.title() = when (code) {
    Code.TURN -> stringResource(R.string.ledger_turns, count)
    Code.TURN_UNGUIDED -> stringResource(R.string.ledger_turns_unguided, count)
    Code.DISTANCE -> stringResource(if (miles > count) R.string.ledger_distance_double else R.string.ledger_distance, count)
    Code.REST -> stringResource(R.string.ledger_rest)
    Code.KEPT_DAY -> stringResource(R.string.ledger_kept_day)
    Code.STREAK -> stringResource(R.string.ledger_streak)
    Code.PARTNER -> stringResource(R.string.ledger_partner)
    Code.FAMILY -> stringResource(R.string.ledger_family)
    Code.BELT -> stringResource(R.string.ledger_belt, count)
}

@Composable
internal fun RestPlaces() {
    val palette = LocalPalette.current
    SectionHeader(stringResource(R.string.rest_places_title), null)
    Glass {
    Drive.places.forEachIndexed { index, place ->
            Row(Modifier.fillMaxWidth().heightIn(min = 112.dp).enter(index), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(place.name), Type.bodyStrong, palette.text)
                    Text(stringResource(R.string.km, "%.1f".format(Drive.distanceKm(place))), Type.label, palette.textSub)
                }
                if (Drive.pendingPlace == place) {
                    Text(stringResource(R.string.waypoint_added), Type.body, palette.accent)
                } else {
                    ActionButton(stringResource(R.string.add_waypoint), { Drive.addRestStop(place) })
                }
            }
            if (index < Drive.places.lastIndex) Hairline()
        }
    }
    Spacer(Modifier.height(Space.m))
    Text(stringResource(R.string.rest_places_note), Type.body, palette.textSub, Modifier.enter(Drive.places.size))
}

@Composable
internal fun FamilyPage() {
    val palette = LocalPalette.current
    val settings = Store.settings
    val me = Mileage.weekMember(Store.trips, LocalDate.now(), R.string.member_me, R.string.car_this)
    TwoColumns(
        left = {
            Text(stringResource(R.string.family_bundle_title), Type.body, palette.textSub)
            Text(stringResource(R.string.family_bundle_value, Family.cars), Type.title.copy(brush = palette.accentBrush), Color.Unspecified, Modifier.enter(0))
            Text(stringResource(R.string.family_bundle_bonus, (Mileage.familyRate(Family.cars) * 100).roundToInt()), Type.bodyStrong, palette.accent, Modifier.enter(1))
            Spacer(Modifier.height(Space.xl))
            SectionHeader(stringResource(R.string.family_scores_title), null)
            Glass {
    (listOf(me) + Family.others).forEachIndexed { index, member ->
                    Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).enter(index + 3), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${stringResource(member.name)}  ${stringResource(member.car)}", Type.bodyStrong, palette.text)
                            // 감점이 있는 항목만 보여 줍니다
                            val penalties = listOf(
                                R.string.penalty_speeding to member.speeding,
                                R.string.penalty_hard_brake to member.hardBrakes,
                                R.string.penalty_signal to member.missedSignals,
                                R.string.penalty_belt to member.beltOff,
                            ).filter { it.second > 0 }.map { stringResource(it.first, it.second) }
                            Text(if (penalties.isEmpty()) stringResource(R.string.penalty_none) else penalties.joinToString(", "), Type.label, palette.textSub)
                        }
                        Text(stringResource(R.string.family_score, member.score), Type.bodyStrong, palette.text)
                    }
                    if (index < Family.others.size) Hairline()
                }
            }
        },
        right = {
            Text(stringResource(R.string.family_code_title), Type.body, palette.textSub)
            Text(settings.inviteCode.chunked(3).joinToString(" "), Type.title, palette.text)
            Spacer(Modifier.height(Space.l))
            Glass {
    SwitchRow(0, stringResource(R.string.family_share), stringResource(R.string.family_share_detail), settings.familyShare) {
                    Store.update(settings.copy(familyShare = it))
                }
            }
        },
    )
}

@Composable
internal fun SettingsPage() {
    val palette = LocalPalette.current
    val settings = Store.settings
    var clearStep by rememberSaveable { mutableStateOf(0) }
    var seeded by rememberSaveable { mutableStateOf(false) }
    TwoColumns(
        left = {
            SectionHeader(stringResource(R.string.settings_title), null)
            Glass {
    SwitchRow(0, stringResource(R.string.settings_sound), null, settings.sound) {
                    Store.update(settings.copy(sound = it))
                }
                Hairline()
                SwitchRow(1, stringResource(R.string.settings_voice), null, settings.voice) {
                    Store.update(settings.copy(voice = it))
                }
            }
            Spacer(Modifier.height(Space.l))
            val label = when (clearStep) {
                0 -> R.string.settings_clear
                1 -> R.string.settings_clear_confirm
                else -> R.string.settings_cleared
            }
            ActionButton(stringResource(label), {
                if (clearStep == 1) Store.clear()
                clearStep = (clearStep + 1).coerceAtMost(2)
            }, Modifier.enter(3))
        },
        right = {
            SectionHeader(stringResource(R.string.demo_title), null)
            Glass {
    DemoRow(0, R.string.demo_replay, R.string.demo_replay_detail) {
                    ActionButton(stringResource(if (Drive.replaying) R.string.stop else R.string.start), { if (Drive.replaying) Drive.stopReplay() else Drive.replay() })
                }
                Hairline()
                SwitchRow(1, stringResource(R.string.demo_fast), stringResource(R.string.demo_fast_detail), Drive.timeScale > 1) {
                    Drive.timeScale = if (it) DEMO_SCALE else 1
                }
                Hairline()
                DemoRow(2, R.string.demo_seed, null) {
                    if (seeded) Text(stringResource(R.string.demo_seed_done), Type.body, palette.accent)
                    else ActionButton(stringResource(R.string.start), { Drive.seedHistory(); seeded = true })
                }
            }
        },
    )
}

@Composable
private fun DemoRow(index: Int, title: Int, detail: Int?, trailing: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(horizontal = Space.m).enter(index), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), Type.bodyStrong, palette.text)
            if (detail != null) Text(stringResource(detail), Type.label, palette.textSub)
        }
        trailing()
    }
}

private const val DEMO_SCALE = 30
