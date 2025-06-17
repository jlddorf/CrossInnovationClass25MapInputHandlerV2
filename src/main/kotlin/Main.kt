package org.example

import com.pi4j.Pi4J
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.log4j.PropertyConfigurator
import org.example.inputDevice.RotaryEncoder
import org.example.inputDevice.KY_040
import org.example.inputDevice.MFRC522
import org.example.inputDevice.RFIDReader
import org.example.inputDevice.SimpleMFRC
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger { }

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main(args: Array<String>): Unit = runBlocking {
    //Configure logging
    PropertyConfigurator.configure("log4j.properties")
    val pi4j = Pi4J.newAutoContext()
/*    val encoder: RotaryEncoder = KY_040(pi4j, "p1", 17, 27, 22, this)
    launch {
        encoder.turn.collect {
            log.debug { it }
        }
    }
    launch {
        encoder.buttonPress.collect { log.debug { "Button has been pressed" } }
    }
    launch {
        encoder.turnCounter.collect { log.debug { it } }
    }*/

    delay(100.seconds)
    pi4j.shutdown()
    log.info { "Successfully shutdown program resources, exiting application" }
}
