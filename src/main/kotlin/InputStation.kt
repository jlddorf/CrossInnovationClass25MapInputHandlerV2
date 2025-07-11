package org.example

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
                    if (input == "button") {
                        buttonPressFlow.emit(ButtonEvent(1))
                    }
                    else {
                        val item = when (input) {
                            "nature" -> Item.NATURE
                            "mobility" -> Item.MOBILITY
                            "energy_building" -> Item.ENERGY_BUILDING
                            "community" -> Item.COMMUNITY
                            "circular_economy" -> Item.CIRCULAR_ECONOMY
                            "local_consumption" -> Item.LOCAL_CONSUMPTION
                            else -> null
                        }
                        changedItemFlow.emit(RFIDEvent(1, item))
                    }
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
