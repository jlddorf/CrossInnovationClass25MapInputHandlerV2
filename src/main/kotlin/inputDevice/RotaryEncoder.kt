package org.example.inputDevice

import com.pi4j.context.Context
import com.pi4j.io.gpio.digital.DigitalInput
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.gpio.digital.PullResistance

class RotaryEncoder(context: Context, id: String, clkPinNumber: Int, dtPinNumber: Int) {
    var counter = 0

    private var isSecond = false
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
            if (isSecond) {
                if (e.state() != dtPin.state()) {
                    counter++
                } else {
                    counter--
                }
                println(counter)
            }
            isSecond = !isSecond
        })
        button.addListener({ e ->
            if (e.state() == DigitalState.LOW) {
                println("Button was pressed")
            }
        })
    }
}