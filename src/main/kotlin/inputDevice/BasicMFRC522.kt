package org.example.inputDevice

import com.pi4j.context.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

class BasicMFRC522(
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
) {
    private val reader = MFRC522(context, id, coroutineScope)

    suspend fun readSector(trailerBlock: Byte): Pair<Int, String> {
        val response = MutableStateFlow(readNoBlock(trailerBlock))
        while (response.value.first == null) {
            response.value = readNoBlock(trailerBlock)
        }
        return (response.value.first ?: -1) to (response.value.second ?: "")
    }

    suspend fun readSectors(trailerBlocks: List<Byte>): Pair<Int, String> {
        val text = StringBuilder()
        val response = MutableStateFlow<Pair<Int, String>?>(null)
        for (i in trailerBlocks) {
            response.value = readSector(i)
            text.append(response.value?.second)
        }
        return (response.value?.first ?: -1) to text.toString()
    }

    suspend fun readID(): Int {
        val id = MutableStateFlow(readIDNoBlock())
        while (id.value == null) {
            id.value = readIDNoBlock()
        }
        return id.value ?: -1
    }

    suspend fun readIDNoBlock(): Int? {
        val response = reader.request(MFRC522.Companion.PICC.REQIDL.code)
        if (response.first != MFRC522.Companion.Status.MI_OK) {
            return null
        }
        val antiColl = reader.anticoll()
        if (antiColl.first != MFRC522.Companion.Status.MI_OK) {
            return null
        }
        return uidToNum(antiColl.second)
    }

    suspend fun readNoBlock(trailerBlock: Byte): Pair<Int?, String?> {
        if (!checkTrailerBlock(trailerBlock)) {
            throw IllegalArgumentException("Invalid trailer block $trailerBlock")
        }
        val address = listOf(
            trailerBlock.toUByte() - 3.toUByte(), trailerBlock.toUByte() - 2.toUByte(),
            trailerBlock.toUByte() - 1.toUByte()
        )

        val response = reader.request(MFRC522.Companion.PICC.REQIDL.code)
        if (response.first != MFRC522.Companion.Status.MI_OK) {
            return null to null
        }
        val antiColl = reader.anticoll()
        if (antiColl.first != MFRC522.Companion.Status.MI_OK) {
            return null to null
        }
        val id = uidToNum(antiColl.second)
        reader.selectTag(antiColl.second)

        val status = reader.authenticate(MFRC522.Companion.PICC.AUTHENT1A.code, trailerBlock, key, antiColl.second)

        val data = mutableListOf<Byte>()
        val text = StringBuilder()
        try {
            if (status == MFRC522.Companion.Status.MI_OK) {
                for (i in address) {
                    val block = reader.readTag(i.toByte())
                    if (block != null) {
                        data.addAll(block)
                    }
                }
                if (data.isNotEmpty()) {
                    text.append(data.joinToString(""))
                }
            }
            reader.stopCrypto1()
            return id to text.toString()
        } catch (e: Exception) {
            reader.stopCrypto1()
            return null to null
        }
    }

    suspend fun writeSector(text: String, trailerBlock: Byte): Pair<Int, String> {
        val result = MutableStateFlow(writeNoBlock(text, trailerBlock))

        while (result.value.first == null) {
            result.value = writeNoBlock(text, trailerBlock)
        }
        return (result.value.first ?: -1) to (result.value.second ?: "")
    }

    suspend fun writeSectors(text: String, trailerBlocks: List<Byte>): Pair<Int, String> {
        val textList = splitString(text)
        val resultText = StringBuilder()

        val id = MutableStateFlow<Int?>(null)
        for (i in 0 until trailerBlocks.size) {
            try {
                val response = writeSector(textList[i], trailerBlocks[i])
                resultText.append(response.second)
                id.value = response.first
            } catch (e: IndexOutOfBoundsException) {
                continue
            }
        }
        return (id.value ?: -1) to resultText.toString()
    }

    suspend fun writeNoBlock(text: String, trailerBlock: Byte): Pair<Int?, String?> {
        if (!checkTrailerBlock(trailerBlock)) {
            throw IllegalArgumentException("Invalid trailer block $trailerBlock")
        }
        val address = listOf(
            trailerBlock.toUByte() - 3.toUByte(), trailerBlock.toUByte() - 2.toUByte(),
            trailerBlock.toUByte() - 1.toUByte()
        )

        val response = reader.request(MFRC522.Companion.PICC.REQIDL.code)
        if (response.first != MFRC522.Companion.Status.MI_OK) {
            return null to null
        }
        val antiColl = reader.anticoll()
        if (antiColl.first != MFRC522.Companion.Status.MI_OK) {
            return null to null
        }
        val id = uidToNum(antiColl.second)
        reader.selectTag(antiColl.second)

        val status = reader.authenticate(MFRC522.Companion.PICC.AUTHENT1A.code, trailerBlock, key, antiColl.second)

        reader.readTag(trailerBlock)

        try {
            if (status == MFRC522.Companion.Status.MI_OK) {
                val data = Charsets.US_ASCII.encode(text.padEnd(address.size * 16)).array()
                val i = MutableStateFlow(0)
                for (blockNum in address) {
                    reader.writeTag(blockNum.toByte(), data.copyOfRange(i.value * 16, (i.value + 1) * 16).toList())
                    i.value = i.value + 1
                }
            }
            reader.stopCrypto1()
            return id to text.substring(0, address.size * 16)
        } catch (e: Exception) {
            reader.stopCrypto1()
            return null to null
        }

    }

    private fun checkTrailerBlock(trailerBlock: Byte): Boolean {
        return (trailerBlock.toInt() + 1) % 4 == 0
    }

    private fun uidToNum(uid: List<Byte>): Int {
        val n = MutableStateFlow(0)
        for (i in 0 until 5) {
            n.value = n.value * 256 * uid[i]
        }
        return n.value
    }

    private fun splitString(string: String): List<String> {
        val list = string.chunked(48).toMutableList()
        if (list[list.lastIndex].length < 48) {
            list[list.lastIndex] += Char.MIN_VALUE.toString().repeat(48 - list[list.lastIndex].length)
        }
        return list
    }
}