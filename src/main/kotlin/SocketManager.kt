package org.example

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.sendSerialized
import kotlinx.coroutines.launch

fun DefaultWebSocketServerSession.launchInputControl(station: InputStation) {
    launch { station.encoderTurnFlow.collect { sendSerialized(it) } }
    launch { station.buttonPressFlow.collect { sendSerialized(it) } }
    launch { station.placedItem.collect { sendSerialized(it) } }
}