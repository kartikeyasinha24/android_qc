package com.punchthrough.blestarterappandroid

import android.os.Environment
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.punchthrough.blestarterappandroid.ble.ConnectionEventListener
import com.punchthrough.blestarterappandroid.ble.ConnectionManager
import com.punchthrough.blestarterappandroid.ble.ConnectionManager.parcelableExtraCompat
import com.punchthrough.blestarterappandroid.ble.isIndicatable
import com.punchthrough.blestarterappandroid.ble.isNotifiable
import com.punchthrough.blestarterappandroid.ble.isReadable
import com.punchthrough.blestarterappandroid.ble.isWritable
import com.punchthrough.blestarterappandroid.ble.isWritableWithoutResponse
import com.punchthrough.blestarterappandroid.ble.toHexString
import com.punchthrough.blestarterappandroid.ble.toUtf8String
import java.io.IOException
import java.io.OutputStream
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import com.punchthrough.blestarterappandroid.databinding.ActivityBleOperationsBinding
import fi.iki.elonen.NanoHTTPD
import okhttp3.MediaType.Companion.toMediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.nio.charset.StandardCharsets
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class BleOperationsActivity : AppCompatActivity() {
    private lateinit var wakeLock: PowerManager.WakeLock
    private var server: MyHTTPServer? = null
    private lateinit var binding: ActivityBleOperationsBinding
    private val device: BluetoothDevice by lazy {
        intent.parcelableExtraCompat(BluetoothDevice.EXTRA_DEVICE)
            ?: error("Missing BluetoothDevice from MainActivity!")
    }
    private val dateFormatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)
    private val characteristics by lazy {
        ConnectionManager.servicesOnDevice(device)?.flatMap { service ->
            service.characteristics ?: listOf()
        } ?: listOf()
    }
    inner class MyHTTPServer : NanoHTTPD(8003) { // Define the port number
        override fun serve(session: IHTTPSession): Response {
            // Get the request method and URI (URL)
            val method = session.method
            val uri = session.uri

            // Print the request method and URL in the console
            println("Request Method: $method")
            println("Request URL: $uri")

            // Create a dynamic JSON response with the temperature, illuminance, method, and URL
            val response = """
                {
                    "message": "Hello from Android",
                    "HeartRate": "$heart",
                    "SPO2":"$spo2",
                    "O2":"$o2",
                    "PM1":"$pm1",
                    "PM2":"$pm2",
                    "PM10":"$pm10",

                    "RequestMethod": "$method",
                    "RequestURL": "$uri"
                }
            """.trimIndent()

            return newFixedLengthResponse(Response.Status.OK, "application/json", response)
        }
    }
    private var heart: String = "91"
    private var spo2: String = "98"
    private var o2: String = "21%"
    private var pm1: String = "31"
    private var pm2: String = "45"
    private var pm10: String = "49"
    private fun startHTTPServerWithRandomData() {
        // Initialize and start the server
        server = MyHTTPServer()
        try {
            server?.start()
            println("Server is running on port 8003")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private val characteristicProperties by lazy {
        characteristics.associateWith { characteristic ->
            mutableListOf<CharacteristicProperty>().apply {
                if (characteristic.isNotifiable()) add(CharacteristicProperty.Notifiable)
                if (characteristic.isIndicatable()) add(CharacteristicProperty.Indicatable)
                if (characteristic.isReadable()) add(CharacteristicProperty.Readable)
                if (characteristic.isWritable()) add(CharacteristicProperty.Writable)
                if (characteristic.isWritableWithoutResponse()) {
                    add(CharacteristicProperty.WritableWithoutResponse)
                }
            }.toList()
        }
    }

    private val characteristicAdapter: CharacteristicAdapter by lazy {
        CharacteristicAdapter(characteristics) { characteristic ->
            showCharacteristicOptions(characteristic)
        }
    }
    private val notifyingCharacteristics = mutableListOf<UUID>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConnectionManager.registerListener(connectionEventListener)
        acquireWakeLock()
        binding = ActivityBleOperationsBinding.inflate(layoutInflater)

        setContentView(binding.root)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            title = getString(R.string.ble_playground)
        }
        setupRecyclerView()
        startHTTPServerWithRandomData()

        // Start the foreground service
        val serviceIntent = Intent(this, BluetoothForegroundService::class.java).apply {
            putExtra(BluetoothDevice.EXTRA_DEVICE, device)
        }
        startForegroundService(this, serviceIntent)

        val commandUUID = UUID.fromString("00001027-1212-efde-1523-785feabcd123")
        val commandCharacteristic = characteristics.find { it.uuid == commandUUID }
        commandCharacteristic?.let {
            val defaultHexValues = listOf("7365745F63666720", "73747265616D206173636969", "0d", "72656164207070672035", "0d")
            defaultHexValues.forEach { hexValue ->
                val bytes = hexValue.hexToBytes()
//                log("Writing to $commandUUID: ${bytes.toHexString()}")
                ConnectionManager.writeCharacteristic(device, it, bytes)

            }
        }
        // Enable notifications for the specific UUID
        val characteristicUUID = UUID.fromString("00001011-1212-efde-1523-785feabcd123")
        val characteristic = characteristics.find { it.uuid == characteristicUUID }
        characteristic?.let {
            log("Turning notifications on")
            ConnectionManager.enableNotifications(device, it)
        }
    }

    override fun onDestroy() {
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        stopService(Intent(this, BluetoothForegroundService::class.java))
        server?.stop()
        server = null
        super.onDestroy()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::BLEWakelockTag")
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)
    }

    private fun setupRecyclerView() {
        binding.characteristicsRecyclerView.apply {
            adapter = characteristicAdapter
            layoutManager = LinearLayoutManager(
                this@BleOperationsActivity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false

            itemAnimator.let {
                if (it is SimpleItemAnimator) {
                    it.supportsChangeAnimations = false
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun log(message: String) {
        val formattedMessage = "${dateFormatter.format(Date())}: $message"
        runOnUiThread {
            val uiText = binding.logTextView.text
            val currentLogText = uiText.ifEmpty { "Beginning of log." }
            binding.logTextView.text = "$currentLogText\n$formattedMessage"
            binding.logScrollView.post { binding.logScrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun showCharacteristicOptions(
        characteristic: BluetoothGattCharacteristic
    ) = runOnUiThread {
        characteristicProperties[characteristic]?.let { properties ->
            AlertDialog.Builder(this)
                .setTitle("Select an action to perform")
                .setItems(properties.map { it.action }.toTypedArray()) { _, i ->
                    when (properties[i]) {
                        CharacteristicProperty.Readable -> {
//                            log("Reading from ${characteristic.uuid}")
                            ConnectionManager.readCharacteristic(device, characteristic)
                        }
                        CharacteristicProperty.Writable, CharacteristicProperty.WritableWithoutResponse -> {
                            showWritePayloadDialog(characteristic)
                        }
                        CharacteristicProperty.Notifiable, CharacteristicProperty.Indicatable -> {
                            if (notifyingCharacteristics.contains(characteristic.uuid)) {
                                log("Disabling notifications on ${characteristic.uuid}")
                                ConnectionManager.disableNotifications(device, characteristic)
                            } else {
//                                log("Reading the data")
                                ConnectionManager.enableNotifications(device, characteristic)
                            }
                        }
                    }
                }
                .show()
        }
    }

    @SuppressLint("InflateParams")
    private fun showWritePayloadDialog(characteristic: BluetoothGattCharacteristic) {
        val defaultHexValues = listOf("7365745F63666720", "73747265616D206173636969", "0d", "72656164207070672035", "0d") // Example default hex values

        defaultHexValues.forEach { hexValue ->
            val bytes = hexValue.hexToBytes()
//            log("Writing to ${characteristic.uuid}: ${bytes.toHexString()}")
            ConnectionManager.writeCharacteristic(device, characteristic, bytes)
        }
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onDisconnect = {
                runOnUiThread {
                    AlertDialog.Builder(this@BleOperationsActivity)
                        .setTitle("Disconnected")
                        .setMessage("Disconnected from device.")
                        .setPositiveButton("OK") { _, _ -> onBackPressed() }
                        .show()
                }
            }

            onCharacteristicRead = { _, characteristic, value ->
                log("Read from ${characteristic.uuid}: ${value.toHexString()}")
            }

            onCharacteristicWrite = { _, characteristic ->
//                log("Wrote to ${characteristic.uuid}")
            }

            onMtuChanged = { _, mtu ->
                log("MTU updated to $mtu")
            }

            onCharacteristicChanged = { _, characteristic, value ->
                val utf8String = value.toUtf8String()
//                log("Value changed on ${characteristic.uuid}: $utf8String")
                val fruitArray: Array<String> = utf8String.split(",").map { it.trim() }.toTypedArray()
                heart = fruitArray[2]
                spo2 = fruitArray[9]
            }

            onNotificationsEnabled = { _, characteristic ->
                log("Reading the data ....")
                notifyingCharacteristics.add(characteristic.uuid)
            }

            onNotificationsDisabled = { _, characteristic ->
                log("Disabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.remove(characteristic.uuid)
            }
        }
    }

    private enum class CharacteristicProperty {
        Readable,
        Writable,
        WritableWithoutResponse,
        Notifiable,
        Indicatable;

        val action
            get() = when (this) {
                Readable -> "Read"
                Writable -> "Write"
                WritableWithoutResponse -> "Write Without Response"
                Notifiable -> "Toggle Notifications"
                Indicatable -> "Toggle Indications"
            }
    }

    private fun Activity.hideKeyboard() {
        hideKeyboard(currentFocus ?: View(this))
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun EditText.showKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        requestFocus()
        inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun String.hexToBytes() =
        this.chunked(2).map { it.uppercase(Locale.US).toInt(16).toByte() }.toByteArray()
}