package org.example

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
import kotlinx.serialization.json.Json
import org.apache.log4j.PropertyConfigurator

private val log = KotlinLogging.logger { }

fun main(args: Array<String>): Unit {
    //Configure logging
    PropertyConfigurator.configure("log4j.properties")

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
                val player1 = InputStationImpl(1, this)
                launchInputControl(player1)
                for (frame in incoming) {
                    if (frame as? Frame.Text != null && frame.readText() == "close") {
                        close()
                    }
                }
            }
            webSocket("/input/2") {
               // val player2 = InputStationImpl(2, this)
               // launchInputControl(player2)
                for (frame in incoming) {
                    if (frame as? Frame.Text != null && frame.readText() == "close") {
                        close()
                    }
                }
            }
            monitor.subscribe(ApplicationStopped) {
                log.info { "Closing Server, shutting down Resources" }
                log.info { "Successfully shutdown program resources, exiting application" }
            }
        }
    }.start(wait = true)
}
