package com.example.ens492frontend

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Service to handle WebSocket connections for ECG data
 */
class WebSocketService {
    companion object {
        private const val TAG = "WebSocketService"
        private const val ECG_WEBSOCKET_URL = "ws://10.0.2.2:8080/ws/ecg?userId=1"
        private const val RECONNECT_DELAY = 5000L // 5 seconds delay for reconnect attempts
        private const val NUM_LEADS = 12 // Standard 12-lead ECG
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket connections
        .build()

    private var webSocket: WebSocket? = null
    private var simulationJob: kotlinx.coroutines.Job? = null

    // Data flow of lead data map (lead index -> float array of values)
    private val _ecgDataFlow = MutableSharedFlow<Map<Int, FloatArray>>(replay = 0)
    val ecgDataFlow: SharedFlow<Map<Int, FloatArray>> = _ecgDataFlow

    // Heart rate data flow
    private val _heartRateFlow = MutableStateFlow(0)
    val heartRateFlow: StateFlow<Int> = _heartRateFlow.asStateFlow()

    // Abnormalities data flow
    private val _abnormalitiesFlow = MutableSharedFlow<Map<String, Float>>(replay = 0)
    val abnormalitiesFlow: SharedFlow<Map<String, Float>> = _abnormalitiesFlow

    /**
     * Connect to the WebSocket server
     */
    suspend fun connect() {
        withContext(Dispatchers.IO) {
            if (webSocket != null) {
                Log.w(TAG, "WebSocket already connected or connecting")
                return@withContext
            }

            val request = Request.Builder()
                .url(ECG_WEBSOCKET_URL)
                .build()

            webSocket = client.newWebSocket(request, createWebSocketListener())
            Log.d(TAG, "WebSocket connect request sent")
        }
    }

    /**
     * Disconnect from the WebSocket server
     */
    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        stopSimulation()
        Log.d(TAG, "WebSocket disconnected")
    }

    /**
     * Create the WebSocket listener to handle events
     */
    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Print the raw JSON to see exactly what we're receiving
                Log.d(TAG, "Raw JSON received: $text")

