package com.example.bluefamily

import java.time.LocalDate
import java.time.LocalDateTime
import androidx.annotation.StringRes
import java.time.LocalTime
import kotlin.math.roundToInt

enum class Turn { LEFT, RIGHT }

data class TurnRecord(val time: LocalTime, val turn: Turn, val onTime: Boolean, val guided: Boolean = true)

enum class Code { DISTANCE, TURN, TURN_UNGUIDED, SAFE_DAY, STREAK, FAMILY, BELT }

data class Tx(val id: String, val at: LocalDateTime, val code: Code, val miles: Int, val count: Int)

data class Trip(
    val id: String,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val distanceM: Double,
    val turns: List<TurnRecord>,
    val speeding: Int = 0,
    val hardBrakes: Int = 0,
    val beltOff: Int = 0,
    val beltedSeats: Int = 0,
)

enum class Tier { GREEN, SILVER, GOLD }

data class Member(@StringRes val name: Int, @StringRes val car: Int, val speeding: Int, val hardBrakes: Int, val missedSignals: Int, val beltOff: Int) {
    val score get() = Mileage.score(speeding, hardBrakes, missedSignals, beltOff)
}

object Family {
    val others = listOf(
        Member(R.string.member_mother, R.string.car_grandeur, 0, 1, 1, 0),
        Member(R.string.member_daughter, R.string.car_casper, 2, 1, 3, 1),
        Member(R.string.member_son, R.string.car_ev3, 1, 0, 0, 0),
    )
    val cars get() = others.size + 1
}

object MileageRule {
    const val PER_KM = 1
    const val TURN_ON_TIME = 5
    const val TURN_UNGUIDED = 2
    const val SAFE_DAY = 20
    const val STREAK = 100
    const val STREAK_DAYS = 7
    const val DAILY_CAP = 300
    const val SAFE_RATE = 0.95
    const val SILVER = 3_000
    const val SILVER_RATE = 0.9
    const val GOLD = 10_000
    const val GOLD_RATE = 0.95
    const val SPEEDING_PENALTY = 5
    const val HARD_BRAKE_PENALTY = 3
    const val MISSED_SIGNAL_PENALTY = 3
    const val BELT_OFF_PENALTY = 5
    const val BELT_TRIP = 10
    const val BELT_PASSENGER = 5
    const val FAMILY_RATE_PER_CAR = 0.1
    const val FAMILY_RATE_MAX = 0.3
}

object Mileage {

    /** 과속, 급제동, 늦은 깜빡이, 벨트 풀림이 하나도 없으면 감점 없는 운전입니다. */
    fun safe(trip: Trip) = trip.speeding == 0 && trip.hardBrakes == 0 && trip.beltOff == 0 && trip.turns.all { it.onTime }

    fun signalRate(trips: List<Trip>, from: LocalDate): Double? {
        val turns = trips.filter { !it.startedAt.toLocalDate().isBefore(from) }.flatMap { it.turns }
        return if (turns.isEmpty()) null else turns.count { it.onTime }.toDouble() / turns.size
    }

    fun score(speeding: Int, hardBrakes: Int, missedSignals: Int, beltOff: Int) =
        (100 - speeding * MileageRule.SPEEDING_PENALTY - hardBrakes * MileageRule.HARD_BRAKE_PENALTY - missedSignals * MileageRule.MISSED_SIGNAL_PENALTY - beltOff * MileageRule.BELT_OFF_PENALTY).coerceIn(0, 100)

    fun weekMember(trips: List<Trip>, today: LocalDate, @StringRes name: Int, @StringRes car: Int): Member {
        val week = trips.filter { !it.startedAt.toLocalDate().isBefore(today.minusDays(6)) }
        return Member(name, car, week.sumOf { it.speeding }, week.sumOf { it.hardBrakes }, week.sumOf { trip -> trip.turns.count { !it.onTime } }, week.sumOf { it.beltOff })
    }

