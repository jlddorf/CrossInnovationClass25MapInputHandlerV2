package org.example.inputDevice

import com.pi4j.context.Context
import com.pi4j.io.gpio.digital.DigitalInput
import com.pi4j.io.gpio.digital.DigitalState
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

private val log = KotlinLogging.logger { }

enum class Direction {
    CLOCKWISE, ANTI_CLOCKWISE
}

interface RotaryEncoder {
    val turn: Flow<Direction>
    val buttonPress: Flow<Unit>
    val turnCounter: StateFlow<Int>
}

class RotaryEncoderImpl(
    context: Context,
    id: String,
    clkPinNumber: Int,
    dtPinNumber: Int,
    coroutineScope: CoroutineScope
) : RotaryEncoder {
    private val _turnCounter = MutableStateFlow(0)
    private val _turn = MutableSharedFlow<Direction>()
    private val _buttonPress = MutableSharedFlow<Unit>()

    override val turnCounter: StateFlow<Int> = _turnCounter.asStateFlow()
    override val turn: Flow<Direction> = _turn.asSharedFlow()
    override val buttonPress: Flow<Unit> = _buttonPress.asSharedFlow()

    private val isSecond = MutableStateFlow(false)
    private val clkConfig = DigitalInput.newConfigBuilder(context).apply {
        id("${id}CLK")
        name("$id CLK Pin")
        address(clkPinNumber)
        pull(PullResistance.PULL_UP)
        debounce(50L)
    }
    private val dtConfig = DigitalInput.newConfigBuilder(context).apply {
        id("${id}DT")
        name("$id DT Pin")
        address(dtPinNumber)
        pull(PullResistance.PULL_UP)
        debounce(50L)
    }
    private val clkPin = context.create(clkConfig)
    private val dtPin = context.create(dtConfig)

    private val buttonConfig = DigitalInput.newConfigBuilder(context).apply {
        id("button")
        name("Press button")
        address(22)
        pull(PullResistance.PULL_DOWN)
        debounce(3000L)
    }

    private val button = context.create(buttonConfig)

    init {
        clkPin.addListener({ e ->
            if (isSecond.value) {
                if (e.state() != dtPin.state()) {
                    coroutineScope.launch {
                        _turnCounter.value = _turnCounter.value + 1
                        _turn.emit(Direction.CLOCKWISE)
                    }
                } else {
                    coroutineScope.launch {
                        _turnCounter.value = _turnCounter.value - 1
                        _turn.emit(Direction.ANTI_CLOCKWISE)
                    }
                }
            }
            isSecond.value = !isSecond.value
        })
        button.addListener({ e ->
            if (e.state() == DigitalState.LOW) {
                coroutineScope.launch {
                    _buttonPress.emit(Unit)
                }
            }
        })
    }
}