package it.historia.player.data.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import it.historia.player.data.util.SerialLogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class UsbConnectionState {
    CONNECTED,
    DISCONNECTED,
    WAITING_PERMISSION
}

enum class UsbDisconnectReason {
    NO_DEVICE,
    NO_PERMISSION,
    DETACHED,
    ERROR,
    STOPPED
}

data class UsbConnectionStatus(
    val state: UsbConnectionState,
    val reason: UsbDisconnectReason? = null
)

data class UsbIoStatus(
    val byteCount: Long = 0L,
    val lastByteAtMs: Long? = null,
    val lastReadTimeoutAtMs: Long? = null,
    val readLoopStartedAtMs: Long? = null,
    val partialLineLength: Int = 0
)

class UsbSerialManager(
    context: Context,
    private val serialLogBuffer: SerialLogBuffer
) {
    private val appContext: Context = context.applicationContext
    private val usbManager: UsbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _incomingLines = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingLines: Flow<String> = _incomingLines.asSharedFlow()

    private val _connectionStatus = MutableStateFlow(
        UsbConnectionStatus(
            state = UsbConnectionState.DISCONNECTED,
            reason = UsbDisconnectReason.NO_DEVICE
        )
    )
    val connectionStatus: StateFlow<UsbConnectionStatus> = _connectionStatus.asStateFlow()

    private val _ioStatus = MutableStateFlow(UsbIoStatus())
    val ioStatus: StateFlow<UsbIoStatus> = _ioStatus.asStateFlow()

    private val lock = Any()
    private var activePort: UsbSerialPort? = null
    private var activeConnection: UsbDeviceConnection? = null
    private var activeDeviceId: Int? = null

    private var mockJob: Job? = null
    private var readJob: Job? = null
    private var receiverRegistered = false

    @Volatile
    private var mockMaxTrack: Int = 7

    private val lineBuffer = StringBuilder()
    private var lastByteWasCr = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> handlePermissionResult(intent)
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> startUsbAutoconnect()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val detached = intent.getUsbDeviceCompat()
                    val currentId = synchronized(lock) { activeDeviceId }
                    if (detached == null || detached.deviceId == currentId) {
                        stopUsb(UsbDisconnectReason.DETACHED)
                    }
                }
            }
        }
    }

    init {
        registerReceivers()
    }

    fun startUsbAutoconnect() {
        mockJob?.cancel()
        mockJob = null

        appendStructuredLog("USB: searching drivers")

        scope.launch {
            val currentPort = synchronized(lock) { activePort }
            if (currentPort != null) {
                updateConnectionState(UsbConnectionState.CONNECTED, null)
                return@launch
            }

            val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).firstOrNull()
            if (driver == null) {
                updateConnectionState(UsbConnectionState.DISCONNECTED, UsbDisconnectReason.NO_DEVICE)
                return@launch
            }

            connectDriver(driver.device.deviceId)
        }
    }

    fun stopUsb(reason: UsbDisconnectReason = UsbDisconnectReason.STOPPED) {
        stopUsbInternal(reason = reason, emitState = true)
    }

    fun restartUsbConnection() {
        scope.launch {
            appendStructuredLog("USB: manual restart requested")
            stopUsbInternal(reason = UsbDisconnectReason.STOPPED, emitState = true)
            delay(MANUAL_RESTART_DELAY_MS)
            startUsbAutoconnect()
        }
    }

    fun sendLine(line: String) {
        scope.launch {
            val payload = if (line.endsWith("\r\n")) line else "$line\r\n"
            val bytes = payload.toByteArray(Charsets.UTF_8)

            val port = synchronized(lock) { activePort }
            if (port == null) {
                appendStructuredLog("ERR: sendLine ignored, USB port not connected")
                Log.w(TAG, "sendLine ignored: USB port not connected")
                return@launch
            }

            runCatching {
                port.write(bytes, WRITE_TIMEOUT_MS)
            }.onFailure { error ->
                appendStructuredLog("ERR: sendLine failed ${error.message ?: "unknown"}")
                Log.e(TAG, "sendLine failed", error)
                stopUsb(UsbDisconnectReason.ERROR)
            }
        }
    }

    fun startMock(maxTrack: Int) {
        mockMaxTrack = sanitizeMaxTrack(maxTrack)
        if (mockJob?.isActive == true) return

        mockJob = scope.launch {
            var touch = 1
            var counter = 0
            while (isActive) {
                if (counter % 8 == 0) {
                    _incomingLines.emit("MSG:HI-STORIA READY")
                }

                val currentMaxTrack = sanitizeMaxTrack(mockMaxTrack)
                if (touch > currentMaxTrack) {
                    touch = 1
                }

                _incomingLines.emit("T$touch")
                touch++
                counter++
                delay(2000)
            }
        }
    }

    fun setMockMaxTrack(maxTrack: Int) {
        mockMaxTrack = sanitizeMaxTrack(maxTrack)
    }

    fun close() {
        mockJob?.cancel()
        mockJob = null
        stopUsbInternal(reason = UsbDisconnectReason.STOPPED, emitState = false)
        unregisterReceivers()
        scope.cancel()
    }

    private fun connectDriver(deviceId: Int) {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val driver = drivers.firstOrNull { it.device.deviceId == deviceId }
        if (driver == null) {
            updateConnectionState(UsbConnectionState.DISCONNECTED, UsbDisconnectReason.NO_DEVICE)
            return
        }

        val device = driver.device
        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            updateConnectionState(UsbConnectionState.WAITING_PERMISSION, UsbDisconnectReason.NO_PERMISSION)
            return
        }

        appendStructuredLog("USB: permission granted")

        val connection = usbManager.openDevice(device)
        if (connection == null) {
            requestPermission(device)
            updateConnectionState(UsbConnectionState.WAITING_PERMISSION, UsbDisconnectReason.NO_PERMISSION)
            return
        }

        val port = driver.ports.firstOrNull()
        if (port == null) {
            runCatching { connection.close() }
            updateConnectionState(UsbConnectionState.DISCONNECTED, UsbDisconnectReason.ERROR)
            return
        }

        runCatching {
            port.open(connection)
            port.setParameters(BAUDRATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            runCatching { port.setDTR(true) }
            runCatching { port.setRTS(true) }

            synchronized(lock) {
                activePort = port
                activeConnection = connection
                activeDeviceId = device.deviceId
                lineBuffer.setLength(0)
                lastByteWasCr = false
            }

            startReadLoop(port)
            updateConnectionState(UsbConnectionState.CONNECTED, null)
        }.onFailure { error ->
            appendStructuredLog("ERR: USB connect failed ${error.message ?: "unknown"}")
            Log.e(TAG, "USB connect failed", error)
            runCatching { port.close() }
            runCatching { connection.close() }
            updateConnectionState(UsbConnectionState.DISCONNECTED, UsbDisconnectReason.ERROR)
        }
    }

    private fun startReadLoop(port: UsbSerialPort) {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(2048)
            _ioStatus.value = _ioStatus.value.copy(
                readLoopStartedAtMs = android.os.SystemClock.elapsedRealtime(),
                lastReadTimeoutAtMs = null,
                partialLineLength = 0
            )
            try {
                while (isActive) {
                    val count = port.read(buffer, READ_TIMEOUT_MS)
                    if (count > 0) {
                        recordBytesRead(count)
                        emitLinesFromBytes(buffer, count)
                    } else {
                        _ioStatus.value = _ioStatus.value.copy(
                            lastReadTimeoutAtMs = android.os.SystemClock.elapsedRealtime(),
                            partialLineLength = synchronized(lock) { lineBuffer.length }
                        )
                    }
                }
            } catch (error: Throwable) {
                appendStructuredLog("ERR: USB read loop terminated ${error.message ?: "unknown"}")
                Log.e(TAG, "USB read loop terminated", error)
            } finally {
                val stillActive = synchronized(lock) { activePort === port }
                if (stillActive) {
                    stopUsb(UsbDisconnectReason.ERROR)
                }
            }
        }
    }

    private suspend fun emitLinesFromBytes(buffer: ByteArray, count: Int) {
        for (index in 0 until count) {
            val value = buffer[index].toInt() and 0xFF
            when (value) {
                CR -> {
                    emitBufferedLine()
                    lastByteWasCr = true
                }

                LF -> {
                    if (lastByteWasCr) {
                        lastByteWasCr = false
                    } else {
                        emitBufferedLine()
                    }
                }

                else -> {
                    lastByteWasCr = false
                    lineBuffer.append(value.toChar())
                    if (lineBuffer.length > MAX_SERIAL_LINE_CHARS) {
                        appendStructuredLog("ERR: serial partial line too long, buffer cleared")
                        lineBuffer.setLength(0)
                    }
                }
            }
        }
        _ioStatus.value = _ioStatus.value.copy(
            partialLineLength = synchronized(lock) { lineBuffer.length }
        )
    }

    private suspend fun emitBufferedLine() {
        if (lineBuffer.isEmpty()) return
        val line = lineBuffer.toString()
        appendStructuredLog("RX: $line")
        _incomingLines.emit(line)
        lineBuffer.setLength(0)
        _ioStatus.value = _ioStatus.value.copy(partialLineLength = 0)
    }

    private fun recordBytesRead(count: Int) {
        val now = android.os.SystemClock.elapsedRealtime()
        _ioStatus.value = _ioStatus.value.copy(
            byteCount = _ioStatus.value.byteCount + count,
            lastByteAtMs = now,
            partialLineLength = synchronized(lock) { lineBuffer.length }
        )
    }

    private fun handlePermissionResult(intent: Intent) {
        if (intent.action != ACTION_USB_PERMISSION) return

        val device = intent.getUsbDeviceCompat()
        if (device == null) {
            updateConnectionState(UsbConnectionState.DISCONNECTED, UsbDisconnectReason.NO_DEVICE)
            return
        }

        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
        if (!granted) {
            updateConnectionState(UsbConnectionState.DISCONNECTED, UsbDisconnectReason.NO_PERMISSION)
            return
        }

        appendStructuredLog("USB: permission granted")

        scope.launch {
            connectDriver(device.deviceId)
        }
    }

    private fun requestPermission(device: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            device.deviceId,
            intent,
            flags
        )
        usbManager.requestPermission(device, pendingIntent)
    }

    private fun stopUsbInternal(reason: UsbDisconnectReason, emitState: Boolean) {
        readJob?.cancel()
        readJob = null

        val port: UsbSerialPort?
        val connection: UsbDeviceConnection?
        synchronized(lock) {
            port = activePort
            connection = activeConnection
            activePort = null
            activeConnection = null
            activeDeviceId = null
            lineBuffer.setLength(0)
            lastByteWasCr = false
        }

        runCatching { port?.close() }
        runCatching { connection?.close() }

        if (emitState) {
            updateConnectionState(UsbConnectionState.DISCONNECTED, reason)
        }
    }

    private fun registerReceivers() {
        if (receiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(usbReceiver, filter)
        }

        receiverRegistered = true
    }

    private fun unregisterReceivers() {
        if (!receiverRegistered) return
        runCatching { appContext.unregisterReceiver(usbReceiver) }
        receiverRegistered = false
    }

    private fun updateConnectionState(state: UsbConnectionState, reason: UsbDisconnectReason?) {
        _connectionStatus.value = UsbConnectionStatus(state = state, reason = reason)
        when (state) {
            UsbConnectionState.CONNECTED -> appendStructuredLog("USB: CONNECTED")
            UsbConnectionState.DISCONNECTED -> appendStructuredLog(
                "USB: DISCONNECTED reason=${reason?.name ?: "UNKNOWN"}"
            )

            UsbConnectionState.WAITING_PERMISSION -> Unit
        }
    }

    private fun appendStructuredLog(line: String) {
        serialLogBuffer.append(line)
    }

    private fun sanitizeMaxTrack(value: Int): Int {
        return value.coerceAtLeast(1)
    }

    @Suppress("DEPRECATION")
    private fun Intent.getUsbDeviceCompat(): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
        }
    }

    companion object {
        private const val TAG = "UsbSerialManager"
        private const val ACTION_USB_PERMISSION = "it.historia.player.USB_PERMISSION"
        private const val BAUDRATE = 115200
        private const val READ_TIMEOUT_MS = 1000
        private const val WRITE_TIMEOUT_MS = 1000
        private const val MANUAL_RESTART_DELAY_MS = 2_000L
        private const val MAX_SERIAL_LINE_CHARS = 4096
        private const val CR = 13
        private const val LF = 10
    }
}
