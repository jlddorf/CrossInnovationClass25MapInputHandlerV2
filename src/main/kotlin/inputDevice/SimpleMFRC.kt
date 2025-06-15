package org.example.inputDevice

import com.pi4j.context.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

interface RFIDReader {
    suspend fun read(): Pair<Int, String>
    suspend fun readId(): Int
    suspend fun write(text: String): Pair<Int, String>
}

class SimpleMFRC(
    val context: Context,
    val id: String,
    val coroutineScope: CoroutineScope,
    val key: List<Byte> = listOf(
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte()
    )
) : RFIDReader {
    private val reader = BasicMFRC522(context, id, coroutineScope, key)
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
        while (response.value == null) {
            response.value = reader.readIDNoBlock()
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