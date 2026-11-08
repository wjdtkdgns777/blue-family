package com.example.bluefamily

import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 사람이 앉은 좌석 하나와 그 좌석의 벨트를 맸는지 */
data class SeatState(val seat: SeatValue, val buckled: Boolean)

object Drive {

    var driving by mutableStateOf(false)
        private set
    var sessionOpen by mutableStateOf(false)
        private set
    private val liveTurns = mutableListOf<TurnRecord>()
    var replaying by mutableStateOf(false)
        private set

    private lateinit var context: Context
    private lateinit var car: Car
    private lateinit var vehicle: Vehicle
    private lateinit var navi: NaviHelper
    private lateinit var voice: Voice
    private val main = Handler(Looper.getMainLooper())
    private val judge = TurnJudge(LocalTime::now, ::onTurn)
    private val unguided = UnguidedTurnJudge(LocalTime::now, ::onTurn)
    private val driverDoor = DoorArea(DoorValue.ROW_1_LEFT)
    private val seats = listOf(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)
    private val buckled = mutableStateMapOf<SeatValue, Boolean>()
    private val occupied = mutableStateMapOf(SeatValue.ROW_1_LEFT to true)

    private var started = false
    private var uxKnown = false
    private var doorOpen = false
    private var brake = false
    private var speedMps: Float? = null
    private var speedWatched = false
    private var replayStep: Runnable? = null
    private var signal: Turn? = null
    private var nextTurnM: Double? = null
    private var guided = false
    private var odometerKm: Float? = null
    private var speedLimit = 0

    private var sessionId = ""
    private var sessionStart = LocalDateTime.now()
    private var distanceM = 0.0
    private var speeding = 0
    private var hardBrakes = 0
    private var speedingSinceMs = 0L
    private var speedAtMs = 0L
    private var beltOff = 0
    private var beltedSeats = 0
    private var beltUnsafe = false

    private class Speech(val text: String, val deadlineMs: Long)

    private val speeches = ArrayDeque<Speech>()

    fun watch(appContext: Context) {
        if (::car.isInitialized) return
        context = appContext
        car = Car.createCar(context)
        val restrictions = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
        onRestrictions(restrictions.currentCarUxRestrictions.isRequiresDistractionOptimization)
        restrictions.registerListener { onRestrictions(it.isRequiresDistractionOptimization) }
    }

    fun watchSpeed() {
        if (speedWatched || context.checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) return
        @Suppress("DEPRECATION")
        speedWatched = (car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).registerCallback(speedListener, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_ONCHANGE)
    }

    fun start(appContext: Context) {
        watch(appContext)
        if (started) return
        started = true
        voice = Voice(context)
        watchSpeed()
        Store.openTrip?.let { settle(it) }

        vehicle = Vehicle(context)
        vehicle.initialize()
        vehicle.getTurnSignal().registerTurnSignalState(signalListener)
        vehicle.getBrake().registerBrakePedalStatus(brakeListener)
        vehicle.getDoor().registerDoorPosition(driverDoor, doorListener)
        vehicle.getOdometer().registerDistanceDriven(odometerListener)
        seats.forEach { seat ->
            vehicle.getSeat().registerSeatBeltBuckledState(SeatArea(seat), beltListener(seat))
            if (seat != SeatValue.ROW_1_LEFT) vehicle.getSeat().registerSeatOccupancy(SeatArea(seat), occupancyListener(seat))
        }

        navi = NaviHelper(context)
        navi.initialize({ navi.getTBTInfo() }, { Log.e(TAG, "navi", it) })
        navi.addListener(naviListener)

        main.post(tick)
    }

    // 차가 탑승을 감지하지 못하는 좌석은 값이 오지 않아 목록에서 빠집니다
    val seatStates get() = seats.filter { occupied[it] == true }.map { SeatState(it, buckled[it] == true) }

    fun replay() {
        if (replaying) return
        replaying = true
        guided = true
        val legs = listOf(TurnType.TURN_RIGHT to 320.0, TurnType.TURN_LEFT to 420.0, TurnType.TURN_RIGHT to 300.0)
        var leg = 0
        var remaining = legs[0].second
        val step = object : Runnable {
            override fun run() {
                if (leg >= legs.size) {
                    onNextTurn(TurnType.TURN_ARRIVE, 0.0)
                    stopReplay()
                    return
                }
                onNextTurn(legs[leg].first, remaining)
                remaining -= REPLAY_STEP_M
                if (remaining < 0) {
                    leg++
                    remaining = legs.getOrNull(leg)?.second ?: 0.0
                }
                main.postDelayed(this, REPLAY_TICK_MS)
            }
        }
        replayStep = step
        main.post(step)
    }

