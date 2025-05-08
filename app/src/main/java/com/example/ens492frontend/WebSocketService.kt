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

class WebSocketService {
    companion object {
        private const val TAG = "WebSocketService"
        private const val ECG_WEBSOCKET_URL = "ws://your-ecg-server-url/ws" // Update this with your actual server URL
        private const val RECONNECT_DELAY = 5000L // 5 seconds delay for reconnect attempts
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket connections
        .build()

    private var webSocket: WebSocket? = null

    // Shared flow to broadcast ECG data to consumers
    private val _ecgDataFlow = MutableSharedFlow<String>(replay = 0)
    val ecgDataFlow: SharedFlow<String> = _ecgDataFlow

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
                // When we receive a message, emit it to the flow
                _ecgDataFlow.tryEmit(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                // Schedule reconnect attempt
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
     * For testing: send simulated ECG data
     */
    fun sendSimulatedData() {
        // Create simulated multi-lead ECG data in the same format as the backend
        val simulatedJson = buildSimulatedEcgJson()
        _ecgDataFlow.tryEmit(simulatedJson)
    }

    /**
     * Build simulated ECG JSON data matching the format from the backend
     */
    private fun buildSimulatedEcgJson(): String {
        // Basic ECG waveform pattern
        val pattern = listOf(0.0f, 0.0f, 0.1f, 0.2f, 0.0f, -0.1f, -0.1f, 0.0f, 0.5f, 1.5f, 1.0f,
            -0.5f, -1.0f, -0.3f, 0.0f, 0.2f, 0.4f, 0.3f, 0.0f, -0.1f, 0.0f)

        // Create data points for each lead (slightly different for each lead)
        val leadsData = StringBuilder()
        for (leadIndex in 0 until 12) { // 12 leads
            if (leadIndex > 0) leadsData.append(",")
            leadsData.append("{")
            leadsData.append("\"lead\": ${leadIndex + 1},")
            leadsData.append("\"data\": [")

            // Add 20 data points with slight variation per lead
            val dataPoints = StringBuilder()
            for (i in 0 until 20) {
                if (i > 0) dataPoints.append(",")
                val baseValue = pattern[i % pattern.size]
                val variation = (leadIndex * 0.1f) * baseValue
                dataPoints.append(baseValue + variation)
            }
            leadsData.append(dataPoints)
            leadsData.append("]")
            leadsData.append("}")
        }

        // Random heart rate between 60-100
        val heartRate = (60 + (Math.random() * 40)).toInt()

        // Random abnormality detection (occasionally)
        val hasAbnormality = Math.random() > 0.7
        val abnormalityValue = if (hasAbnormality) 0.75f + (Math.random() * 0.2).toFloat() else 0.1f

        // Build the complete JSON
        return """
        {
            "timestamp": ${System.currentTimeMillis()},
            "heartRate": $heartRate,
            "leads": [$leadsData],
            "abnormalities": {
                "RBBB": ${if (hasAbnormality) abnormalityValue else 0.1f},
                "AF": ${if (!hasAbnormality && Math.random() > 0.8) 0.8f else 0.05f},
                "1dAVb": 0.05,
                "LBBB": 0.02,
                "SB": ${if (heartRate < 65) 0.9f else 0.0f},
                "ST": ${if (heartRate > 95) 0.9f else 0.0f}
            }
        }
        """.trimIndent()
    }
}