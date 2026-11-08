// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
@file:Suppress("DEPRECATION")
package br.dev.edgard.easygamepad.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import br.dev.edgard.easygamepad.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import br.dev.edgard.easygamepad.BuildConfig
import kotlin.coroutines.resume

enum class LinkPhase { DISCONNECTED, CONNECTING, DISCOVERING, SUBSCRIBING, READY }
data class LinkState(val phase: LinkPhase = LinkPhase.DISCONNECTED, val address: String? = null, val name: String? = null, val error: String? = null,
    /** Which configuration protocol this link was opened for. Only that protocol's driver may talk on it. */
    val protocol: ConfigurationProtocol = ConfigurationProtocol.KRLY)
data class FoundDevice(val address: String, val name: String, val rssi: Int? = null, val bonded: Boolean = false, val candidate: Boolean = false)
data class BleLog(val time: Long = System.currentTimeMillis(), val direction: String, val message: String)

@SuppressLint("MissingPermission")
class BleClient(private val context: Context, private val scope: CoroutineScope) : GamepadTransport {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val main = Handler(Looper.getMainLooper())
    private val mutableLink = MutableStateFlow(LinkState())
    val link = mutableLink.asStateFlow()
    private val mutableDevices = MutableStateFlow<List<FoundDevice>>(emptyList())
    val devices = mutableDevices.asStateFlow()
    private val mutableScanning = MutableStateFlow(false)
    val scanning = mutableScanning.asStateFlow()
    private val mutableScanFailure = MutableStateFlow<Int?>(null)
    val scanFailure = mutableScanFailure.asStateFlow()
    private val mutableLogs = MutableStateFlow<List<BleLog>>(emptyList())
    val logs = mutableLogs.asStateFlow()
    private val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 128)
    override val notifications = incoming.asSharedFlow()
    private var gatt: BluetoothGatt? = null
    private var profile = GattProfile()
    private var linkProtocol = ConfigurationProtocol.KRLY
    private var linkName = "EasySMX X10"
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var scanJob: Job? = null
    private var connectTimeout: Job? = null
    private var discoveryJob: Job? = null
    private val observed = ConcurrentHashMap<String, Pair<BluetoothDevice, Long>>()
    private val operation = Mutex()
    @Volatile private var pendingWrite: CompletableDeferred<Int>? = null
    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun permissions() = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasPermissions() = permissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    fun supported() = adapter != null
    fun enabled() = hasPermissions() && adapter?.isEnabled == true
    fun locationEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= 31) return true
        val manager = context.getSystemService(LocationManager::class.java)
        return if (Build.VERSION.SDK_INT >= 28) manager.isLocationEnabled
        else manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun log(direction: String, message: String) {
        mutableLogs.update { (it + BleLog(direction = direction, message = message)).takeLast(200) }
        if (BuildConfig.DEBUG) {
            val summary = if (direction == "RX" || direction == "TX") "${message.split(' ').size} bytes" else
                message.replace(Regex("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}"), "<controller>")
            android.util.Log.d("EasyGamepad", "$direction $summary")
        }
    }
    fun clearLogs() { mutableLogs.value = emptyList() }

    fun scan() {
        check(hasPermissions()) { "Bluetooth permission required" }
        check(enabled()) { "Turn on Bluetooth" }
        check(locationEnabled()) { "Turn on Location to scan on Android 8–11" }
        stopScan()
        mutableScanFailure.value = null
        mutableDevices.value = adapter?.bondedDevices.orEmpty().filter { it.type != BluetoothDevice.DEVICE_TYPE_CLASSIC }.map { device ->
            FoundDevice(device.address, device.name ?: device.address, bonded = true, candidate = candidate(device.name))
        }
        mutableScanning.value = true
        try {
            adapter?.bluetoothLeScanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        } catch (error: Exception) { mutableScanning.value = false; throw error }
        scanJob = scope.launch { delay(12000); stopScan() }
    }
    private fun candidate(name: String?) = listOf("x10", "x20", "easysmx", "qmacro", "keylinker", "xbox", "smx").any { name?.contains(it, true) == true }
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            observed[result.device.address] = result.device to android.os.SystemClock.elapsedRealtime()
            val name = result.scanRecord?.deviceName ?: result.device.name ?: "Bluetooth LE"
            val item = FoundDevice(result.device.address, name, result.rssi, result.device.bondState == BluetoothDevice.BOND_BONDED, candidate(name))
            mutableDevices.update { old -> (old.filterNot { it.address == item.address } + item)
                .sortedWith(compareByDescending<FoundDevice> { it.candidate }.thenByDescending { it.bonded }.thenByDescending { it.rssi ?: -127 }).take(100) }
        }
        override fun onScanFailed(errorCode: Int) {
            mutableScanning.value = false
            mutableScanFailure.value = errorCode
            log("SCAN", "Scan failed: $errorCode")
        }
    }
    fun stopScan() {
        scanJob?.cancel(); scanJob = null
        if (mutableScanning.value && hasPermissions()) runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        mutableScanning.value = false
    }

    /**
     * Opens the configuration link the user chose. [protocol] decides which driver may use it;
     * nothing on the link is sent by BleClient itself beyond the GATT subscription.
     */
    fun connect(address: String, selectedProfile: GattProfile, protocol: ConfigurationProtocol = ConfigurationProtocol.KRLY,
        displayName: String = "EasySMX X10") {
        check(hasPermissions() && enabled()) { "Bluetooth unavailable or permission missing" }
        require(BluetoothAdapter.checkBluetoothAddress(address)) { "Invalid Bluetooth address" }
        listOf(selectedProfile.service, selectedProfile.write, selectedProfile.notify).forEach { UUID.fromString(it) }
        disconnect(); stopScan(); profile = selectedProfile; linkProtocol = protocol; linkName = displayName
        mutableLink.value = LinkState(LinkPhase.CONNECTING, address, displayName, protocol = protocol)
        log("LINK", "Looking for configuration channel $address")
        discoveryJob = scope.launch {
            try {
                val recent = observed[address]?.takeIf { android.os.SystemClock.elapsedRealtime() - it.second < 30_000 }?.first
                val discovered = recent ?: discoverConfiguration(address)
                val device = discovered ?: requireNotNull(adapter).getRemoteDevice(address)
                ensureActive()
                mutableLink.update { it.copy(name = device.name ?: displayName) }
                log("LINK", "Connecting to $address · ${device.name} · type ${device.type}")
                // Keep the BLE device returned by discovery, including its address type.
                startGatt(device)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { fail(error.message ?: "Bluetooth connection could not start") }
        }
    }

    private fun startGatt(device: BluetoothDevice) {
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK, main)
        checkNotNull(gatt) { "Bluetooth connection could not start" }
        connectTimeout?.cancel()
        connectTimeout = scope.launch {
            delay(18000)
            if (link.value.phase != LinkPhase.READY)
                fail(if (linkProtocol == ConfigurationProtocol.KRLY) "QMacro did not respond. Gameplay may still work. Disconnect configuration apps on other phones, then try again."
                    else "The configuration channel did not respond. Gameplay may still work. Close the KeyLinker app and configuration apps on other phones, then try again.")
        }
    }

    private suspend fun discoverConfiguration(address: String): BluetoothDevice? {
        if (!locationEnabled()) return null
        val scanner = adapter?.bluetoothLeScanner ?: return null
        var active: ScanCallback? = null
        return try {
            withTimeoutOrNull(6000) {
                suspendCancellableCoroutine<BluetoothDevice?> { continuation ->
                    val probe = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult) {
                            if (result.device.address != address || !continuation.isActive) return
                            observed[address] = result.device to android.os.SystemClock.elapsedRealtime()
                            log("SCAN", "Configuration advertisement: ${result.scanRecord?.deviceName} · $address · ${result.rssi} dBm")
                            scanner.stopScan(this)
                            continuation.resume(result.device)
                        }
                        override fun onScanFailed(errorCode: Int) {
                            if (continuation.isActive) continuation.resume(null)
                            log("SCAN", "Configuration discovery failed ($errorCode)")
                        }
                    }
                    active = probe
                    scanner.startScan(listOf(ScanFilter.Builder().setDeviceAddress(address).build()),
                        ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), probe)
                }
            }
        } finally { active?.let { runCatching { scanner.stopScan(it) } } }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(connection: BluetoothGatt, status: Int, newState: Int) {
            if (connection !== gatt) { runCatching { connection.close() }; return }
            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                fail(if (status == 0) "Controller disconnected" else "Bluetooth connection failed ($status)")
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mutableLink.update { it.copy(phase = LinkPhase.DISCOVERING) }
                if (!connection.discoverServices()) fail("Unable to discover services")
            }
        }
        override fun onServicesDiscovered(connection: BluetoothGatt, status: Int) {
            if (connection !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) { fail("GATT discovery failed ($status)"); return }
            val service = connection.getService(UUID.fromString(profile.service))
            val writer = service?.getCharacteristic(UUID.fromString(profile.write))
            val notifier = service?.getCharacteristic(UUID.fromString(profile.notify))
            log("GATT", connection.services.joinToString { it.uuid.toString() })
            if (writer == null || notifier == null || writer.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
                fail(if (linkProtocol == ConfigurationProtocol.KRLY) "X10 configuration service not found. The gameplay connection may use a different BLE address. Check the device and advanced UUIDs."
                    else "$linkName configuration service (KeyLinker) not found on this device. Nothing was sent."); return
            }
            val descriptor = notifier.getDescriptor(cccd)
            val notifyValue = when {
                notifier.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                notifier.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else -> null
            }
            if (descriptor == null || notifyValue == null || !connection.setCharacteristicNotification(notifier, true)) {
                fail("Controller notifications unavailable"); return
            }
            writeCharacteristic = writer
            mutableLink.update { it.copy(phase = LinkPhase.SUBSCRIBING) }
            val started = if (Build.VERSION.SDK_INT >= 33) connection.writeDescriptor(descriptor, notifyValue) == BluetoothStatusCodes.SUCCESS
                else { descriptor.value = notifyValue; connection.writeDescriptor(descriptor) }
            if (!started) fail("Could not subscribe to notifications")
        }
        override fun onDescriptorWrite(connection: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (connection !== gatt || descriptor.uuid != cccd) return
            if (status != BluetoothGatt.GATT_SUCCESS) { fail("Notification subscription failed ($status)"); return }
            connectTimeout?.cancel()
            mutableLink.update { it.copy(phase = LinkPhase.READY, error = null) }
            log("LINK", "Ready · notifications enabled")
        }
        override fun onCharacteristicChanged(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            receive(connection, characteristic, value)
        }
        @Suppress("OVERRIDE_DEPRECATION") // Required for notifications on Android 8–12.
        override fun onCharacteristicChanged(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) receive(connection, characteristic, characteristic.value ?: return)
        }
        override fun onCharacteristicWrite(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (connection === gatt && characteristic.uuid == writeCharacteristic?.uuid) pendingWrite?.complete(status)
        }
    }
    private fun receive(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        if (connection !== gatt || characteristic.uuid != UUID.fromString(profile.notify)) return
        log("RX", data.hex())
        if (!incoming.tryEmit(data.copyOf())) fail("Notification buffer overflow. Reconnect.")
    }

    override suspend fun write(frame: ByteArray) = operation.withLock {
        withContext(Dispatchers.Main.immediate) {
            check(hasPermissions() && link.value.phase == LinkPhase.READY) { "Controller not connected" }
            val connection = requireNotNull(gatt)
            val characteristic = requireNotNull(writeCharacteristic)
            val type = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            for (chunk in frame.toList().chunked(20)) {
                val data = chunk.toByteArray()
                val completion = CompletableDeferred<Int>()
                pendingWrite = completion
                try {
                    val started = if (Build.VERSION.SDK_INT >= 33) connection.writeCharacteristic(characteristic, data, type) == BluetoothStatusCodes.SUCCESS
                        else { characteristic.writeType = type; characteristic.value = data; connection.writeCharacteristic(characteristic) }
                    check(started) { "Bluetooth write could not start" }
                    log("TX", data.hex())
                    val status = withTimeout(4000) { completion.await() }
                    check(status == BluetoothGatt.GATT_SUCCESS) { "Bluetooth write failed ($status)" }
                } catch (error: Exception) {
                    fail(error.message ?: "Bluetooth write failed"); throw error
                } finally { pendingWrite = null }
            }
        }
    }
    override fun abort(cause: Throwable) { fail(cause.message ?: "Controller response timed out. Reconnect.") }
    private fun fail(message: String) {
        log("ERROR", message)
        disconnect()
        mutableLink.update { it.copy(error = message) }
    }
    fun disconnect() {
        discoveryJob?.cancel(); discoveryJob = null
        connectTimeout?.cancel(); connectTimeout = null
        pendingWrite?.completeExceptionally(IllegalStateException("Disconnected"))
        pendingWrite = null
        val previous = gatt; gatt = null; writeCharacteristic = null
        runCatching { previous?.disconnect() }; runCatching { previous?.close() }
        mutableLink.value = LinkState()
    }
}