                // Process incoming JSON message
                try {
                    // Parse the JSON message
                    val jsonObject = JSONObject(text)

                    // Log all top-level keys to understand the structure
                    val keys = jsonObject.keys()
                    val keyList = mutableListOf<String>()
                    while (keys.hasNext()) {
                        keyList.add(keys.next())
                    }
                    Log.d(TAG, "JSON contains keys: $keyList")

                    // Get timestamp and heart rate
                    val timestamp = jsonObject.optLong("timestamp")
                    val heartRate = jsonObject.optInt("heartRate", 0)
                    Log.d(TAG, "Timestamp: $timestamp, Heart Rate: $heartRate")

                    _heartRateFlow.tryEmit(heartRate)

                    // Initialize leadDataMap
                    val leadDataMap = mutableMapOf<Int, FloatArray>()
                    var leadsFound = false

                    // SIMPLIFIED APPROACH: Directly check for ecgData and process it
                    if (jsonObject.has("ecgData")) {
                        val ecgData = jsonObject.getJSONArray("ecgData")
                        Log.d(TAG, "Found ecgData array with ${ecgData.length()} leads")

                        // Process each lead in the ecgData array
                        for (i in 0 until ecgData.length()) {
                            try {
                                val leadArray = ecgData.getJSONArray(i)
                                val values = FloatArray(leadArray.length())
                                for (j in 0 until leadArray.length()) {
                                    values[j] = leadArray.getDouble(j).toFloat()
                                }
                                leadDataMap[i] = values
                                leadsFound = true
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing ecgData lead $i: ${e.message}")
                            }
                        }

                        if (leadsFound) {
                            Log.d(TAG, "Successfully processed ${leadDataMap.size} leads from ecgData")
                        }
                    } else {
                        Log.d(TAG, "No ecgData field found in the JSON message")
                    }

                    // Get abnormalities data
                    val abnormalitiesMap = mutableMapOf<String, Float>()
                    if (jsonObject.has("abnormalities")) {
                        // [existing abnormalities processing code]
                    }

                    // Emit all collected data using coroutines
                    CoroutineScope(Dispatchers.IO).launch {
                        if (heartRate > 0) {
                            _heartRateFlow.emit(heartRate)
                            Log.d(TAG, "Heart rate emitted: $heartRate")
                        }

                        if (abnormalitiesMap.isNotEmpty()) {
                            _abnormalitiesFlow.emit(abnormalitiesMap)
                            Log.d(TAG, "Abnormalities data emitted: ${abnormalitiesMap.keys}")
                        }

                        if (leadDataMap.isNotEmpty()) {
                            _ecgDataFlow.emit(leadDataMap)
                            Log.d(TAG, "ECG data emitted for ${leadDataMap.size} leads")
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing ECG data: ${e.message}")
                    e.printStackTrace()
                }

            }

            /**
             * Process a JSONObject that contains leads as key-value pairs
             * where keys are lead numbers and values are arrays of float values
             */
            private fun processLeadsObject(leadsObject: JSONObject, leadDataMap: MutableMap<Int, FloatArray>): Boolean {
                var foundAnyLead = false
                val keys = leadsObject.keys()
                val keysList = mutableListOf<String>()
                while (keys.hasNext()) {
                    keysList.add(keys.next())
                }
                Log.d(TAG, "Processing leads object with keys: $keysList")

                for (leadKey in keysList) {
                    try {
                        // Try to get the lead index
                        val leadIndex = leadKey.toIntOrNull()
                        if (leadIndex == null) {
                            Log.d(TAG, "Skipping non-numeric key: $leadKey")
                            continue
                        }

                        val leadArray = leadsObject.getJSONArray(leadKey)
                        Log.d(TAG, "Processing lead $leadKey with ${leadArray.length()} values")

                        val values = FloatArray(leadArray.length())
                        for (j in 0 until leadArray.length()) {
                            values[j] = leadArray.getDouble(j).toFloat()
                        }

                        // Store with 0-based index for internal use
                        leadDataMap[leadIndex] = values
                        foundAnyLead = true
                        Log.d(TAG, "Successfully processed lead $leadKey")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing lead $leadKey: ${e.message}")
                    }
                }
                return foundAnyLead
            }

            /**
             * Process a JSONArray that contains an array of lead arrays
             * where each element is an array of float values for one lead
             */
            private fun processLeadArray(leadsArray: JSONArray, leadDataMap: MutableMap<Int, FloatArray>): Boolean {
                var foundAnyLead = false
                Log.d(TAG, "Processing lead array with ${leadsArray.length()} elements")

                for (i in 0 until leadsArray.length()) {
                    try {
                        val leadArray = leadsArray.getJSONArray(i)
                        Log.d(TAG, "Processing lead $i with ${leadArray.length()} values")

                        val values = FloatArray(leadArray.length())
                        for (j in 0 until leadArray.length()) {
                            values[j] = leadArray.getDouble(j).toFloat()
                        }

                        leadDataMap[i] = values
                        foundAnyLead = true
                        Log.d(TAG, "Successfully processed lead $i")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing lead at index $i: ${e.message}")
                    }
                }
                return foundAnyLead
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                handleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                this@WebSocketService.webSocket = null
            }
        }
    }

    /**
     * Handle reconnection attempts
     */
    private fun handleReconnect() {
        CoroutineScope(Dispatchers.IO).launch {
            delay(RECONNECT_DELAY)
            if (webSocket == null) {
                Log.d(TAG, "Attempting to reconnect...")
                connect()
            }
        }
    }

    /**
     * Start simulation mode (for testing without a server)
     */
    fun startSimulation(scope: CoroutineScope) {
        // Stop any existing simulation
        stopSimulation()

        simulationJob = scope.launch {
            while (true) {
                // Generate simulated data
                val simulatedData = generateSimulatedEcgData()

                // Emit lead data to flow
                _ecgDataFlow.emit(simulatedData)

                // Emit simulated heart rate
                _heartRateFlow.emit(70 + Random.nextInt(-5, 6))

                // Emit simulated abnormalities (mostly normal, occasionally showing an abnormality)
                if (Random.nextInt(10) < 2) { // 20% chance of showing abnormality
                    val abnormalities = mapOf(
                        "SB" to 0.1f + Random.nextFloat() * 0.2f,
                        "RBBB" to 0.1f + Random.nextFloat() * 0.3f,
                        "LBBB" to 0.0f,
                        "1dAVb" to 0.0f,
                        "AF" to 0.0f,
                        "ST" to 0.0f
                    )
                    _abnormalitiesFlow.emit(abnormalities)
                } else {
                    // Normal condition
                    _abnormalitiesFlow.emit(mapOf(
                        "SB" to 0.0f,
                        "RBBB" to 0.0f,
                        "LBBB" to 0.0f,
                        "1dAVb" to 0.0f,
                        "AF" to 0.0f,
                        "ST" to 0.0f
                    ))
                }

                // Delay between data packets
                delay(150)
            }
        }
    }

    /**
     * Stop simulation mode
     */
    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    /**
     * Generate simulated ECG data for all leads
     */
    fun generateSimulatedEcgData(): Map<Int, FloatArray> {
        val result = mutableMapOf<Int, FloatArray>()

        // Basic ECG waveform pattern (PQRST complex)
        val basePattern = floatArrayOf(
            0.0f, 0.0f, 0.1f, 0.2f, 0.0f, -0.1f, -0.1f, 0.0f, 0.5f, 1.5f, 1.0f,
            -0.5f, -1.0f, -0.3f, 0.0f, 0.2f, 0.4f, 0.3f, 0.0f, -0.1f, 0.0f
        )

        // Generate data for each lead
        for (leadIndex in 0 until NUM_LEADS) {
            // Create data points with lead-specific variations
            val dataPoints = FloatArray(20) // 20 data points per update

            for (i in dataPoints.indices) {
                val baseValue = basePattern[i % basePattern.size]

                // Each lead has a different morphology
                val leadAmplitude = when (leadIndex) {
                    0 -> 1.0f    // Lead I
                    1 -> 1.2f    // Lead II
                    2 -> 0.7f    // Lead III
                    3 -> -0.5f   // aVR
                    4 -> 0.8f    // aVL
                    5 -> 1.1f    // aVF
                    6 -> 1.7f    // V1
                    7 -> 2.0f    // V2
                    8 -> 2.5f    // V3
                    9 -> 2.2f    // V4
                    10 -> 1.8f   // V5
                    11 -> 1.5f   // V6
                    else -> 1.0f
                }

                // Lead-specific variation
                val leadVariation = leadAmplitude * baseValue

                // Small random noise
                val noise = (Random.nextFloat() - 0.5f) * 0.05f

                dataPoints[i] = leadVariation + noise
            }

            result[leadIndex] = dataPoints
        }

        return result
    }


    private fun processWebSocketMessage(message: String) {
        try {
            // Parse the JSON message from the server
            val jsonObject = JSONObject(message)

            // Extract heart rate and abnormalities data
            val heartRate = jsonObject.getInt("heartRate")

            // Process abnormalities if present
            val abnormalities = mutableMapOf<String, Float>()
            if (jsonObject.has("abnormalities")) {
                val abnormalityObj = jsonObject.getJSONObject("abnormalities")
                val keys = abnormalityObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    abnormalities[key] = abnormalityObj.getDouble(key).toFloat()
                }
            }

            // Process the lead data that the UI expects
            val leadData = mutableMapOf<Int, FloatArray>()
            if (jsonObject.has("leadData")) {
                val leadDataObj = jsonObject.getJSONObject("leadData")
                val leadKeys = leadDataObj.keys()

                while (leadKeys.hasNext()) {
                    val leadKey = leadKeys.next()
                    val leadIndex = leadKey.toInt()
                    val dataArray = leadDataObj.getJSONArray(leadKey)

                    // Convert JSON array to FloatArray
                    val dataPoints = FloatArray(dataArray.length())
                    for (i in 0 until dataArray.length()) {
                        dataPoints[i] = dataArray.getDouble(i).toFloat()
                    }

                    // Store in the format the UI component expects
                    leadData[leadIndex] = dataPoints
                }
            }

            // Emit the processed data to listeners
            CoroutineScope(Dispatchers.IO).launch {
                _heartRateFlow.emit(heartRate)
                _abnormalitiesFlow.emit(abnormalities)
                _ecgDataFlow.emit(leadData)  // This is the key data the UI component uses
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing WebSocket message", e)
        }
    }


    /**
     * Process sample data format (for importing from CSV or other sources)
     * Format: sampleNum,lead1val,lead2val,...,lead12val
     */
    fun processSampleData(sampleData: String): Map<Int, FloatArray>? {
        try {
            val values = sampleData.split(",")
            if (values.size != NUM_LEADS + 1) {
                Log.e(TAG, "Invalid sample data format. Expected ${NUM_LEADS + 1} values, got ${values.size}")
                return null
            }

            val sampleNum = values[0].toInt() // Get sample number
            val result = mutableMapOf<Int, FloatArray>()

            // Extract lead values (index 1 to NUM_LEADS)
            for (i in 0 until NUM_LEADS) {
                val leadValue = values[i + 1].toFloat()
                result[i] = floatArrayOf(leadValue)
            }

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error processing sample data: ${e.message}", e)
            return null
        }
    }
}