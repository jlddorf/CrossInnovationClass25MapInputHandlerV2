package org.example

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.sendSerialized
import kotlinx.coroutines.launch

private val log = KotlinLogging.logger { }

fun DefaultWebSocketServerSession.launchInputControl(station: InputStation) {
    launch {
        station.encoderTurnFlow.collect {
            log.trace { "Sending $it via station $station" }
            sendSerialized(it)
        }
    }
    launch {
        station.buttonPressFlow.collect {
            log.trace { "Sending $it via station $station" }
            sendSerialized(it)
        }
    }
    launch {
        station.changedItemFlow.collect {
            log.trace { "Sending $it via station $station" }
            sendSerialized(it)
        }
    }
}