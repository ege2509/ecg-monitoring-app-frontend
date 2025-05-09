package com.example.ens492frontend

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    private fun connectToEcgService() {
        // Connect the WebSocket service
        lifecycleScope.launch {
            try {
                Log.d("ECG", "Connecting to WebSocket service")

                // Connect the visualization to receive data FIRST
                ecgVisualization.connectToEcgService(lifecycleScope, webSocketService)

                // If using real server connection
                if (!isInEditMode) {
                    webSocketService.connect()
                    Log.d("ECG", "Connected to real WebSocket server")
                }

                // Start simulation job
                webSocketService.startSimulation(lifecycleScope)


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
        // Cancel simulation if running
        simulationJob?.cancel()
        simulationJob = null

        // Disconnect the WebSocket
        webSocketService.disconnect()

        // Disconnect the visualization
        ecgVisualization.disconnectFromEcgService()
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
        get() = true  // Change to false for production with real server
}