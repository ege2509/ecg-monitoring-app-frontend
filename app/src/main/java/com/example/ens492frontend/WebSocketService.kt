package com.example.ens492frontend

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Service to handle WebSocket connections for ECG data
 */
class WebSocketService {
    companion object {
        private const val TAG = "WebSocketService"
        private const val ECG_WEBSOCKET_URL = "ws://your-ecg-server-url/ws"
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
                // Process incoming message
                try {
                    // Format: leadIndex:value1,value2,value3|leadIndex:value1,value2,value3|...
                    val leadDataMap = mutableMapOf<Int, FloatArray>()

                    // Split by lead sections
                    val leadSections = text.split("|")
                    for (section in leadSections) {
                        val parts = section.split(":", limit = 2)
                        if (parts.size == 2) {
                            val leadIndex = parts[0].toInt()
                            val values = parts[1].split(",").map { it.toFloat() }.toFloatArray()
                            leadDataMap[leadIndex] = values
                        }
                    }

                    if (leadDataMap.isNotEmpty()) {
                        _ecgDataFlow.tryEmit(leadDataMap)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing ECG data", e)
                }
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

                // Emit to flow
                _ecgDataFlow.emit(simulatedData)

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
}