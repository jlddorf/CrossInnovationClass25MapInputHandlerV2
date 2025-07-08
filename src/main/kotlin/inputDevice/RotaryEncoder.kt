package org.example.inputDevice

import com.pi4j.context.Context
import com.pi4j.io.gpio.digital.DigitalInput
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.gpio.digital.DigitalStateChangeEvent
import com.pi4j.io.gpio.digital.DigitalStateChangeListener
import com.pi4j.io.gpio.digital.PullResistance
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

private val log = KotlinLogging.logger { }

enum class Direction(val code: Int) {
    CLOCKWISE(1), ANTI_CLOCKWISE(-1)
}

interface RotaryEncoder {
    val turn: Flow<Direction>
    val buttonPress: Flow<Unit>
    val turnCounter: StateFlow<Int>
}

class KY_040(
    context: Context,
    id: String,
    clkPinNumber: Int,
    dtPinNumber: Int,
    swPinNumber: Int,
    val coroutineScope: CoroutineScope
) : RotaryEncoder {
    private val _turnCounter = MutableStateFlow(0)
    private val _turn = MutableSharedFlow<Direction>()
    private val _buttonPress = MutableSharedFlow<Unit>()

    override val turnCounter: StateFlow<Int> = _turnCounter.asStateFlow()
    override val turn: Flow<Direction> = _turn.asSharedFlow()
    override val buttonPress: Flow<Unit> = _buttonPress.asSharedFlow()

    private val isSecond = MutableStateFlow(false)

    private val debounceTimer = 20L

    private val clkConfig = DigitalInput.newConfigBuilder(context).apply {
        id("${id}CLK")
        name("$id CLK Pin")
        address(clkPinNumber)
        pull(PullResistance.PULL_UP)
        debounce(debounceTimer)
    }
    private val dtConfig = DigitalInput.newConfigBuilder(context).apply {
        id("${id}DT")
        name("$id DT Pin")
        address(dtPinNumber)
        pull(PullResistance.PULL_UP)
        debounce(debounceTimer)
    }
    private val clkPin = context.create(clkConfig)
    private val dtPin = context.create(dtConfig)

    private val buttonConfig = DigitalInput.newConfigBuilder(context).apply {
        id("$id button")
        name("$id Button")
        address(swPinNumber)
        pull(PullResistance.PULL_DOWN)
        debounce(3000L)
    }

    private val button = context.create(buttonConfig)
    private val encoderLock = Mutex()

    init {

        clkPin.addListener({ change ->
            coroutineScope.launch {
                encoderLock.withLock {
                    handleEncoderChanges()
                }
            }
        })
        dtPin.addListener({ change ->
            coroutineScope.launch {
                encoderLock.withLock {
                    handleEncoderChanges()
                }
            }
        })
        button.addListener({ e ->
            if (e.state() == DigitalState.LOW) {
                coroutineScope.launch {
                    _buttonPress.emit(Unit)
                }
            }
        })
        log.info { "Successfully initialized rotary encoder with id $id" }
    }

    private var lastEncoded: Int = 0

    private var called = 0

    private fun handleEncoderChanges() {
        val clkState = if (clkPin.state() == DigitalState.HIGH) 1 else 0
        val dtState = if (dtPin.state() == DigitalState.HIGH) 1 else 0

        val encoded = (clkState shl 1) or dtState
        val sum = (lastEncoded shl 2) or encoded

        when (sum) {
            0b1101, 0b0100, 0b0010, 0b1011 -> {
                // Clockwise sequence
                coroutineScope.launch {
                    called += 1
                    if (called == 4) {
                        _turnCounter.value = _turnCounter.value + 1
                        _turn.emit(Direction.CLOCKWISE)
                        called = 0
                    }
                }
            }

            0b1110, 0b0111, 0b0001, 0b1000 -> {
                // Counter-clockwise sequence
                coroutineScope.launch {
                    called += 1
                    if (called == 4) {
                        _turnCounter.value = _turnCounter.value - 1
                        _turn.emit(Direction.ANTI_CLOCKWISE)
                        called = 0
                    }
                }
            }
        }
        lastEncoded = encoded
    }
}