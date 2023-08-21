package io.vinicius.sak.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class UsbHelper(
    private val context: Context,
    private val usbManager: UsbManager,
    private val deviceName: String,
) : PrivateFlow {
    private val driver: UsbSerialDriver by lazy {
        val allDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (allDrivers.isEmpty()) error("No USB devices found!")
        allDrivers.first { it.device.deviceName.equals(deviceName, ignoreCase = true) }
    }

    private val usbPermissionAction = "${deviceName}_USB_PERMISSION".uppercase()
    private val device = driver.device
    private val port = driver.ports.first()
    private var connection: UsbDeviceConnection? = null

    val hasPermission = privateStateFlow(false)
    val isPortOpen get() = port.isOpen

    init {
        registerPermissionReceiver()
    }

    fun requestPermission() {
        val pendingIntent = PendingIntent.getBroadcast(context, 0, Intent(usbPermissionAction), 0)
        usbManager.requestPermission(device, pendingIntent)
    }

    @Synchronized
    fun connect(): UsbDeviceConnection? {
        val baudRate = 115_200

        connection = usbManager.openDevice(device)
        port.open(connection)
        port.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

        return connection
    }

    @Synchronized
    fun disconnect() {
        if (port.isOpen) port.close()
        connection?.close()
        connection = null
    }

    @Synchronized
    fun read(timeout: Duration = 1.seconds): Pair<Int, ByteArray> {
        val buffer = ByteArray(1024)
        val bytesRead = port.read(buffer, timeout.inWholeMilliseconds.toInt())
        return Pair(bytesRead, buffer)
    }

    @Synchronized
    fun write(bytes: ByteArray, timeout: Duration = 0.seconds) {
        port.write(bytes, timeout.inWholeMilliseconds.toInt())
    }

    private fun registerPermissionReceiver() {
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    hasPermission.mutable = intent?.action == usbPermissionAction &&
                    intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                }
            },
            IntentFilter(usbPermissionAction),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
}