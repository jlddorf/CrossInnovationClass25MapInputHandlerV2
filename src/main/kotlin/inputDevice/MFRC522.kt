package org.example.inputDevice

import com.pi4j.context.Context
import com.pi4j.io.gpio.GpioConfigBuilder
import com.pi4j.io.gpio.digital.DigitalOutput
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.spi.Spi
import com.pi4j.io.spi.SpiBus
import com.pi4j.io.spi.SpiChipSelect
import com.pi4j.io.spi.SpiMode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream

private val log = KotlinLogging.logger { }

interface RFIDReader {
    fun getVersion(): String
    fun writeData(): Boolean
}

class MFRC522(context: Context, id: String, val coroutineScope: CoroutineScope) : RFIDReader {
    private val spiConfig = Spi.newConfigBuilder(context).apply {
        id(id)
        name("MFRC $id")
        bus(SpiBus.BUS_0)
        mode(SpiMode.MODE_0)
        chipSelect(SpiChipSelect.CS_0)
        baud(4_000_000)
    }

    private val resetConfig = DigitalOutput.newConfigBuilder(context).apply {
        id("$id reset")
        initial(DigitalState.HIGH)
        address(25)
    }
    private val resetPin = context.create(resetConfig)


    private val spi = context.create(spiConfig)

    override fun writeData(data: Collection<Byte>): Boolean {
        //Clear the internal buffer
        val clearBytes = byteArrayOf(getByteToSend(Access.WRITE, Register.FLUSH_BUFFER), (1 shl 7).toByte())
        spi.write(clearBytes)
        //Send the data into the FIFO Register
        val toSend = mutableListOf(getByteToSend(Access.WRITE, Register.FIFO_DATA_REG))
        toSend.addAll(data)
        spi.write(toSend.toByteArray())


    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun getVersion(): String {
        coroutineScope.launch {
            resetPin.state(DigitalState.HIGH)
            delay(10)
            val toSend = byteArrayOf(getByteToSend(Access.READ, Register.VERSION), STOP_READ)
            println("Transferring with return ${spi.transfer(toSend)}")

            log.debug {
                "Received MFRC522 version: " + toSend.contentToString() + " with hex ${
                    toSend.getOrNull(1)?.toHexString()
                }"
            }
        }
        return "toSend.toString()"
    }
    private fun sendCommand(command: Command) {
        coroutineScope.launch {
            val toSend = byteArrayOf(getByteToSend(Access.WRITE, Register.COMMAND), command.commandCode)
            spi.write(toSend)
        }
    }

    private fun getByteToSend(type: Access, register: Register): Byte {
        return when (type) {
            Access.READ -> (Flag.READ_FLAG.flag or (register.address shl 1)).toByte().also { println(it) }
            Access.WRITE -> (Flag.WRITE_FLAG.flag or (register.address shl 1)).toByte()
        }
    }

    init {
        
    }

    companion object {
        enum class Register(val address: Int) {
            COMMAND(0x01),
            FIFO_DATA_REG(0x09),
            FLUSH_BUFFER(0x0A),
            VERSION(0x37),
        }

        enum class Flag(val flag: Int) {
            READ_FLAG(0x80),
            WRITE_FLAG(0x00),
            STOP_READ_FLAG(0x00),
        }

        enum class Command(val commandCode: Byte) {
            IDLE(0),
            MEM(1)
        }

        val STOP_READ: Byte = Flag.STOP_READ_FLAG.flag.toByte()

        enum class Access {
            READ, WRITE
        }
    }
}