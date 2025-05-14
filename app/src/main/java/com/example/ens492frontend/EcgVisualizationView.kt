package com.example.ens492frontend

import com.example.ens492frontend.WebSocketService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Custom view for displaying real-time ECG data with multi-lead support and abnormality highlighting
 */
class EcgVisualizationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Constants
    companion object {
        private const val TAG = "EcgVisualizationView"
        const val MAX_DATA_POINTS = 500
        const val GRID_SIZE_MM = 5
        const val MM_TO_PIXEL_RATIO = 6.0f
        const val PIXELS_PER_GRID = GRID_SIZE_MM * MM_TO_PIXEL_RATIO
        const val MILLIVOLTS_PER_GRID = 0.5f
        const val DEFAULT_BPM = 70
        const val DEFAULT_LEAD_INDEX = 0 // Starting with lead I (0-based index)
        const val NUM_LEADS = 12
        const val ABNORMALITY_THRESHOLD = 0.7f
    }

    object EcgLeads {
        // Standard 12-lead ECG names
        const val LEAD_I = 0
        const val LEAD_II = 1
        const val LEAD_III = 2
        const val LEAD_AVR = 3
        const val LEAD_AVL = 4
        const val LEAD_AVF = 5
        const val LEAD_V1 = 6
        const val LEAD_V2 = 7
        const val LEAD_V3 = 8
        const val LEAD_V4 = 9
        const val LEAD_V5 = 10
        const val LEAD_V6 = 11

        // Map of indices to human-readable names
        val leadNames = mapOf(
            LEAD_I to "Lead I",
            LEAD_II to "Lead II",
            LEAD_III to "Lead III",
            LEAD_AVR to "aVR",
            LEAD_AVL to "aVL",
            LEAD_AVF to "aVF",
            LEAD_V1 to "V1",
            LEAD_V2 to "V2",
            LEAD_V3 to "V3",
            LEAD_V4 to "V4",
            LEAD_V5 to "V5",
            LEAD_V6 to "V6"
        )
    }

    // Data model
    private val dataPoints = ArrayList<Float>(MAX_DATA_POINTS)
    private var currentLeadIndex = DEFAULT_LEAD_INDEX
    private var heartRate = DEFAULT_BPM
    private var isConnected = false

    // Abnormality detection
    private data class Abnormality(val type: String, val probability: Float)
    private val abnormalities = mutableListOf<Abnormality>()
    private val abnormalRanges = mutableListOf<IntRange>()

    // Grid configuration
    private var minorGridDivisions = 5

    // Rendering properties
    private var pixelsPerDataPoint = 2f
    private var verticalScale = 1f
    private val ecgPath = Path()

    // WebSocket collection job
    private var dataCollectionJob: Job? = null

    // Paints
    private val backgroundPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#fa87af") // Light pink
        strokeWidth = 0.5f
        style = Paint.Style.STROKE
        alpha = 200
    }

    private val majorGridPaint = Paint().apply {
        color = Color.parseColor("#ed5187") // Stronger pink
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        alpha = 200
    }

    private val ecgPaint = Paint().apply {
        color = Color.BLACK// ECG Black
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val abnormalEcgPaint = Paint().apply {
        color = Color.parseColor("#FF4500") // Orange-red for abnormal sections
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 40f
        isAntiAlias = true
    }

    init {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.EcgVisualizationView)
            currentLeadIndex = typedArray.getInt(R.styleable.EcgVisualizationView_displayLeadIndex, DEFAULT_LEAD_INDEX)
            typedArray.recycle()
        }
    }

    /**
     * Connect to the WebSocket service to receive ECG data
     */
    fun connectToEcgService(scope: CoroutineScope, webSocketService: WebSocketService) {
        isConnected = true
        dataCollectionJob?.cancel()

        dataCollectionJob = scope.launch {
            try {
                Log.d(TAG, "Starting to collect ECG data")

                // Make sure we clear and initialize data
                dataPoints.clear()

                webSocketService.ecgDataFlow.collectLatest { leadDataMap ->
                    // Process the data for the current lead
                    processEcgData(leadDataMap)

                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        updateEcgPath()
                        invalidate()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting ECG data", e)
            }
        }

        invalidate()
    }

    /**
     * Disconnect from the ECG service
     */
    fun disconnectFromEcgService() {
        dataCollectionJob?.cancel()
        dataCollectionJob = null
        isConnected = false
        invalidate()
    }

    /**
     * Process the incoming ECG data map
     */
    private fun processEcgData(leadDataMap: Map<Int, FloatArray>) {
        // Check if we have data for the selected lead
        if (leadDataMap.containsKey(currentLeadIndex)) {
            val leadName = EcgLeads.leadNames[currentLeadIndex] ?: "Unknown Lead"
            val newDataPoints = leadDataMap[currentLeadIndex]!!

            // Log reception with clear lead name
            Log.d(TAG, "Received ${newDataPoints.size} data points for $leadName (index: $currentLeadIndex)")

            // Add new data points
            synchronized(dataPoints) {
                for (value in newDataPoints) {
                    addDataPoint(value)
                }
            }

            // The rest of your implementation remains the same...
            // Heart rate simulation and abnormality detection
        } else {
            val expectedLeadName = EcgLeads.leadNames[currentLeadIndex] ?: "Unknown Lead"
            Log.w(TAG, "No data received for $expectedLeadName (index: $currentLeadIndex)")

            // Log available leads for debugging
            val availableLeads = leadDataMap.keys.joinToString(", ") { key ->
                "${key} (${EcgLeads.leadNames[key] ?: "Unknown"})"
            }
            Log.d(TAG, "Available leads: $availableLeads")
        }
    }

    /**
     * Add a data point to the buffer, maintaining maximum size
     */
    private fun addDataPoint(value: Float) {
        if (dataPoints.size >= MAX_DATA_POINTS) {
            dataPoints.removeAt(0)
        }
        dataPoints.add(value)
    }

    /**
     * Update the path used for drawing the ECG line
     */
    private fun updateEcgPath() {
        if (dataPoints.isEmpty()) {
            Log.d(TAG, "No data points to draw")
            return
        }

        if (width <= 0 || height <= 0) {
            Log.d(TAG, "View dimensions not ready: width=$width, height=$height")
            return
        }

        Log.d(TAG, "Updating ECG path with ${dataPoints.size} data points")

        ecgPath.reset()

        // Calculate center line (0mV reference)
        val centerY = height / 2f

        // Calculate horizontal scaling - ensure we have non-zero value
        pixelsPerDataPoint = if (MAX_DATA_POINTS > 0) {
            width.toFloat() / MAX_DATA_POINTS
        } else {
            2f // Default fallback
        }

        // Calculate vertical scaling - standard is 10mm/mV
        verticalScale = (PIXELS_PER_GRID * 2) / MILLIVOLTS_PER_GRID

        // Calculate starting X position
        val startX = if (dataPoints.size >= MAX_DATA_POINTS) {
            0f // Start from left edge if we have max points
        } else {
            (dataPoints.size * pixelsPerDataPoint) // Start from left-aligned position
        }

        // Create the path - ensure we have valid data points
        if (dataPoints.isNotEmpty()) {
            ecgPath.moveTo(startX, centerY - (dataPoints[0] * verticalScale))
            for (i in 1 until dataPoints.size) {
                val x = startX + (i * pixelsPerDataPoint)
                val y = centerY - (dataPoints[i] * verticalScale)
                ecgPath.lineTo(x, y)
            }
            Log.d(TAG, "ECG path created successfully")
        }
    }


    /**
     * Set which lead to display (0-based index)
     */
    fun setLeadToDisplay(leadIndex: Int) {
        if (leadIndex in 0 until NUM_LEADS && leadIndex != currentLeadIndex) {
            currentLeadIndex = leadIndex

            // Clear existing data as we're switching leads
            dataPoints.clear()
            abnormalRanges.clear()
            abnormalities.clear()

            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Recalculate scaling when size changes
        pixelsPerDataPoint = w.toFloat() / MAX_DATA_POINTS
        verticalScale = (PIXELS_PER_GRID * 2) / MILLIVOLTS_PER_GRID

        updateEcgPath()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background grid
        drawGrid(canvas)

        // Draw ECG data if available
        if (dataPoints.isNotEmpty()) {
            // Draw normal ECG path
            canvas.drawPath(ecgPath, ecgPaint)

            // Draw abnormal segments with different color
            drawAbnormalSegments(canvas)
        }

        // Draw stats and labels
        drawStatsAndLabels(canvas)
    }

    /**
     * Draw the ECG grid background
     */
    private fun drawGrid(canvas: Canvas) {
        // Fill background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Get display density
        val density = context.resources.displayMetrics.density
        val majorGridSpacing = (PIXELS_PER_GRID * density).toFloat()

        // Actually use the minorGridDivisions variable
        val minorGridSpacing = majorGridSpacing / minorGridDivisions

        // Increase visibility of minor grid lines
        val minorGridPaint = Paint(gridPaint).apply {
            alpha = 150  // Slightly more visible
            strokeWidth = 0.7f * density  // Scale with device density
        }

        // Draw minor grid lines first
        // Minor vertical lines
        var minorX = 0f
        while (minorX < width) {
            canvas.drawLine(minorX, 0f, minorX, height.toFloat(), minorGridPaint)
            minorX += minorGridSpacing
        }

        // Minor horizontal lines
        var minorY = 0f
        while (minorY < height) {
            canvas.drawLine(0f, minorY, width.toFloat(), minorY, minorGridPaint)
            minorY += minorGridSpacing
        }

        // Draw major grid lines
        // Vertical
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), majorGridPaint)
            x += majorGridSpacing
        }

        // Horizontal
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, majorGridPaint)
            y += majorGridSpacing
        }
    }

    /**
     * Draw abnormal segments with highlight color
     */
    private fun drawAbnormalSegments(canvas: Canvas) {
        if (abnormalRanges.isEmpty() || dataPoints.isEmpty()) return

        val centerY = height / 2f

        for (range in abnormalRanges) {
            val abnormalPath = Path()

            // Calculate starting X position
            val startX = if (dataPoints.size >= MAX_DATA_POINTS) {
                width - (dataPoints.size * pixelsPerDataPoint)
            } else {
                0f
            }

            // Create path for the abnormal segment
            abnormalPath.moveTo(
                startX + (range.first * pixelsPerDataPoint),
                centerY - (dataPoints[range.first] * verticalScale)
            )

            for (i in range.first + 1..range.last) {
                if (i < dataPoints.size) {
                    val x = startX + (i * pixelsPerDataPoint)
                    val y = centerY - (dataPoints[i] * verticalScale)
                    abnormalPath.lineTo(x, y)
                }
            }

            canvas.drawPath(abnormalPath, abnormalEcgPaint)
        }
    }

    /**
     * Draw information text (heart rate, lead, abnormalities)
     */
    private fun drawStatsAndLabels(canvas: Canvas) {
        // Draw heart rate
        val bpmText = "♥ $heartRate BPM"
        canvas.drawText(bpmText, 20f, 60f, textPaint)

        // Draw lead label (convert from 0-based to 1-based for display)
        val leadName = "Lead ${currentLeadIndex + 1}"
        canvas.drawText(leadName, 20f, 120f, textPaint)

        // Draw abnormality alerts
        var y = 180f
        for (abnormality in abnormalities) {
            if (abnormality.probability >= ABNORMALITY_THRESHOLD) {
                val text = "${abnormality.type}: ${(abnormality.probability * 100).toInt()}%"

                val alertPaint = Paint(textPaint).apply {
                    color = Color.parseColor("#FF4500")
                    textSize = 30f
                }

                canvas.drawText(text, 20f, y, alertPaint)
                y += 40f
            }
        }

        // Draw connection status indicator
        val connectionText = if (isConnected) "● CONNECTED" else "○ DISCONNECTED"
        val connectionPaint = Paint(textPaint).apply {
            color = if (isConnected) Color.GREEN else Color.RED
            textSize = 30f
        }

        canvas.drawText(connectionText, width - 250f, 60f, connectionPaint)
    }


    /**
     * Simulate ECG data for preview mode
     */
    fun simulateEcgData() {
        // Sample ECG data points representing one heartbeat
        val sampleEcgPattern = listOf(
            0.0f, 0.0f, 0.1f, 0.2f, 0.0f, -0.1f, -0.1f, 0.0f, 0.5f, 2.0f, 1.5f,
            -0.8f, -1.5f, -0.5f, 0.0f, 0.4f, 0.8f, 0.5f, 0.0f, -0.1f, 0.0f
        )

        // Clear existing data
        dataPoints.clear()

        // Fill with sample pattern repeated
        val repeats = MAX_DATA_POINTS / sampleEcgPattern.size + 1
        for (i in 0 until repeats) {
            for (value in sampleEcgPattern) {
                if (dataPoints.size < MAX_DATA_POINTS) {
                    dataPoints.add(value)
                } else {
                    break
                }
            }
        }

        // Simulate abnormal section
        abnormalRanges.clear()
        abnormalRanges.add((MAX_DATA_POINTS - 100) until MAX_DATA_POINTS)

        // Set heart rate and sample abnormality
        heartRate = 75
        abnormalities.clear()
        abnormalities.add(Abnormality("RBBB", 0.85f))

        // Update visual elements
        updateEcgPath()
        invalidate()
    }
}