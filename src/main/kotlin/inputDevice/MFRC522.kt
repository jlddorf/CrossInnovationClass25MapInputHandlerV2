package org.example.inputDevice

import com.pi4j.context.Context
import com.pi4j.io.gpio.digital.DigitalOutput
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.spi.Spi
import com.pi4j.io.spi.SpiBus
import com.pi4j.io.spi.SpiChipSelect
import com.pi4j.io.spi.SpiMode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

private val log = KotlinLogging.logger { }

interface RFIDReader {
    fun getVersion(): String
    fun writeData(): Boolean
}

class MFRC522(val context: Context, id: String, val coroutineScope: CoroutineScope) {
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

    private val stopAuth = ::stopCrypto1

    @OptIn(ExperimentalStdlibApi::class)
    fun getVersion(): String {
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

    private fun readRegister(register: Register): Byte {
        val transferArray = byteArrayOf(getByteToSend(Access.READ, register), STOP_READ)
        spi.transfer(transferArray)
        return transferArray.getOrNull(1) ?: throw RuntimeException("Can't access red byte at index 1")
    }

    private fun writeRegister(register: Register, toWrite: Byte) {
        val toSend = byteArrayOf(getByteToSend(Access.WRITE, register), toWrite)
        spi.transfer(toSend)
    }

    fun close() {
        spi.close()
        //TODO Close Pin connections as well
    }

    private fun clearBitMask(register: Register, mask: Byte) {
        val current = readRegister(register)
        writeRegister(register, current and mask.inv())
    }

    private fun setBitMask(register: Register, mask: Byte) {
        val current = readRegister(register)

        writeRegister(register, current or mask)
    }

    private fun reset() {
        writeRegister(Register.COMMAND, Command.RESETPHASE.code)
    }

    private fun antennaOn() {
        val currentStatus = readRegister(Register.TX_CONTROL)

        if ((currentStatus and 0x03).toInt() != 0x03) {
            setBitMask(Register.TX_CONTROL, 0x03)
        }
    }

    private fun antennaOff() {
        clearBitMask(Register.TX_CONTROL, 0x03)
    }

    private suspend fun toCard(command: Command, sendData: List<Byte>): Response {
        val data = mutableListOf<Byte>()
        val status = MutableStateFlow(Status.MI_ERR)
        val lastBits = MutableStateFlow<Byte>(0)
        val received = MutableStateFlow(0)
        val bitLength = MutableStateFlow(0)

        val irqEn = MutableStateFlow<Byte>(0x00)
        val waitIRq = MutableStateFlow<Byte>(0x00)
        if (command == Command.AUTHENT) {
            irqEn.value = 0x12
            waitIRq.value = 0x10
        }
        if (command == Command.RECEIVE) {
            irqEn.value = 0x77
            waitIRq.value = 0x30
        }

        writeRegister(Register.COMM_IEN, irqEn.value or 0x80.toByte())
        clearBitMask(Register.COMM_IRQ, 0x80.toByte())
        setBitMask(Register.FIFO_LEVEL, 0x80.toByte())

        writeRegister(Register.COMMAND, Command.IDLE.code)

        for (i in sendData) {
            writeRegister(Register.FIFO_DATA, i)
        }
        if (command == Command.TRANSCEIVE) {
            setBitMask(Register.BIT_FRAMING, 0x80.toByte())
        }
        val execution = withTimeoutOrNull(2000) {
            while (true) {
                delay(200)
                received.value = readRegister(Register.COMM_IRQ).toInt()
                if (received.value and 0x01 != 0 || received.value and waitIRq.value.toInt() != 0) {
                    break
                }
            }
            Unit
        }
        clearBitMask(Register.BIT_FRAMING, 0x80.toByte())
        if (execution != null) {
            if (readRegister(Register.ERROR) and 0x1B == 0x00.toByte()) {
                status.value = Status.MI_OK
                if (received.value and irqEn.value.toInt() and 0x01 != 0) {
                    status.value = Status.MI_NOTAGERR
                }

                if (command == Command.TRANSCEIVE) {
                    received.value = readRegister(Register.FIFO_LEVEL).toInt()
                    lastBits.value = readRegister(Register.CONTROL) and 0x07
                    if (lastBits.value != 0.toByte()) {
                        bitLength.value = (received.value - 1) * 8 + lastBits.value.toInt()
                    } else {
                        bitLength.value = received.value * 8
                    }
                    if (received.value == 0) {
                        received.value = 1
                    }
                    if (received.value > MAX_LENGTH) {
                        received.value = MAX_LENGTH
                    }
                    for (i in 0 until received.value) {
                        data.add(readRegister(Register.FIFO_DATA))
                    }
                }
            } else {
                status.value = Status.MI_ERR
            }
        }
        return Response(status.value, data, bitLength.value)
    }

    suspend fun request(reqMode: Byte): Pair<Status, Int> {
        val status = MutableStateFlow<Status>(Status.MI_OK)
        val tagType = mutableListOf<Byte>()

        writeRegister(Register.BIT_FRAMING, 0x07)
        tagType.add(reqMode)

        val response = toCard(Command.TRANSCEIVE, tagType)

        if (response.status != Status.MI_OK || response.bitLength != 0x10) {
            status.value = Status.MI_ERR
        }
        return status.value to response.bitLength
    }

    suspend fun anticoll(): Pair<Status, List<Byte>> {
        val data = mutableListOf<Byte>()
        val serNumCheck = MutableStateFlow(0)

        val serNum = mutableListOf<Byte>()
        val status = MutableStateFlow(Status.MI_ERR)

        writeRegister(Register.BIT_FRAMING, 0x00)

        serNum.add(PICC.ANTICOLL.code)
        serNum.add(0x20)

        val response = toCard(Command.TRANSCEIVE, serNum)
        status.value = response.status

        if (response.status != Status.MI_OK) {
            val i = 0
            if (response.data.size == 5) {
                for (i in 0 until 5) {
                    serNumCheck.value = serNumCheck.value xor response.data[i].toInt()
                }
                if (serNumCheck.value != response.data[4].toInt()) {
                    status.value = Status.MI_ERR
                }
            } else {
                status.value = Status.MI_ERR
            }
        }
        return status.value to data
    }

    private fun calculateCRC(pinData: List<Byte>): List<Byte> {
        clearBitMask(Register.DIV_IRQ, 0x04)
        setBitMask(Register.FIFO_LEVEL, 0x80.toByte())

        for (i in pinData) {
            writeRegister(Register.FIFO_DATA, i)
        }

        writeRegister(Register.COMMAND, Command.CALCCRC.code)
        for (i in 0xFF downTo 0) {
            val current = readRegister(Register.DIV_IRQ)
            if (current and 0x04 != 0.toByte()) {
                break
            }
        }
        val outData = mutableListOf<Byte>()
        outData.add(readRegister(Register.CRC_RESULT_L))
        outData.add(readRegister(Register.CRC_RESULT_M))
        return outData
    }

    suspend fun selectTag(serNum: List<Byte>): Int {
        val returnedData = mutableListOf<Byte>()
        val buffer = mutableListOf<Byte>()
        buffer.add(PICC.SElECTTAG.code)
        buffer.add(0x70)

        for (i in 0 until 5) {
            buffer.add(serNum[i])
        }
        val pOut = calculateCRC(buffer)
        buffer.add(pOut[0])
        buffer.add(pOut[1])

        val received = toCard(Command.TRANSCEIVE, buffer)

        if (received.status == Status.MI_OK && received.bitLength == 0x18) {
            log.debug { "Size: ${received.data[0]}" }
            return received.data[0].toInt()
        } else return 0
    }

    suspend fun authenticate(
        authMode: Byte,
        blockAddr: Byte,
        sectorKey: List<Byte>,
        serNum: List<Byte>
    ): Status {
        val buffer = mutableListOf<Byte>()

        buffer.add(authMode)

        buffer.add(blockAddr)

        for (i in sectorKey) {
            buffer.add(i)
        }

        for (i in 0 until 4) {
            buffer.add(serNum[i])
        }

        val response = toCard(Command.AUTHENT, buffer)

        if (response.status != Status.MI_OK) {
            log.error { "AUTH ERROR!!" }
        }
        if ((readRegister(Register.STATUS2) and 0x08).toInt() == 0) {
            log.error { "AUTH ERROR(status2reg & 0x08) != 0" }
        }
        return response.status
    }

    fun stopCrypto1() {
        clearBitMask(Register.STATUS2, 0x08)
    }

    suspend fun readTag(blockAddr: Byte): List<Byte>? {
        val receivedData = mutableListOf<Byte>()
        receivedData.add(PICC.READ.code)
        receivedData.add(blockAddr)

        val pOut = calculateCRC(receivedData)
        receivedData.add(pOut[0])
        receivedData.add(pOut[1])

        val response = toCard(Command.TRANSCEIVE, receivedData)

        if (response.status != Status.MI_OK) {
            log.error { "Error while reading" }
        }
        if (response.bitLength == 16) {
            log.debug { "Sector $blockAddr ${response.data}" }
            return response.data
        } else return null
    }

    private suspend fun writeTag(blockAddr: Byte, writeData: List<Byte>) {
        val buffer = mutableListOf<Byte>()
        buffer.add(PICC.WRITE.code)
        buffer.add(blockAddr)

        val check = calculateCRC(buffer)
        buffer.add(check[0])
        buffer.add(check[1])

        val response = toCard(Command.TRANSCEIVE, buffer)
        val status = MutableStateFlow(response.status)
        if (response.status != Status.MI_OK || response.bitLength != 4 && (response.data[0] and 0x0F) != 0x0A.toByte()) {
            status.value = Status.MI_ERR
        }
        log.debug { "${response.bitLength} backdata &0x0F == 0x0A ${response.data[0] and 0x0F}" }
        if (status.value == Status.MI_OK) {
            val dataBuffer = mutableListOf<Byte>()
            for (i in 0 until 16) {
                dataBuffer.add(writeData[i])
            }
            val crc = calculateCRC(dataBuffer)
            dataBuffer.add(crc[0])
            dataBuffer.add(crc[1])
            val response = toCard(Command.TRANSCEIVE, dataBuffer)
            if (response.status != Status.MI_OK || response.bitLength != 4 || (response.data[0] and 0x0F) != 0x0A.toByte()) {
                log.error { "Error while writing" }
            }
            if (response.status == Status.MI_OK) {
                log.debug { "Data written" }
            }
        }
    }

    private fun getByteToSend(type: Access, register: Register): Byte {
        return when (type) {
            Access.READ -> (Flag.READ_FLAG.flag or (register.address shl 1)).toByte()
            Access.WRITE -> (Flag.WRITE_FLAG.flag or (register.address shl 1)).toByte()
        }
    }

    init {
        reset()

        writeRegister(Register.T_MODE, 0x8D.toByte())
        writeRegister(Register.T_PRESCALER, 0x3E.toByte())
        writeRegister(Register.T_RELOAD_L, 30.toByte())
        writeRegister(Register.T_RELOAD_H, 0.toByte())

        writeRegister(Register.TX_AUTO, 0x40.toByte())
        writeRegister(Register.MODE, 0x3D.toByte())

        antennaOn()
    }

    companion object {
        enum class Register(val address: Int) {
            RESERVED00(0x00),
            COMMAND(0x01),
            COMM_IEN(0x02),
            DIVL_EN(0x03),
            COMM_IRQ(0x04),
            DIV_IRQ(0x05),
            ERROR(0x06),
            STATUS1(0x07),
            STATUS2(0x08),
            FIFO_DATA(0x09),
            FIFO_LEVEL(0x0A),
            WATER_LEVEL(0x0B),
            CONTROL(0x0C),
            BIT_FRAMING(0x0D),
            COLL(0x0E),
            RESERVED01(0x0F),

            RESERVED10(0x10),
            MODE(0x11),
            TX_MODE(0x12),
            RX_MODE(0x13),
            TX_CONTROL(0x14),
            TX_AUTO(0x15),
            TX_SEL(0x16),
            RX_SEL(0x17),
            RX_THRESHOLD(0x18),
            DEMOD(0x19),
            RESERVED11(0x1A),
            RESERVED12(0x1B),
            MIFARE(0x1C),
            RESERVED13(0x1D),
            RESERVED14(0x1E),
            SERIAL_SPEED(0x1F),

            RESERVED20(0x20),
            CRC_RESULT_M(0x21),
            CRC_RESULT_L(0x22),
            RESERVED21(0x23),
            MOD_WIDTH(0x24),
            RESERVED22(0x25),
            RFC_FG(0x26),
            GS_N(0x27),
            CWGS_P(0x28),
            MOD_GS_P(0x29),
            T_MODE(0x2A),
            T_PRESCALER(0x2B),
            T_RELOAD_H(0x2C),
            T_RELOAD_L(0x2D),
            T_COUNTER_VALUE_H(0x2E),
            T_COUNTER_VALUE_L(0x2F),

            RESERVED30(0x30),
            TEST_SEL_1(0x31),
            TEST_SEL_2(0x32),
            TEST_PIN_ENABLE(0x33),
            TEST_PIN_VALUE(0x34),
            TEST_BUS(0x35),
            AUTO_TEST(0x36),
            VERSION(0x37),
            ANALOG_TEST(0x38),
            TEST_DAC1(0x39),
            TEST_DAC2(0x3A),
            TEST_ADC(0x3B),
            RESERVED31(0x3C),
            RESERVED32(0x3D),
            RESERVED33(0x3E),
            RESERVED34(0x3F),
        }

        enum class PICC(val code: Byte) {
            REQIDL(0x26),
            REQALL(0x52),
            ANTICOLL(0x93.toByte()),
            SElECTTAG(0x93.toByte()),
            AUTHENT1A(0x60),
            AUTHENT1B(0x61),
            READ(0x30),
            WRITE(0xA0.toByte()),
            DECREMENT(0xC0.toByte()),
            INCREMENT(0xC1.toByte()),
            RESTORE(0xC2.toByte()),
            TRANSFER(0xB0.toByte()),
            HALT(0x50),
        }

        enum class Flag(val flag: Int) {
            READ_FLAG(0x80),
            WRITE_FLAG(0x00),
            STOP_READ_FLAG(0x00),
        }

        val STOP_READ: Byte = Flag.STOP_READ_FLAG.flag.toByte()

        enum class Command(val code: Byte) {
            IDLE(0x00),
            AUTHENT(0x0E),
            RECEIVE(0x08),
            TRANSMIT(0x04),
            TRANSCEIVE(0x0C),
            RESETPHASE(0x0F),
            CALCCRC(0x03),
        }

        enum class Status(val code: Int) {
            MI_OK(0),
            MI_NOTAGERR(1),
            MI_ERR(2)
        }

        enum class Access {
            READ, WRITE
        }

        val MAX_LENGTH = 16
    }
}

data class Response(val status: MFRC522.Companion.Status, val data: List<Byte>, val bitLength: Int)