package org.example

import com.pi4j.context.Context
import com.pi4j.io.spi.SpiBus
import com.pi4j.io.spi.SpiChipSelect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.example.inputDevice.KY_040
import org.example.inputDevice.RotaryEncoder
import org.example.inputDevice.SimpleMFRC

interface InputStation {
    val placedItem: StateFlow<Item?>
    val changedItemFlow: SharedFlow<RFIDEvent>
    val encoderTurnFlow: Flow<EncoderEvent>
    val buttonPressFlow: Flow<ButtonEvent>
}

private val TREE_LIST = listOf(63001119)

class InputStationImpl(
    id: Int,
    coroutineScope: CoroutineScope
) : InputStation {


    override val encoderTurnFlow = MutableSharedFlow<EncoderEvent>()

    override val buttonPressFlow = MutableSharedFlow<ButtonEvent>()


    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override val placedItem: StateFlow<Item?> =MutableStateFlow(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    override val changedItemFlow = MutableSharedFlow<RFIDEvent>()
    init {
        coroutineScope.launch {
            while (true) {
                val input = readln()
                try {
                    encoderTurnFlow.emit(EncoderEvent(1, input.toInt()))
                }
                catch (e: Exception) {
                    val item = when (input) {
                        "tree" -> Item.TREE
                        else -> null
                    }
                    changedItemFlow.emit(RFIDEvent(1, item))
                }
                delay(200)
            }
        }
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
