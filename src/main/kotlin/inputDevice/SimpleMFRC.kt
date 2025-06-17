package org.example.inputDevice

import com.pi4j.context.Context
import com.pi4j.io.spi.SpiBus
import com.pi4j.io.spi.SpiChipSelect
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Translation of the SimpleMFRC class from the mfrc522-python library, located at https://github.com/1AdityaX/mfrc522-python/tree/master/src/mfrc522
 */

private val log = KotlinLogging.logger { }

interface RFIDReader {
    suspend fun read(): Pair<Int, String>
    suspend fun readId(): Int
    suspend fun write(text: String): Pair<Int, String>
}

class SimpleMFRC(
    context: Context,
    id: String,
    coroutineScope: CoroutineScope,
    bus: SpiBus,
    chipSelect: SpiChipSelect,
    resetPinNum: Int,
    key: List<Byte> = listOf(
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte()
    )
) : RFIDReader {
    private val reader = BasicMFRC522(context, id, coroutineScope, bus, chipSelect, resetPinNum, key)
    private val trailerBlock = 11.toByte()

    override suspend fun read(): Pair<Int, String> {
        val response = MutableStateFlow(reader.readNoBlock(trailerBlock))
        while (response.value.first == null) {
            response.value = reader.readNoBlock(trailerBlock)
        }
        return (response.value.first ?: -1) to (response.value.second ?: "")
    }

    override suspend fun readId(): Int {
        val response = MutableStateFlow(reader.readIDNoBlock())
        log.debug { "Trying to read id with result ${response.value}" }
        while (response.value == null) {
            response.value = reader.readIDNoBlock()
            log.debug { "Trying to read id with result ${response.value}" }
        }
        return response.value ?: -1
    }

    override suspend fun write(text: String): Pair<Int, String> {
        val response = MutableStateFlow(reader.writeNoBlock(text, trailerBlock))
        while (response.value.first == null) {
            response.value = reader.writeNoBlock(text, trailerBlock)
        }
        return (response.value.first ?: -1) to (response.value.second ?: "")
    }
}