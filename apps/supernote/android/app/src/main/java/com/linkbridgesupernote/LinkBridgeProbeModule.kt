package com.linkbridgesupernote

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class LinkBridgeProbeModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

  private data class DeviceResult(
      val address: String,
      val name: String?,
      val rssi: Int,
      val serviceUuids: List<String>
  )

  private val mainHandler = Handler(Looper.getMainLooper())
  private val scanResults = linkedMapOf<String, DeviceResult>()
  private var activeCallback: ScanCallback? = null
  private var activePromise: Promise? = null
  private var activeScanner: android.bluetooth.le.BluetoothLeScanner? = null
  private var requestedServiceUuid: String? = null
  private var scanStartedAt: Long = 0L
  private var scanTimeout: Runnable? = null

  private var receivePromise: Promise? = null
  private var receiveScanner: android.bluetooth.le.BluetoothLeScanner? = null
  private var receiveScanCallback: ScanCallback? = null
  private var receiveGatt: BluetoothGatt? = null
  private var receiveServiceUuid: UUID? = null
  private var receiveUrlCharacteristicUuid: UUID? = null
  private var receiveMetadataCharacteristicUuid: UUID? = null
  private var receiveAckCharacteristicUuid: UUID? = null
  private var receiveStartedAt: Long = 0L
  private var receiveDeviceName: String? = null
  private var receiveDeviceAddress: String? = null
  private var receiveRssi: Int = 0
  private var receiveMtu: Int = 23
  private var receiveDiscoveryStarted = false
  private var receiveTimeout: Runnable? = null
  private var receiveSessionTimeout: Runnable? = null
  private var receiveAckPromise: Promise? = null
  private var receivedItemId: String? = null
  private var receivedTitle: String = ""
  private var receivedCreatedAt: Long = 0L

  override fun getName(): String = "LinkBridgeProbe"

  @ReactMethod
  fun getCapabilities(promise: Promise) {
    try {
      val packageManager = reactContext.packageManager
      val bluetoothManager =
          reactContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
      val adapter = bluetoothManager?.adapter
      val result = Arguments.createMap()

      result.putInt("sdkInt", Build.VERSION.SDK_INT)
      result.putString("androidRelease", Build.VERSION.RELEASE)
      result.putString("runtimePackage", reactContext.packageName)
      result.putInt("targetSdk", reactContext.applicationInfo.targetSdkVersion)
      result.putBoolean(
          "bleFeaturePresent",
          packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
      result.putBoolean("adapterPresent", adapter != null)
      result.putBoolean("adapterEnabled", adapter?.isEnabled == true)
      result.putBoolean("scannerAvailable", adapter?.bluetoothLeScanner != null)
      result.putBoolean(
          "multipleAdvertisementSupported",
          adapter?.isMultipleAdvertisementSupported == true)

      val permissions = Arguments.createMap()
      permissionState(Manifest.permission.BLUETOOTH, permissions)
      permissionState(Manifest.permission.BLUETOOTH_ADMIN, permissions)
      permissionState(Manifest.permission.ACCESS_FINE_LOCATION, permissions)
      permissionState(Manifest.permission.ACCESS_COARSE_LOCATION, permissions)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissionState(Manifest.permission.BLUETOOTH_SCAN, permissions)
        permissionState(Manifest.permission.BLUETOOTH_CONNECT, permissions)
      }
      result.putMap("permissions", permissions)

      val bondedCount = try {
        adapter?.bondedDevices?.size ?: 0
      } catch (_: SecurityException) {
        -1
      }
      result.putInt("bondedDeviceCount", bondedCount)
      promise.resolve(result)
    } catch (error: Throwable) {
      promise.reject("capability_check_failed", error.message, error)
    }
  }

  @ReactMethod
  fun startScan(serviceUuid: String, timeoutMs: Double, promise: Promise) {
    if (activePromise != null || receivePromise != null || receiveGatt != null) {
      promise.reject("scan_in_progress", "A BLE scan is already running")
      return
    }

    val parsedUuid = try {
      ParcelUuid(UUID.fromString(serviceUuid))
    } catch (error: IllegalArgumentException) {
      promise.reject("invalid_service_uuid", "Invalid service UUID: $serviceUuid", error)
      return
    }

    val bluetoothManager =
        reactContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bluetoothManager?.adapter
    if (adapter == null) {
      promise.reject("bluetooth_unavailable", "This device has no Bluetooth adapter")
      return
    }
    if (!adapter.isEnabled) {
      promise.reject("bluetooth_disabled", "Bluetooth is turned off on the Supernote")
      return
    }

    val scanner = adapter.bluetoothLeScanner
    if (scanner == null) {
      promise.reject("scanner_unavailable", "The BLE scanner is unavailable")
      return
    }

    val boundedTimeout = timeoutMs.toLong().coerceIn(1_000L, 30_000L)
    scanResults.clear()
    activePromise = promise
    activeScanner = scanner
    requestedServiceUuid = parsedUuid.uuid.toString()
    scanStartedAt = System.currentTimeMillis()

    val callback = object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        recordResult(result)
      }

      override fun onBatchScanResults(results: MutableList<ScanResult>) {
        results.forEach(::recordResult)
      }

      override fun onScanFailed(errorCode: Int) {
        finishScan("scan_failed_$errorCode", "Android BLE scan failed with code $errorCode")
      }
    }
    activeCallback = callback

    try {
      val filter = ScanFilter.Builder().setServiceUuid(parsedUuid).build()
      val settings =
          ScanSettings.Builder()
              .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
              .build()
      scanner.startScan(listOf(filter), settings, callback)
      val timeout = Runnable { finishScan() }
      scanTimeout = timeout
      mainHandler.postDelayed(timeout, boundedTimeout)
    } catch (error: SecurityException) {
      finishScan("scan_permission_denied", error.message ?: "BLE scan permission denied")
    } catch (error: Throwable) {
      finishScan("scan_start_failed", error.message ?: "Unable to start BLE scan")
    }
  }

  @ReactMethod
  fun stopScan(promise: Promise) {
    if (activePromise == null) {
      promise.resolve(false)
      return
    }
    finishScan()
    promise.resolve(true)
  }

  @ReactMethod
  fun receiveItem(
      serviceUuid: String,
      urlCharacteristicUuid: String,
      metadataCharacteristicUuid: String,
      ackCharacteristicUuid: String,
      timeoutMs: Double,
      promise: Promise
  ) {
    if (activePromise != null || receivePromise != null || receiveGatt != null) {
      promise.reject("bluetooth_busy", "A LinkBridge Bluetooth operation is already running")
      return
    }

    val parsedService = try {
      UUID.fromString(serviceUuid)
    } catch (error: IllegalArgumentException) {
      promise.reject("invalid_service_uuid", "Invalid service UUID: $serviceUuid", error)
      return
    }
    val parsedUrlCharacteristic = try {
      UUID.fromString(urlCharacteristicUuid)
    } catch (error: IllegalArgumentException) {
      promise.reject(
        "invalid_characteristic_uuid",
          "Invalid URL characteristic UUID: $urlCharacteristicUuid",
          error)
      return
    }
    val parsedMetadataCharacteristic = try {
      UUID.fromString(metadataCharacteristicUuid)
    } catch (error: IllegalArgumentException) {
      promise.reject(
          "invalid_characteristic_uuid",
          "Invalid metadata characteristic UUID: $metadataCharacteristicUuid",
          error)
      return
    }
    val parsedAckCharacteristic = try {
      UUID.fromString(ackCharacteristicUuid)
    } catch (error: IllegalArgumentException) {
      promise.reject(
          "invalid_characteristic_uuid",
          "Invalid acknowledgement characteristic UUID: $ackCharacteristicUuid",
          error)
      return
    }

    val bluetoothManager =
        reactContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bluetoothManager?.adapter
    val scanner = adapter?.bluetoothLeScanner
    if (adapter == null || scanner == null) {
      promise.reject("bluetooth_unavailable", "Bluetooth scanning is unavailable")
      return
    }
    if (!adapter.isEnabled) {
      promise.reject("bluetooth_disabled", "Bluetooth is turned off on the Supernote")
      return
    }

    receivePromise = promise
    receiveScanner = scanner
    receiveServiceUuid = parsedService
    receiveUrlCharacteristicUuid = parsedUrlCharacteristic
    receiveMetadataCharacteristicUuid = parsedMetadataCharacteristic
    receiveAckCharacteristicUuid = parsedAckCharacteristic
    receiveStartedAt = System.currentTimeMillis()
    receiveDiscoveryStarted = false
    receiveMtu = 23
    receivedItemId = null
    receivedTitle = ""
    receivedCreatedAt = 0L

    val callback = object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        connectToPhone(result)
      }

      override fun onBatchScanResults(results: MutableList<ScanResult>) {
        results.firstOrNull()?.let(::connectToPhone)
      }

      override fun onScanFailed(errorCode: Int) {
        finishReceive(
            "receive_scan_failed_$errorCode",
            "Android BLE scan failed with code $errorCode")
      }
    }
    receiveScanCallback = callback

    try {
      val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(parsedService)).build()
      val settings =
          ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
      scanner.startScan(listOf(filter), settings, callback)
      val timeout = Runnable {
        finishReceive(
            "receive_timeout",
            "No queued LinkBridge item was received before the timeout")
      }
      receiveTimeout = timeout
      mainHandler.postDelayed(timeout, timeoutMs.toLong().coerceIn(3_000L, 45_000L))
    } catch (error: Throwable) {
      finishReceive("receive_start_failed", error.message ?: "Unable to start receiving")
    }
  }

  @ReactMethod
  @Synchronized
  fun hasReceipt(itemId: String, promise: Promise) {
    if (itemId.isBlank()) {
      promise.reject("invalid_item_id", "The LinkBridge item ID is empty")
      return
    }
    promise.resolve(readReceiptIds().contains(itemId))
  }

  @ReactMethod
  @Synchronized
  fun recordReceipt(itemId: String, promise: Promise) {
    if (itemId.isBlank()) {
      promise.reject("invalid_item_id", "The LinkBridge item ID is empty")
      return
    }
    val ids = readReceiptIds().filterNot { it == itemId }.toMutableList()
    ids.add(0, itemId)
    while (ids.size > 500) ids.removeAt(ids.lastIndex)
    val encoded = JSONArray()
    ids.forEach(encoded::put)
    val saved =
        reactContext
            .getSharedPreferences("linkbridge_receipts", Context.MODE_PRIVATE)
            .edit()
            .putString("saved_item_ids", encoded.toString())
            .commit()
    if (!saved) {
      promise.reject("receipt_save_failed", "Could not save the LinkBridge insertion receipt")
      return
    }
    promise.resolve(true)
  }

  @ReactMethod
  @Synchronized
  fun acknowledgeItem(itemId: String, promise: Promise) {
    val gatt = receiveGatt
    if (gatt == null || receivedItemId == null) {
      promise.reject("ack_session_missing", "The phone connection is no longer open")
      return
    }
    if (itemId != receivedItemId) {
      promise.reject("ack_item_mismatch", "The acknowledgement does not match the received item")
      return
    }
    if (receiveAckPromise != null) {
      promise.reject("ack_in_progress", "A LinkBridge acknowledgement is already in progress")
      return
    }
    val characteristic =
        receiveServiceUuid?.let(gatt::getService)
            ?.getCharacteristic(receiveAckCharacteristicUuid)
    if (characteristic == null) {
      promise.reject("ack_characteristic_missing", "The phone did not expose save confirmation")
      closeReceiveSession()
      return
    }
    val bytes =
        JSONObject()
            .put("v", 1)
            .put("id", itemId)
            .put("status", "saved")
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
    try {
      @Suppress("DEPRECATION")
      characteristic.value = bytes
      characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
      receiveAckPromise = promise
      if (!gatt.writeCharacteristic(characteristic)) {
        receiveAckPromise = null
        promise.reject("ack_write_failed", "Could not send save confirmation to the phone")
        closeReceiveSession()
        return
      }
      receiveSessionTimeout?.let(mainHandler::removeCallbacks)
      val timeout = Runnable {
        val pending = receiveAckPromise
        receiveAckPromise = null
        pending?.reject("ack_timeout", "The phone did not confirm the saved link")
        closeReceiveSession()
      }
      receiveSessionTimeout = timeout
      mainHandler.postDelayed(timeout, 10_000L)
    } catch (error: Throwable) {
      receiveAckPromise = null
      promise.reject("ack_write_failed", error.message ?: "Could not confirm the saved link", error)
      closeReceiveSession()
    }
  }

  @ReactMethod
  @Synchronized
  fun cancelReceive(promise: Promise) {
    val hadSession = receivePromise != null || receiveGatt != null
    val pendingReceive = receivePromise
    receivePromise = null
    closeReceiveSession()
    pendingReceive?.reject("receive_cancelled", "LinkBridge receiving was cancelled")
    promise.resolve(hadSession)
  }

  private fun readReceiptIds(): MutableList<String> {
    val encoded =
        reactContext
            .getSharedPreferences("linkbridge_receipts", Context.MODE_PRIVATE)
            .getString("saved_item_ids", "[]")
            ?: "[]"
    val result = mutableListOf<String>()
    try {
      val array = JSONArray(encoded)
      for (index in 0 until array.length()) {
        val id = array.optString(index).trim()
        if (id.isNotEmpty() && !result.contains(id)) result.add(id)
      }
    } catch (_: Throwable) {
    }
    return result
  }

  private fun permissionState(permission: String, target: com.facebook.react.bridge.WritableMap) {
    val shortName = permission.substringAfterLast('.')
    target.putBoolean(
        shortName,
        reactContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
  }

  private fun recordResult(result: ScanResult) {
    val address = try {
      result.device.address ?: "unknown"
    } catch (_: SecurityException) {
      "permission-denied"
    }
    val name = try {
      result.device.name ?: result.scanRecord?.deviceName
    } catch (_: SecurityException) {
      result.scanRecord?.deviceName
    }
    val services =
        result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
    scanResults[address] = DeviceResult(address, name, result.rssi, services)
  }

  @Synchronized
  private fun connectToPhone(result: ScanResult) {
    if (receivePromise == null || receiveGatt != null) return
    try {
      receiveScanCallback?.let { receiveScanner?.stopScan(it) }
      receiveDeviceName = result.device.name ?: result.scanRecord?.deviceName
      receiveDeviceAddress = result.device.address
      receiveRssi = result.rssi
      receiveGatt =
          result.device.connectGatt(
              reactContext,
              false,
              receiveGattCallback,
              BluetoothDevice.TRANSPORT_LE)
      if (receiveGatt == null) {
        finishReceive("gatt_connect_failed", "Could not connect to the LinkBridge phone service")
      }
    } catch (error: Throwable) {
      finishReceive("gatt_connect_failed", error.message ?: "Could not connect to the phone")
    }
  }

  private val receiveGattCallback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
      if (status != BluetoothGatt.GATT_SUCCESS) {
        finishReceive("gatt_connection_error", "Bluetooth connection failed with status $status")
        return
      }
      if (newState == BluetoothProfile.STATE_CONNECTED) {
        try {
          gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
          if (!gatt.requestMtu(517)) discoverServices(gatt)
          // Android permits only one queued GATT operation. The Nomad can take
          // roughly two seconds to negotiate MTU, so service discovery normally
          // starts from onMtuChanged. This is only a lost-callback fallback.
          mainHandler.postDelayed({ discoverServices(gatt) }, 5_000L)
        } catch (error: Throwable) {
          finishReceive("gatt_setup_failed", error.message ?: "Could not prepare the connection")
        }
      } else if (newState == BluetoothProfile.STATE_DISCONNECTED && gatt === receiveGatt) {
        if (receivePromise != null) {
          finishReceive("gatt_disconnected", "The phone disconnected before sending the link")
        } else {
          val pendingAck = receiveAckPromise
          receiveAckPromise = null
          closeReceiveSession()
          pendingAck?.reject(
              "ack_disconnected", "The phone disconnected before confirming the saved link")
        }
      }
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
      if (status == BluetoothGatt.GATT_SUCCESS) receiveMtu = mtu
      discoverServices(gatt)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
      if (receivePromise == null) return
      if (status != BluetoothGatt.GATT_SUCCESS) {
        finishReceive("service_discovery_failed", "Service discovery failed with status $status")
        return
      }
      val characteristic =
          receiveServiceUuid?.let(gatt::getService)
              ?.getCharacteristic(receiveMetadataCharacteristicUuid)
      if (characteristic == null) {
        finishReceive(
            "metadata_characteristic_missing", "The phone did not expose LinkBridge item metadata")
        return
      }
      if (!gatt.readCharacteristic(characteristic)) {
        finishReceive("metadata_read_failed", "Could not request item metadata from the phone")
      }
    }

    @Suppress("DEPRECATION")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
      if (status != BluetoothGatt.GATT_SUCCESS) {
        finishReceive("item_read_failed", "Link read failed with status $status")
        return
      }
      when (characteristic.uuid) {
        receiveMetadataCharacteristicUuid -> {
          if (!acceptReceivedMetadata(characteristic.value)) return
          val urlCharacteristic =
              receiveServiceUuid?.let(gatt::getService)
                  ?.getCharacteristic(receiveUrlCharacteristicUuid)
          if (urlCharacteristic == null) {
            finishReceive("url_characteristic_missing", "The phone did not expose the link URL")
          } else if (!gatt.readCharacteristic(urlCharacteristic)) {
            finishReceive("url_read_failed", "Could not request the URL from the phone")
          }
        }
        receiveUrlCharacteristicUuid -> acceptReceivedUrl(characteristic.value)
        else -> finishReceive("unexpected_characteristic", "The phone returned an unexpected value")
      }
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
      if (characteristic.uuid != receiveAckCharacteristicUuid || gatt !== receiveGatt) return
      val promise = receiveAckPromise ?: return
      receiveAckPromise = null
      receiveSessionTimeout?.let(mainHandler::removeCallbacks)
      receiveSessionTimeout = null
      if (status == BluetoothGatt.GATT_SUCCESS) {
        promise.resolve(true)
      } else {
        promise.reject("ack_write_failed", "Save confirmation failed with status $status")
      }
      closeReceiveSession()
    }
  }

  @Synchronized
  private fun discoverServices(gatt: BluetoothGatt) {
    if (receivePromise == null || receiveDiscoveryStarted || gatt !== receiveGatt) return
    receiveDiscoveryStarted = true
    try {
      if (!gatt.discoverServices()) {
        finishReceive("service_discovery_failed", "Could not start service discovery")
      }
    } catch (error: Throwable) {
      finishReceive("service_discovery_failed", error.message ?: "Could not discover services")
    }
  }

  private fun acceptReceivedMetadata(bytes: ByteArray?): Boolean {
    val raw = bytes?.toString(StandardCharsets.UTF_8)?.trim().orEmpty()
    try {
      val metadata = JSONObject(raw)
      if (metadata.optInt("v", 0) != 1) {
        finishReceive("unsupported_protocol", "The phone is using an unsupported LinkBridge version")
        return false
      }
      val itemId = metadata.optString("id").trim()
      if (itemId.isEmpty() || itemId.length > 128) {
        finishReceive("invalid_item_id", "The phone sent an invalid LinkBridge item ID")
        return false
      }
      receivedItemId = itemId
      receivedTitle = metadata.optString("title").replace(Regex("\\s+"), " ").trim()
      receivedCreatedAt = metadata.optLong("createdAt", 0L)
      return true
    } catch (error: Throwable) {
      finishReceive("invalid_metadata", "The phone sent malformed LinkBridge metadata")
      return false
    }
  }

  private fun acceptReceivedUrl(bytes: ByteArray?) {
    val url = bytes?.toString(StandardCharsets.UTF_8)?.trim().orEmpty()
    if (!(url.startsWith("https://") || url.startsWith("http://"))) {
      finishReceive("invalid_url", "The phone returned text that is not an HTTP URL")
      return
    }
    if (url.toByteArray(StandardCharsets.UTF_8).size > 500) {
      finishReceive("url_too_long", "The phone returned a URL larger than this LinkBridge version supports")
      return
    }
    deliverReceivedItem(url)
  }

  @Synchronized
  private fun deliverReceivedItem(url: String) {
    val promise = receivePromise ?: return
    val itemId = receivedItemId
    if (itemId == null) {
      finishReceive("metadata_missing", "The phone sent a URL without item metadata")
      return
    }
    try {
      receiveScanCallback?.let { receiveScanner?.stopScan(it) }
    } catch (_: Throwable) {
    }
    receiveTimeout?.let(mainHandler::removeCallbacks)
    receiveTimeout = null
    receiveScanner = null
    receiveScanCallback = null
    receivePromise = null

    val sessionTimeout = Runnable { closeReceiveSession() }
    receiveSessionTimeout = sessionTimeout
    mainHandler.postDelayed(sessionTimeout, 45_000L)

    val response = Arguments.createMap()
    response.putInt("version", 1)
    response.putString("id", itemId)
    response.putString("url", url)
    response.putString("title", receivedTitle)
    response.putDouble("createdAt", receivedCreatedAt.toDouble())
    response.putString("deviceName", receiveDeviceName)
    response.putString("deviceAddress", receiveDeviceAddress)
    response.putInt("rssi", receiveRssi)
    response.putInt("mtu", receiveMtu)
    response.putDouble("durationMs", (System.currentTimeMillis() - receiveStartedAt).toDouble())
    promise.resolve(response)
  }

  @Synchronized
  private fun finishReceive(
      errorCode: String? = null,
      errorMessage: String? = null
  ) {
    val promise = receivePromise ?: return
    receivePromise = null
    closeReceiveSession()
    if (errorCode != null) {
      promise.reject(errorCode, errorMessage)
    } else {
      promise.reject("receive_ended", errorMessage ?: "LinkBridge receiving ended")
    }
  }

  @Synchronized
  private fun closeReceiveSession() {
    try {
      receiveScanCallback?.let { receiveScanner?.stopScan(it) }
    } catch (_: Throwable) {
    }
    try {
      receiveGatt?.disconnect()
      receiveGatt?.close()
    } catch (_: Throwable) {
    }
    receiveTimeout?.let(mainHandler::removeCallbacks)
    receiveSessionTimeout?.let(mainHandler::removeCallbacks)

    receivePromise = null
    receiveScanner = null
    receiveScanCallback = null
    receiveGatt = null
    receiveTimeout = null
    receiveSessionTimeout = null
    receiveDiscoveryStarted = false
    receivedItemId = null
    receivedTitle = ""
    receivedCreatedAt = 0L
  }

  @Synchronized
  private fun finishScan(errorCode: String? = null, errorMessage: String? = null) {
    val promise = activePromise ?: return
    val callback = activeCallback
    try {
      if (callback != null) {
        activeScanner?.stopScan(callback)
      }
    } catch (_: SecurityException) {
      // Report the original outcome; a permission error while stopping is non-fatal.
    }

    activePromise = null
    activeCallback = null
    activeScanner = null
    scanTimeout?.let(mainHandler::removeCallbacks)
    scanTimeout = null

    if (errorCode != null) {
      promise.reject(errorCode, errorMessage)
      return
    }

    val response = Arguments.createMap()
    response.putString("requestedServiceUuid", requestedServiceUuid)
    response.putDouble("durationMs", (System.currentTimeMillis() - scanStartedAt).toDouble())
    val devices = Arguments.createArray()
    scanResults.values.forEach { device ->
      val item = Arguments.createMap()
      item.putString("address", device.address)
      item.putString("name", device.name)
      item.putInt("rssi", device.rssi)
      val serviceArray = Arguments.createArray()
      device.serviceUuids.forEach(serviceArray::pushString)
      item.putArray("serviceUuids", serviceArray)
      devices.pushMap(item)
    }
    response.putArray("devices", devices)
    promise.resolve(response)
  }
}
