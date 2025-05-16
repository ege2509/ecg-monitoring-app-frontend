package com.example.ens492frontend

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ens492frontend.models.EcgRecording
import kotlinx.coroutines.launch

class RecordingViewActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var leadSelector: Spinner
    private lateinit var ecgVisualization: RecordingVisualizationView
    private lateinit var backButton: Button

    private var recordingId: Long = -1
    private lateinit var recording: EcgRecording
    private val leads = listOf("Lead I", "Lead II", "Lead III", "aVR", "aVL", "aVF", "V1", "V2", "V3", "V4", "V5", "V6")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording_view)

        // Get recording ID from intent
        recordingId = intent.getLongExtra("RECORDING_ID", -1L)
        if (recordingId == -1L) {
            Toast.makeText(this, "Error: No recording selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize views
        titleText = findViewById(R.id.titleText)
        leadSelector = findViewById(R.id.leadSelector)
        ecgVisualization = findViewById(R.id.ecgVisualization)
        backButton = findViewById(R.id.backButton)

        backButton.setOnClickListener {
            finish()
        }

        // Load recording data
        loadRecordingData()

        // Setup lead selector
        setupLeadSelector()
    }

    private fun loadRecordingData() {
        lifecycleScope.launch {
            try {
                // Load recording from API
                val recordingData = UserApi.getRecording(recordingId)

                // Update UI with recording data
                titleText.text = "ECG Recording - ${recordingData.recordingDate}"

                // Parse processed data for visualization
                val processedData = parseProcessedData(recordingData.processedData)

                // Update visualization with data
                ecgVisualization.setEcgData(processedData)

                // Store recording for later use
                recording = recordingData

            } catch (e: Exception) {

            }
        }
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
    private fun parseProcessedData(processedData: String): Map<Int, List<Float>> {
        // Parse the processed data string into a map of lead index to data points
        // Format described as: "lead1:0.0,0.0,0.0,0...lead2:0.0,0.0..."
        val result = mutableMapOf<Int, List<Float>>()

        // Split by lead
        val leadStrings = processedData.split("lead")

        for (leadString in leadStrings) {
            if (leadString.isBlank()) continue

            val leadParts = leadString.trim().split(":", limit = 2)
            if (leadParts.size != 2) continue

            try {
                val leadIndex = leadParts[0].toInt()
                val dataPoints = leadParts[1].split(",").mapNotNull {
                    it.trim().toFloatOrNull()
                }
                result[leadIndex] = dataPoints
            } catch (e: Exception) {
                // Skip this lead if there's an error
                continue
            }
        }

        return result
    }
}