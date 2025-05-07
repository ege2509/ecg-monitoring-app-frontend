package com.example.ens492frontend

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class EcgActivity : AppCompatActivity() {

    private lateinit var ecgVisualization: EcgVisualizationView
    private lateinit var leadSelector: Spinner
    private lateinit var connectButton: Button
    private lateinit var statusText: TextView

    private lateinit var webSocketService: WebSocketService
    private var webSocketJob: Job? = null
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

        // Setup lead selector
        setupLeadSelector()

        // Setup connect button
        setupConnectButton()

        // Use simulated data for testing (remove in production)
        if (isInEditMode) {
            ecgVisualization.simulateEcgData()
        }
    }

    private fun setupLeadSelector() {
        // Create adapter for the leads spinner
        val leadAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, leads)
        leadAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        leadSelector.adapter = leadAdapter

        // Set default selection (Lead II = index 1)
        leadSelector.setSelection(EcgVisualizationView.DEFAULT_LEAD_TO_DISPLAY)

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
            if (isConnected) {
                disconnectFromEcgService()
                connectButton.text = "Connect"
                statusText.text = "Disconnected"
                statusText.setTextColor(getColor(android.R.color.holo_red_dark))
            } else {
                connectToEcgService()
                connectButton.text = "Disconnect"
                statusText.text = "Connected"
                statusText.setTextColor(getColor(android.R.color.holo_green_dark))
            }
            isConnected = !isConnected
        }
    }

    private fun connectToEcgService() {
        // Connect the WebSocket service
        webSocketJob = lifecycleScope.launch {
            webSocketService.connect()

            // For testing: send simulated data periodically
            if (isInEditMode) {
                while (true) {
                    webSocketService.sendSimulatedData()
                    kotlinx.coroutines.delay(1000) // Send new data every second
                }
            }
        }

        // Connect the visualization to the WebSocket data flow
        ecgVisualization.connectToEcgService(lifecycleScope, webSocketService)
    }

    private fun disconnectFromEcgService() {
        // Disconnect the WebSocket service
        webSocketJob?.cancel()
        webSocketJob = null
        webSocketService.disconnect()

        // Disconnect the visualization
        ecgVisualization.disconnectFromEcgService()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Make sure to disconnect on activity destroy
        disconnectFromEcgService()
    }

    /**
     * Helper method to enable simulation mode for development
     * Set to true during development for simulated data
     */
    private val isInEditMode: Boolean
        get() = true  // Change to false for production with real server
}