package org.example

import com.pi4j.context.Context
import com.pi4j.io.spi.SpiBus
import com.pi4j.io.spi.SpiChipSelect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.inputDevice.Direction
import org.example.inputDevice.KY_040
import org.example.inputDevice.RotaryEncoder
import org.example.inputDevice.SimpleMFRC

interface InputStation {
    val placedItem: StateFlow<Item?>
    val encoderTurnFlow: Flow<Direction>
    val buttonPressFlow: Flow<Unit>
}

class InputStationImpl(
    id: String,
    context: Context,
    coroutineScope: CoroutineScope,
    spiBus: SpiBus,
    chipSelect: SpiChipSelect,
    resetPinNum: Int,
    spiMutex: Mutex
) : InputStation {
    private val inputReader = SimpleMFRC(context, "$id RFID", coroutineScope, spiBus, chipSelect, resetPinNum)

    val _placedIdFlow = flow {
        while (true) {
            spiMutex.withLock {
                emit(inputReader.readId())
            }
            delay(200)
        }
    }

    val encoder: RotaryEncoder = KY_040(context, "$id Encoder",  17, 27, 22, coroutineScope)

    override val encoderTurnFlow = encoder.turn

    override val buttonPressFlow: Flow<Unit> = encoder.buttonPress

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override val placedItem: StateFlow<Item?> =
        _placedIdFlow.mapLatest {
            when (it) {
                1, 2 -> Item.TREE
                else -> null
            }
        }.sample(300).stateIn(coroutineScope, SharingStarted.WhileSubscribed(), null)

}