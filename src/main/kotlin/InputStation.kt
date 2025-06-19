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
    context: Context,
    coroutineScope: CoroutineScope,
    spiBus: SpiBus,
    chipSelect: SpiChipSelect,
    resetPinNum: Int,
    spiMutex: Mutex
) : InputStation {
    private val inputReader = SimpleMFRC(context, "Input $id RFID", coroutineScope, spiBus, chipSelect, resetPinNum)

    private val _placedIdFlow = flow {
        //As soon as an item is placed, emit it. Otherwise, try for 2 seconds, if no item is detected in this timeframe, switch back to null
        while (true) {
            val placedItem = withTimeoutOrNull(2000) {
                var currentItem: Int? = null
                while (currentItem == null) {
                    spiMutex.withLock {
                        currentItem = inputReader.readIdOnce()
                    }.also {
                        delay(200)
                    }
                }
                currentItem
            }
            emit(placedItem)
        }
    }

    private val encoder: RotaryEncoder = KY_040(context, "Input $id Encoder", 2, 3, 4, coroutineScope)

    override val encoderTurnFlow = encoder.turn.map { it.code }.map { EncoderEvent(id, it) }

    override val buttonPressFlow: Flow<ButtonEvent> = encoder.buttonPress.map { ButtonEvent(id) }


    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override val placedItem: StateFlow<Item?> =
        _placedIdFlow.mapLatest {
            when (it) {
                in TREE_LIST -> Item.TREE
                else -> null
            }
        }.stateIn(coroutineScope, SharingStarted.WhileSubscribed(), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val changedItemFlow: SharedFlow<RFIDEvent> = placedItem.mapLatest { RFIDEvent(id, it) }.shareIn(coroutineScope, SharingStarted.WhileSubscribed())
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