    fun stopReplay() {
        replayStep?.let(main::removeCallbacks)
        replayStep = null
        replaying = false
        guided = false
        nextTurnM = null
        judge.onRouteChanged()
    }

    fun seedHistory() {
        val today = LocalDate.now()
        for (back in 6 downTo 1) {
            val end = today.minusDays(back.toLong()).atTime(15, 10)
            val turns = List(6 + back % 3) { TurnRecord(LocalTime.of(14, 30).plusMinutes(it * 3L), if (it % 2 == 0) Turn.RIGHT else Turn.LEFT, !(back == 4 && it == 1)) }
            val trip = Trip("seed-$back", end.minusMinutes(40), end, (14 + back * 3) * 1000.0, turns, speeding = if (back == 3) 1 else 0, hardBrakes = if (back == 5) 1 else 0, beltedSeats = 1 + back % 2)
            Store.add(trip, Mileage.forTrip(trip, Store.trips.toList(), Store.ledger.toList()))
        }
    }

    private fun onRestrictions(restricted: Boolean) = main.post {
        uxKnown = true
        driving = restricted
        updateSession()
        checkBelts()
    }

    private fun updateSession() {
        if (!sessionOpen && driving) open()
        else if (sessionOpen && !driving && doorOpen) close()
    }

    private fun open() {
        sessionId = "trip-${System.currentTimeMillis()}"
        sessionStart = LocalDateTime.now()
        distanceM = 0.0
        speeding = 0
        hardBrakes = 0
        speedingSinceMs = 0L
        beltOff = 0
        beltedSeats = 0
        beltUnsafe = false
        liveTurns.clear()
        sessionOpen = true
        Store.saveOpen(snapshot())
        checkBelts()
    }

    private fun checkBelts() {
        if (!sessionOpen || !driving) return
        val seated = seats.filter { occupied[it] == true }
        if (seated.any { buckled[it] != true }) beltUnsafe = true
        beltedSeats = maxOf(beltedSeats, seated.count { buckled[it] == true })
    }

    private fun onBelt(seat: SeatValue, state: Boolean) {
        val unbuckled = buckled[seat] == true && !state
        buckled[seat] = state
        if (unbuckled && sessionOpen && driving && occupied[seat] == true) {
            beltOff++
            Store.saveOpen(snapshot())
        }
        checkBelts()
    }

    private fun snapshot() = Trip(sessionId, sessionStart, LocalDateTime.now(), distanceM, liveTurns.toList(), speeding, hardBrakes, beltOff, if (beltUnsafe) 0 else beltedSeats)

    private fun settle(trip: Trip) {
        Store.add(trip, Mileage.forTrip(trip, Store.trips.toList(), Store.ledger.toList(), familyRate()))
    }

    fun familyRate() = if (Store.settings.familyShare) Mileage.familyRate(Family.cars) else 0.0

    private fun close() {
        sessionOpen = false
        val trip = snapshot()
        val txs = Mileage.forTrip(trip, Store.trips.toList(), Store.ledger.toList(), familyRate())
        Store.add(trip, txs)
        liveTurns.clear()
        val miles = txs.sumOf { it.miles }
        val missed = trip.turns.firstOrNull { !it.onTime }?.turn
        val text = if (trip.turns.isEmpty()) context.getString(R.string.say_summary_no_turns, miles)
        else context.getString(R.string.say_summary, trip.turns.size, trip.turns.count { it.onTime }, miles)
        say(text)
        if (missed != null) say(context.getString(R.string.say_missed_tip, context.getString(if (missed == Turn.LEFT) R.string.turn_left else R.string.turn_right)))
    }

    private fun onTurn(record: TurnRecord) {
        if (!sessionOpen) return
        liveTurns += record
        Store.saveOpen(snapshot())
        if (record.onTime && Store.settings.sound) voice.chime()
    }

