package org.example

import com.pi4j.Pi4J
import org.example.inputDevice.RotaryEncoder

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
    fun main(args: Array<String>) {
        val pi4j = Pi4J.newAutoContext()
        val encoder = RotaryEncoder(pi4j, "p1", 17, 27)
        readln()
        pi4j.shutdown()
    }
