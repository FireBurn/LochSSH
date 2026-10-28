package uk.co.fireburn.lochssh.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Opens a USB serial adapter through the usb-serial-for-android library.
object UsbSerial {
    private const val PERMISSION = "uk.co.fireburn.lochssh.USB_PERMISSION"
    private const val WRITE_TIMEOUT_MS = 2000
    private const val PERMISSION_WAIT_SECONDS = 30L

    class Attached(val id: String, val label: String)

    fun attached(context: Context): List<Attached> = drivers(context).map { driver ->
        val device = driver.device
        val id = "%04x:%04x".format(device.vendorId, device.productId)
        Attached(id, listOfNotNull(device.productName, id).joinToString(" "))
    }

    fun open(context: Context, settings: SerialSettings): SerialConnection {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val wanted = settings.deviceNumbers()
        val driver = drivers(context).firstOrNull {
            wanted == null || (it.device.vendorId to it.device.productId) == wanted
        } ?: throw IOException(
            if (wanted == null) "No USB serial adapter found. Plug it in and try again."
            else "The USB adapter ${settings.device} is not plugged in."
        )
        if (!manager.hasPermission(driver.device)) requestPermission(context, manager, driver.device)
        val connection = manager.openDevice(driver.device) ?: throw IOException("Could not open the USB adapter")
        val port = driver.ports.firstOrNull() ?: throw IOException("The USB adapter has no serial port")
        try {
            port.open(connection)
            port.setParameters(settings.baud, settings.dataBits, stopBits(settings), parity(settings))
            applyFlow(port, settings.flow)
            runCatching { port.dtr = true }
            runCatching { port.rts = true }
        } catch (e: Exception) {
            runCatching { port.close() }
            throw IOException("Could not set up the serial port: ${e.message}", e)
        }
        return SerialConnection(object : SerialIo {
            override fun read(buffer: ByteArray, timeoutMs: Int) = port.read(buffer, timeoutMs)
            override fun write(data: ByteArray) = port.write(data, WRITE_TIMEOUT_MS)
            override fun close() = port.close()
        })
    }

    private fun drivers(context: Context): List<UsbSerialDriver> {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return UsbSerialProber.getDefaultProber().findAllDrivers(manager)
    }

    private fun applyFlow(port: UsbSerialPort, flow: SerialSettings.Flow) {
        val control = when (flow) {
            SerialSettings.Flow.NONE -> UsbSerialPort.FlowControl.NONE
            SerialSettings.Flow.RTS_CTS -> UsbSerialPort.FlowControl.RTS_CTS
            SerialSettings.Flow.XON_XOFF -> UsbSerialPort.FlowControl.XON_XOFF
        }
        try { port.setFlowControl(control) } catch (_: UnsupportedOperationException) {
            if (flow != SerialSettings.Flow.NONE) throw IOException("This adapter does not support $flow flow control")
        }
    }

    private fun stopBits(settings: SerialSettings) = when (settings.stopBits) {
        SerialSettings.StopBits.ONE -> UsbSerialPort.STOPBITS_1
        SerialSettings.StopBits.ONE_AND_HALF -> UsbSerialPort.STOPBITS_1_5
        SerialSettings.StopBits.TWO -> UsbSerialPort.STOPBITS_2
    }

    private fun parity(settings: SerialSettings) = when (settings.parity) {
        SerialSettings.Parity.NONE -> UsbSerialPort.PARITY_NONE
        SerialSettings.Parity.ODD -> UsbSerialPort.PARITY_ODD
        SerialSettings.Parity.EVEN -> UsbSerialPort.PARITY_EVEN
        SerialSettings.Parity.MARK -> UsbSerialPort.PARITY_MARK
        SerialSettings.Parity.SPACE -> UsbSerialPort.PARITY_SPACE
    }

    private fun requestPermission(context: Context, manager: UsbManager, device: UsbDevice) {
        val answered = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = answered.countDown()
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val intent = PendingIntent.getBroadcast(context, 0,
                Intent(PERMISSION).setPackage(context.packageName), PendingIntent.FLAG_MUTABLE)
            manager.requestPermission(device, intent)
            if (!answered.await(PERMISSION_WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw IOException("USB permission was not answered")
            }
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
        if (!manager.hasPermission(device)) throw IOException("USB permission was refused")
    }
}
