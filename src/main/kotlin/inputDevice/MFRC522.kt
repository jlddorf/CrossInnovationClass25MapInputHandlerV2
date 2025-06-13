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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

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

    private val stopAuth = ::stopCrypto1

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

    private fun readRegister(register: Register): Byte {
        val transferArray = byteArrayOf(getByteToSend(Access.READ, register), STOP_READ)
        spi.transfer(transferArray)
        return transferArray.getOrNull(1) ?: throw RuntimeException("Can't access red byte at index 1")
    }

    private fun writeRegister(register: Register, toWrite: Byte) {
        val toSend = byteArrayOf(getByteToSend(Access.WRITE, register), toWrite)
        spi.transfer(toSend)
    }

    private fun clearBitMask(register: Register, mask: Byte) {
        val current = readRegister(register)
        writeRegister(register, current and mask.inv())
    }

    private fun setBitMask(register: Register, mask: Byte) {
        val current = readRegister(register)

        writeRegister(register, current or mask)
    }

    private fun stopCrypto1() {
        clearBitMask(Register.STATUS2, 0x08)
    }

    private fun reset() {
        writeRegister(Register.COMMAND, PCD.RESETPHASE.code)
    }

    private fun antennaOn() {
        val currentStatus = readRegister(Register.TX_CONTROL)

        if((currentStatus and 0x03).toInt() != 0x03) {
            setBitMask(Register.TX_CONTROL, 0x03)
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

        enum class PCD(val code: Byte) {
            IDLE(0x00),
            AUTHENT(0x0E),
            RECEIVE(0x08),
            TRANSMIT(0x04),
            TRANSCEIVE(0x0C),
            RESETPHASE(0x0F),
            CALCCRC(0x03),
        }

        enum class Access {
            READ, WRITE
        }
    }
}