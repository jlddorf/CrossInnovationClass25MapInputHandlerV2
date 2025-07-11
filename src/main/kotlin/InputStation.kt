package org.example

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.example.inputDevice.Direction
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseWheelEvent
import javax.swing.JFrame

interface InputStation {
    val placedItem: StateFlow<Item?>
    val changedItemFlow: SharedFlow<RFIDEvent>
    val encoderTurnFlow: Flow<EncoderEvent>
    val buttonPressFlow: Flow<ButtonEvent>
}

private val NATURE_LIST = listOf<Int>(63001119)
private val MOBILITY_LIST = listOf<Int>()
private val ENERGY_BUILDING_LIST = listOf<Int>()
private val COMMUNITY_LIST = listOf<Int>()
private val CIRCULAR_ECONOMY_LIST = listOf<Int>()
private val LOCAL_CONSUMPTION_LIST = listOf<Int>()

class InputStationImpl(
    id: Int,
    coroutineScope: CoroutineScope
) : InputStation {


    override val encoderTurnFlow = MutableSharedFlow<EncoderEvent>()

    override val buttonPressFlow = MutableSharedFlow<ButtonEvent>()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override val placedItem: StateFlow<Item?> = MutableStateFlow(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val changedItemFlow = MutableSharedFlow<RFIDEvent>()

    init {
        var currentItem = 0
        val frame = JFrame("Adapter")
        frame.isVisible = true
        val mouseAdapter = object: MouseAdapter() {
            override fun mouseWheelMoved(e: MouseWheelEvent?) {
                e?.wheelRotation?.let {
                    if (it > 0) {
                        coroutineScope.launch {
                            encoderTurnFlow.emit(EncoderEvent(1, 1))
                        }
                    }
                    else if (it < 0) {
                        coroutineScope.launch {
                            encoderTurnFlow.emit(EncoderEvent(1, -1))
                        }
                    }
                }
            }
        }
        frame.addMouseListener(mouseAdapter)
        val adapter: KeyAdapter = object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent?) {
                when (e?.keyCode) {
                    KeyEvent.VK_LEFT -> {
                        currentItem = (currentItem - 1)
                        if (currentItem < 0) {
                            currentItem = Item.entries.size - 1
                        }
                        coroutineScope.launch {
                            changedItemFlow.emit(RFIDEvent(1, Item.entries.get(currentItem)))
                        }
                    }

                    KeyEvent.VK_RIGHT -> {
                        currentItem = (currentItem + 1) % Item.entries.size
                        coroutineScope.launch {
                            changedItemFlow.emit(RFIDEvent(1, Item.entries.get(currentItem)))
                        }
                    }

                    KeyEvent.VK_UP -> {
                        coroutineScope.launch {
                            encoderTurnFlow.emit(EncoderEvent(1, 1))
                        }
                    }
                    KeyEvent.VK_DOWN -> {
                        coroutineScope.launch {
                            encoderTurnFlow.emit(EncoderEvent(1, -1))
                        }
                    }
                    KeyEvent.VK_SPACE -> {
                        coroutineScope.launch {
                            buttonPressFlow.emit(ButtonEvent(1))
                            buttonPressFlow.emit(ButtonEvent(2))                        }
                    }
                }
            }
        }
        frame.addKeyListener(adapter)
    }
}

@Serializable
enum class Device {
    @SerialName("encoder")
    ENCODER,

    @SerialName("encoder_button")
    ENCODER_BUTTON,

    @SerialName("button")
    BUTTON,

    @SerialName("rfid_reader")
    RFID
}

interface InputEvent {
    @SerialName("device_type")
    val deviceType: Device

    @SerialName("device_id")
    val deviceId: Int
}

@Serializable
data class EncoderEvent(
    @SerialName("device_id")
    override val deviceId: Int,
    @SerialName("encoder_direction")
    val directionCode: Int
) : InputEvent {
    @SerialName("device_type")
    override val deviceType: Device = Device.ENCODER
}

@Serializable
data class ButtonEvent(
    @SerialName("device_id")
    override val deviceId: Int,
) : InputEvent {
    @SerialName("device_type")
    override val deviceType: Device = Device.BUTTON

    @SerialName("button_pressed")
    val buttonPressed: Boolean = true
}

@Serializable
data class RFIDEvent(
    @SerialName("device_id")
    override val deviceId: Int,
    @SerialName("rfid_content")
    val item: Item?
) : InputEvent {
    @SerialName("device_type")
    override val deviceType: Device = Device.RFID
}
