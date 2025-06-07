package org.example.inputDevice

import com.pi4j.context.Context
import com.pi4j.io.spi.Spi
import com.pi4j.io.spi.SpiBus
import com.pi4j.io.spi.SpiChipSelect
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope

private val log = KotlinLogging.logger { }

interface RFIDReader{
    fun getVersion() : String
}

class MFRC522(context: Context, id: String, coroutineScope: CoroutineScope) : RFIDReader {
    val spiConfig = Spi.newConfigBuilder(context).apply {
        id(id)
        name("MFRC $id")
        writeLsbFirst(0)
        readLsbFirst(0)
        bus(SpiBus.BUS_0)
        chipSelect(SpiChipSelect.CS_0)
        baud(10_000_000)
    }

    private val spi = context.create(spiConfig)

    override fun getVersion() : String {
        val toSend = byteArrayOf(getByteToSend(Access.READ, Register.VERSION), STOP_READ)
        spi.transfer(toSend)
        log.debug { "Received MFRC522 version: $toSend" }
        return toSend.toString()
    }

    private fun getByteToSend(type: Access, register: Register): Byte {
        return when (type) {
            Access.READ -> (Flag.READ_FLAG.flag or register.address shl 1).toByte()
            Access.WRITE -> (Flag.WRITE_FLAG.flag or register.address shl 1).toByte()
        }
    }

    companion object {
        enum class Register(val address: Int) {
            VERSION(0x37)
        }

        enum class Flag(val flag: Int) {
            READ_FLAG(0x80),
            WRITE_FLAG(0x00),
            STOP_READ_FLAG(0x00),
        }

        val STOP_READ: Byte = Flag.STOP_READ_FLAG.flag.toByte()

        enum class Access {
            READ, WRITE
        }
    }
}