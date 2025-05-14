package com.example.ens492frontend

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class EcgActivity : AppCompatActivity() {

    private lateinit var ecgVisualization: EcgVisualizationView
    private lateinit var leadSelector: Spinner
    private lateinit var connectButton: Button
    private lateinit var statusText: TextView
    private lateinit var gridDensityGroup: RadioGroup

    private lateinit var webSocketService: WebSocketService
    private var simulationJob: Job? = null
    private var isConnected = false

    // Available ECG leads
    private val leads = listOf("Lead I", "Lead II", "Lead III", "aVR", "aVL", "aVF", "V1", "V2", "V3", "V4", "V5", "V6")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ecg)

        // Initialize UI components
        ecgVisualization = findViewById(R.id.ecgVisualization)
        leadSelector = findViewById(R.id.leadSelector)
        connectButton = findViewById(R.id.connectButton)
        statusText = findViewById(R.id.statusText)

        // Setup WebSocket service
        webSocketService = WebSocketService()

        // Setup UI components
        setupLeadSelector()
        setupConnectButton()
    }

    private fun setupLeadSelector() {
        // Create adapter for the leads spinner
        val leadAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, leads)
        leadAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        leadSelector.adapter = leadAdapter

        // Set default selection (Lead II = index 1)
        leadSelector.setSelection(1) // Lead II is typically the default for rhythm analysis

        // Set lead change listener
        leadSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                ecgVisualization.setLeadToDisplay(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No action needed
            }
        }
    }

    private fun setupConnectButton() {
        connectButton.setOnClickListener {
            // Toggle connection state FIRST
            isConnected = !isConnected

            // Then act on the new state
            if (isConnected) {
                Log.d("ECG", "Connecting to ECG service...")
                connectToEcgService()
                //startContinuousTest()
                connectButton.text = "Disconnect"
                statusText.text = "Connected"
                statusText.setTextColor(getColor(android.R.color.holo_green_dark))
            } else {
                Log.d("ECG", "Disconnecting from ECG service...")
                disconnectFromEcgService()
                connectButton.text = "Connect"
                statusText.text = "Disconnected"
                statusText.setTextColor(getColor(android.R.color.holo_red_dark))
            }

            Log.d("ECG", "Connect button processed, new state: $isConnected")
        }
    }

    private fun startContinuousTest() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("${ApiClient.baseUrl}/api/ecg/test/continuous/1?seconds=30&intervalMs=150&includeAbnormal=true")
                    .post("".toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Continuous test started successfully")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@EcgActivity, "Continuous test started", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e(TAG, "Error starting test: ${response.code}, body: ${response.body?.string()}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception starting continuous test: ${e.message}", e)
            }
        }
    }



    private fun connectToEcgService() {
        // Connect the WebSocket service
        lifecycleScope.launch {
            try {
                Log.d("ECG", "Connecting to WebSocket service, isInEditMode = $isInEditMode")

                // Connect the visualization to receive data
                ecgVisualization.connectToEcgService(lifecycleScope, webSocketService)

                if (isInEditMode) {
                    // Start simulation for development
                    Log.d("ECG", "Running in edit mode - starting simulation")
                    //webSocketService.startSimulation(lifecycleScope)
                } else {
                    // Connect to real WebSocket server for production
                    Log.d("ECG", "Running in production mode - connecting to real server")
                    webSocketService.connect()

                    webSocketService.stopSimulation()
                    Log.d("ECG", "Connected to real WebSocket server")
                }

            } catch (e: Exception) {
                Log.e("ECG", "Error connecting to ECG service", e)
                runOnUiThread {
                    statusText.text = "Connection Failed"
                    statusText.setTextColor(getColor(android.R.color.holo_red_dark))
                    isConnected = false
                    connectButton.text = "Connect"
                }
            }
        }
    }

    private fun disconnectFromEcgService() {
        // Make sure to stop simulation if it's running
        webSocketService.stopSimulation()

        // Disconnect the WebSocket
        webSocketService.disconnect()

        // Disconnect the visualization
        ecgVisualization.disconnectFromEcgService()

        Log.d("ECG", "Fully disconnected from ECG service")
    }
    override fun onDestroy() {
        super.onDestroy()
        // Make sure to disconnect on activity destroy
        if (isConnected) {
            disconnectFromEcgService()
        }
    }

    /**
     * Helper method to enable simulation mode for development
     * Set to true during development for simulated data
     */
    private val isInEditMode: Boolean
        get() = false  // Change to false for production with real server
}