    private fun onNextTurn(type: TurnType?, distance: Double) {
        nextTurnM = if (type == null || type == TurnType.TURN_ARRIVE) null else distance
        judge.onNextTurn(type, distance)
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val over = sessionOpen && speedLimit > 0 && (speedMps ?: 0f) * KMH_PER_MPS > speedLimit + SPEEDING_MARGIN_KMH
            if (!over) speedingSinceMs = 0L
            else if (speedingSinceMs == 0L) speedingSinceMs = now
            else if (speedingSinceMs > 0L && now - speedingSinceMs >= SPEEDING_HOLD_MS) {
                speeding++
                speedingSinceMs = -1L
                Store.saveOpen(snapshot())
            }
            speakNext(now)
            main.postDelayed(this, TICK_MS)
        }
    }

    private fun say(text: String) {
        if (!Store.settings.voice) return
        speeches.addLast(Speech(text, SystemClock.elapsedRealtime() + SPEECH_TTL_MS))
    }

    private fun speakNext(now: Long) {
        while (speeches.isNotEmpty() && speeches.first().deadlineMs < now) speeches.removeFirst()
        val next = speeches.firstOrNull() ?: return
        val stopped = speedMps?.let { it < STOPPED_MPS } ?: false
        val clear = uxKnown && (stopped || (!brake && signal == null && (nextTurnM ?: Double.MAX_VALUE) >= QUIET_TURN_M))
        if (clear && voice.speak(next.text)) speeches.removeFirst()
    }

    private fun addDistance(km: Float) {
        val previous = odometerKm
        odometerKm = km
        if (previous == null || !sessionOpen || km <= previous) return
        val meters = (km - previous) * 1000.0
        distanceM += meters
        Store.saveOpen(snapshot())
    }

    private val signalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
            main.post {
                signal = when (state) {
                    TurnSignalValue.LEFT -> Turn.LEFT
                    TurnSignalValue.RIGHT -> Turn.RIGHT
                    else -> null
                }
                judge.onSignal(signal)
                unguided.onSignal(SystemClock.elapsedRealtime(), signal)
            }
        }

        override fun onFailed(e: Exception) = fail("signal", e)
    }

    private val speedListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val speed = value.value as? Float ?: return
            main.post { onSpeed(speed) }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) = Unit
    }

    private fun onSpeed(speed: Float) {
        val now = SystemClock.elapsedRealtime()
        val seconds = ((now - speedAtMs) / 1000f).coerceIn(MIN_SAMPLE_S, 1f)
        val drop = ((speedMps ?: speed) - speed) * KMH_PER_MPS / seconds
        if (sessionOpen && brake && drop >= HARD_BRAKE_KMH_PER_S) {
            hardBrakes++
            Store.saveOpen(snapshot())
        }
        speedMps = speed
        speedAtMs = now
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
            main.post { brake = pressed == true }
        }

        override fun onFailed(e: Exception) = fail("brake", e)
    }

    private val doorListener = object : DoorPositionListener {
        override fun onPositionUpdated(position: Int?) {
            main.post {
                doorOpen = (position ?: 0) > 0
                updateSession()
            }
        }

        override fun onFailed(e: Exception) = fail("door", e)
    }

    private fun beltListener(seat: SeatValue) = object : SeatBeltBuckledStateListener {
        override fun onSeatBeltBuckledStateUpdated(state: Boolean?) {
            if (state != null) main.post { onBelt(seat, state) }
        }

        override fun onFailed(e: Exception) = fail("belt $seat", e)
    }

    private fun occupancyListener(seat: SeatValue) = object : SeatOccupancyListener {
        override fun onSeatOccupancyUpdated(state: Boolean?) {
            if (state != null) main.post {
                // SDK 2.3.2는 표준 값 1(빈자리)을 true로 넘겨서 뒤집어 씁니다
                occupied[seat] = !state
                checkBelts()
            }
        }

        override fun onFailed(e: Exception) = fail("seat $seat", e)
    }

    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(km: Float?) {
            if (km != null) main.post { addDistance(km) }
        }

        override fun onFailed(e: Exception) = fail("odometer", e)
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onTBTInfo(list: List<TBTInfo>) {
            val next = list.firstOrNull()
            main.post { if (!replaying) onNextTurn(next?.type, next?.distance ?: 0.0) }
        }

        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            main.post {
                this@Drive.speedLimit = speedLimit
                judge.onSpeedLimit(speedLimit)
            }
        }

        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            main.post { unguided.onHeading(SystemClock.elapsedRealtime(), info.heading, sessionOpen && !guided) }
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            main.post { if (!replaying) guided = status == NavigationStatus.ROUTE_DRIVE }
        }

        override fun onReRouteEnded() {
            main.post { judge.onRouteChanged() }
        }

        override fun onRouteCancelled() {
            main.post { judge.onRouteChanged() }
        }

        override fun onRouteEnded() {
            main.post { judge.onRouteChanged() }
        }
    }

    private fun fail(what: String, e: Exception) {
        Log.e(TAG, what, e)
    }

    private const val TAG = "BlueFamily"
    private const val TICK_MS = 1_000L
    private const val SPEECH_TTL_MS = 120_000L
    private const val QUIET_TURN_M = 500.0
    private const val STOPPED_MPS = 0.5f
    private const val KMH_PER_MPS = 3.6f
    private const val SPEEDING_MARGIN_KMH = 20
    private const val SPEEDING_HOLD_MS = 3_000L
    private const val HARD_BRAKE_KMH_PER_S = 11f
    private const val MIN_SAMPLE_S = 0.1f
    private const val REPLAY_STEP_M = 8.0
    private const val REPLAY_TICK_MS = 500L
}
