package org.example

import com.pi4j.Pi4J
import com.pi4j.io.gpio.digital.DigitalInput
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.gpio.digital.PullResistance

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    val pi4j = Pi4J.newAutoContext()
    val buttonConfig = DigitalInput.newConfigBuilder(pi4j).apply {
        id("button")
        name("Press button")
        address(22)
        pull(PullResistance.PULL_DOWN)
        debounce(3000L)
    }
    val button = pi4j.create(buttonConfig)
    button.addListener({ e ->
        if (e.state() == DigitalState.LOW) {
            println("Button was pressed")
        }
    })
    readln()
    pi4j.shutdown()
}