    fun familyRate(cars: Int) = ((cars - 1) * MileageRule.FAMILY_RATE_PER_CAR).coerceIn(0.0, MileageRule.FAMILY_RATE_MAX)

    fun forTrip(trip: Trip, history: List<Trip>, ledger: List<Tx>, familyRate: Double = 0.0): List<Tx> {
        val day = trip.endedAt.toLocalDate()
        fun tx(code: Code, miles: Int, count: Int) = Tx("${trip.id}-$code", trip.endedAt, code, miles, count)

        val guided = trip.turns.count { it.guided && it.onTime }
        val unguided = trip.turns.count { !it.guided && it.onTime }
        val km = (trip.distanceM / 1000).toInt()
        val signalGood = (signalRate(history, day.minusDays(6)) ?: 0.0) >= MileageRule.SAFE_RATE
        val safeToday = (history.filter { it.endedAt.toLocalDate() == day } + trip).all(::safe)
        val safeDayGiven = ledger.any { it.code == Code.SAFE_DAY && it.at.toLocalDate() == day }

        val earned = buildList {
            if (guided > 0) add(tx(Code.TURN, guided * MileageRule.TURN_ON_TIME, guided))
            if (unguided > 0) add(tx(Code.TURN_UNGUIDED, unguided * MileageRule.TURN_UNGUIDED, unguided))
            if (km > 0) add(tx(Code.DISTANCE, km * MileageRule.PER_KM * (if (signalGood) 2 else 1), km))
            if (safeToday && !safeDayGiven) {
                add(tx(Code.SAFE_DAY, MileageRule.SAFE_DAY, 1))
                val streak = (1 until MileageRule.STREAK_DAYS).all { back ->
                    ledger.any { it.code == Code.SAFE_DAY && it.at.toLocalDate() == day.minusDays(back.toLong()) }
                }
                val streakGiven = ledger.any { it.code == Code.STREAK && !it.at.toLocalDate().isBefore(day.minusDays(MileageRule.STREAK_DAYS - 1L)) }
                if (streak && !streakGiven) add(tx(Code.STREAK, MileageRule.STREAK, 1))
            }
            if (trip.beltOff == 0 && trip.beltedSeats > 0) add(tx(Code.BELT, MileageRule.BELT_TRIP + (trip.beltedSeats - 1) * MileageRule.BELT_PASSENGER, trip.beltedSeats))
        }

        var room = MileageRule.DAILY_CAP - ledger.filter { it.at.toLocalDate() == day }.sumOf { it.miles }
        val capped = earned.mapNotNull { item ->
            val miles = item.miles.coerceAtMost(room.coerceAtLeast(0))
            room -= miles
            item.copy(miles = miles).takeIf { (miles > 0 || item.code == Code.SAFE_DAY) && ledger.none { it.id == item.id } }
        }
        val bonus = (capped.sumOf { it.miles } * familyRate).roundToInt()
        return if (bonus > 0) capped + tx(Code.FAMILY, bonus, 1) else capped
    }

    fun tier(ledger: List<Tx>, trips: List<Trip>, today: LocalDate): Tier {
        val total = ledger.sumOf { it.miles }
        val safeDays = ledger.count { it.code == Code.SAFE_DAY && !it.at.toLocalDate().isBefore(today.minusDays(89)) }
        val drivenDays = trips.filter { !it.startedAt.toLocalDate().isBefore(today.minusDays(89)) }.map { it.startedAt.toLocalDate() }.distinct().size
        val safeRate = if (drivenDays == 0) 0.0 else safeDays.toDouble() / drivenDays
        val signal30 = signalRate(trips, today.minusDays(29)) ?: 0.0
        return when {
            total >= MileageRule.GOLD && safeRate >= MileageRule.GOLD_RATE -> Tier.GOLD
            total >= MileageRule.SILVER && signal30 >= MileageRule.SILVER_RATE -> Tier.SILVER
            else -> Tier.GREEN
        }
    }
}
