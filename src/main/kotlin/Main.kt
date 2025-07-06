package org.example

import com.pi4j.Pi4J
import com.pi4j.io.spi.SpiBus
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
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.apache.log4j.PropertyConfigurator

private val log = KotlinLogging.logger { }

fun main(args: Array<String>): Unit {
    //Configure logging
    PropertyConfigurator.configure("log4j.properties")
    val pi4j = Pi4J.newAutoContext()
    val spiMutex = Mutex()

    embeddedServer(Netty, 8090, host = "0.0.0.0") {
        install(WebSockets) {
            maxFrameSize = Long.MAX_VALUE
            masking = false
            contentConverter = KotlinxWebsocketSerializationConverter(Json { encodeDefaults = true })
        }
        routing {
            monitor.subscribe(ApplicationStarted) {
                log.info { "Server started" }
            }
            webSocket("/input/1") {
                val player1 = spiMutex.withLock {
                    InputStationImpl(1, pi4j, this, SpiBus.BUS_0, 0, 16, 14, 15, 18, spiMutex)
                }
                launchInputControl(player1)
                for (frame in incoming) {
                    if (frame as? Frame.Text != null && frame.readText() == "close") {
                        close()
                    }
                }
            }
            webSocket("/input/2") {
                val player2 = spiMutex.withLock {
                    InputStationImpl(2, pi4j, this, SpiBus.BUS_0, 1, 20, 13, 19, 26, spiMutex)
                }
                launchInputControl(player2)
                for (frame in incoming) {
                    if (frame as? Frame.Text != null && frame.readText() == "close") {
                        close()
                    }
                }
            }/*
            webSocket("/input/3") {
                val player3 =
                    spiMutex.withLock { InputStationImpl(3, pi4j, this, SpiBus.BUS_0, 2, 21, 17, 27, 22, spiMutex) }
                launchInputControl(player3)
                for (frame in incoming) {
                    if (frame as? Frame.Text != null && frame.readText() == "close") {
                        close()
                    }
                }
            }*/
            monitor.subscribe(ApplicationStopped) {
                log.info { "Closing Server, shutting down Resources" }
                pi4j.shutdown()
                log.info { "Successfully shutdown program resources, exiting application" }
            }
        }
    }.start(wait = true)
}
