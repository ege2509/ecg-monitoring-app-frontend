package com.example.ens492frontend

import android.util.Log
import kotlinx.coroutines.Dispatchers
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

/**
 * Service for handling WebSocket connections to the ECG data server
 */
class WebSocketService {
    companion object {
        private const val TAG = "WebSocketService"
        private const val ECG_WEBSOCKET_URL = "ws://your-ecg-server-url/ws" // Replace with your actual WebSocket URL
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
        // Just for demonstration - in a real app, use a more robust reconnection strategy
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (webSocket == null) {
                // Only try to reconnect if we're not already connected
                kotlinx.coroutines.GlobalScope.launch {
                    connect()
                }
            }
        }, RECONNECT_DELAY)
    }

    /**
     * For testing or previewing, you can send simulated data
     */
    fun sendSimulatedData() {
        val sampleData = """
            {
                "heartRate": 75,
                "leads": [
                    {
                        "lead": 1,
                        "data": [0.0, 0.1, 0.2, 0.0, -0.1, 0.0, 0.5, 1.5, 1.0, -0.5, -1.0, -0.3, 0.0]
                    },
                    {
                        "lead": 2,
                        "data": [0.0, 0.1, 0.3, 0.1, -0.2, -0.1, 0.6, 1.7, 0.9, -0.6, -0.9, -0.2, 0.0]
                    }
                ],
                "abnormalities": {
                    "RBBB": 0.85,
                    "AF": 0.3
                }
            }
        """.trimIndent()

        _ecgDataFlow.tryEmit(sampleData)
    }
}