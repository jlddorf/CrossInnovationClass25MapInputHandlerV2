package org.example

import com.pi4j.Pi4J
import com.pi4j.io.spi.SpiBus
import com.pi4j.io.spi.SpiChipSelect
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import org.apache.log4j.PropertyConfigurator
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger { }

fun main(args: Array<String>): Unit {
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
    val spiMutex = Mutex()

    embeddedServer(Netty, 8090, host = "0.0.0.0") {
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 15.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
            contentConverter = KotlinxWebsocketSerializationConverter(Json { encodeDefaults = true })
        }
        routing {
            monitor.subscribe(ApplicationStarted) {
                log.info { "Server started" }
            }
            webSocket("/input/1") {
                val player1 = InputStationImpl(1, pi4j, this, SpiBus.BUS_0, 0, 22, 2, 3, 17, spiMutex)
                launchInputControl(player1)
                for (frame in incoming) {
                    if (frame as? Frame.Text != null && frame.readText() == "close") {
                        close()
                    }
                }
            }
            monitor.subscribe(ApplicationStopped) {
                log.info { "Closing Server, shutting down Resources" }
                pi4j.shutdown()
                log.info { "Successfully shutdown program resources, exiting application" }
            }
        }
    }.start(wait = true)
}
