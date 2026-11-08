package com.example.bluefamily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MileageTest {

    private val day = LocalDate.of(2026, 11, 1)
    private val noon = LocalDateTime.of(day, LocalTime.NOON)

    private fun trip(id: String = "t", at: LocalDateTime = noon, km: Double = 21.0, onTime: Int = 0, late: Int = 0) = Trip(
        id, at.minusMinutes(30), at, km * 1000,
        List(onTime) { TurnRecord(LocalTime.NOON, Turn.LEFT, true) } + List(late) { TurnRecord(LocalTime.NOON, Turn.RIGHT, false) },
    )

    private fun miles(txs: List<Tx>, code: Code) = txs.filter { it.code == code }.sumOf { it.miles }

    @Test
    fun turnsDistanceAndSafeDay() {
        val txs = Mileage.forTrip(trip(onTime = 5), emptyList(), emptyList())
        assertEquals(25, miles(txs, Code.TURN))
        assertEquals(21, miles(txs, Code.DISTANCE))
        assertEquals(20, miles(txs, Code.SAFE_DAY))
    }

    @Test
    fun anyPenaltyLosesTheSafeDay() {
        assertEquals(0, miles(Mileage.forTrip(trip(onTime = 5, late = 1), emptyList(), emptyList()), Code.SAFE_DAY))
        assertEquals(0, miles(Mileage.forTrip(trip().copy(speeding = 1), emptyList(), emptyList()), Code.SAFE_DAY))
        assertEquals(0, miles(Mileage.forTrip(trip().copy(hardBrakes = 1), emptyList(), emptyList()), Code.SAFE_DAY))
        assertEquals(0, miles(Mileage.forTrip(trip().copy(beltOff = 1), emptyList(), emptyList()), Code.SAFE_DAY))
    }

    @Test
    fun earlierPenaltyTodayLosesTheSafeDay() {
        val history = listOf(trip("h", noon.minusHours(2)).copy(hardBrakes = 1))
        assertEquals(0, miles(Mileage.forTrip(trip(), history, emptyList()), Code.SAFE_DAY))
    }

    @Test
    fun safeWeekDoublesDistance() {
        val history = listOf(trip("h", noon.minusDays(1), onTime = 20))
        val txs = Mileage.forTrip(trip(km = 10.0), history, emptyList())
        assertEquals(20, miles(txs, Code.DISTANCE))
    }

    @Test
    fun familyBundleAddsBonusOnTopOfEarned() {
        val txs = Mileage.forTrip(trip(onTime = 5, late = 1), emptyList(), emptyList(), Mileage.familyRate(4))
        assertEquals(14, miles(txs, Code.FAMILY))
    }

    @Test
    fun allBeltsBuckledEarnsPerSeat() {
        val txs = Mileage.forTrip(trip().copy(beltedSeats = 3), emptyList(), emptyList())
        assertEquals(20, miles(txs, Code.BELT))
    }

    @Test
    fun beltOffEarnsNoBeltMiles() {
        val txs = Mileage.forTrip(trip().copy(beltedSeats = 2, beltOff = 1), emptyList(), emptyList())
        assertEquals(0, miles(txs, Code.BELT))
    }

    @Test
    fun familyRateGrowsPerCarUpToThirtyPercent() {
        assertEquals(0.0, Mileage.familyRate(1), 1e-9)
        assertEquals(0.1, Mileage.familyRate(2), 1e-9)
        assertEquals(0.3, Mileage.familyRate(6), 1e-9)
    }

    @Test
    fun scoreDeductsPenaltiesAndStopsAtZero() {
        assertEquals(79, Mileage.score(speeding = 2, hardBrakes = 1, missedSignals = 1, beltOff = 1))
        assertEquals(0, Mileage.score(speeding = 30, hardBrakes = 0, missedSignals = 0, beltOff = 0))
    }

    @Test
    fun dailyCapStopsAtThreeHundred() {
        val ledger = listOf(Tx("old", noon.minusHours(1), Code.DISTANCE, 290, 290))
        val txs = Mileage.forTrip(trip(onTime = 4), emptyList(), ledger)
        assertEquals(10, txs.sumOf { it.miles })
    }

    @Test
    fun sameTripIsNotPaidTwice() {
        val first = Mileage.forTrip(trip(onTime = 2), emptyList(), emptyList())
        val again = Mileage.forTrip(trip(onTime = 2), emptyList(), first)
        assertTrue(again.none { tx -> first.any { it.id == tx.id } })
    }

    @Test
    fun seventhSafeDayGivesStreak() {
        val ledger = (1..6).map { Tx("k$it", noon.minusDays(it.toLong()), Code.SAFE_DAY, 20, 1) }
        val txs = Mileage.forTrip(trip(), emptyList(), ledger)
        assertEquals(100, miles(txs, Code.STREAK))
    }

    @Test
    fun silverNeedsMilesAndSignalRate() {
        val ledger = listOf(Tx("a", noon, Code.DISTANCE, 3000, 3000))
        assertEquals(Tier.SILVER, Mileage.tier(ledger, listOf(trip(onTime = 9, late = 1)), day))
        assertEquals(Tier.GREEN, Mileage.tier(ledger, listOf(trip(onTime = 8, late = 2)), day))
    }
}
