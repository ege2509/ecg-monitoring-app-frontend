package com.example.ens492frontend


import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.ens492frontend.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.min

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
        const val DEFAULT_LEAD_TO_DISPLAY = 1 // Default to Lead II (index 1)
        const val MAX_DATA_POINTS = 1000 // Maximum number of data points to display
        const val GRID_SIZE_MM = 5 // Standard ECG grid size in mm
        const val MM_TO_PIXEL_RATIO = 2.5f // Convert mm to pixels (approximate for common screens)
        const val PIXELS_PER_GRID = GRID_SIZE_MM * MM_TO_PIXEL_RATIO
        const val MILLIVOLTS_PER_GRID = 0.5f // Standard ECG calibration (0.5mV per 5mm)
        const val DEFAULT_BPM = 70 // Default heart rate
        const val ABNORMALITY_THRESHOLD = 0.7f // Threshold for highlighting abnormalities
    }

    // Drawing properties
    private val backgroundPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    // Update the grid paints to make minor grid lines much more subtle
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#FFCCD5") // Much lighter pink for minor grid lines
        strokeWidth = 0.5f // Thinner lines
        style = Paint.Style.STROKE
    }

    private val majorGridPaint = Paint().apply {
        color = Color.parseColor("#FC6C85") // Keep the stronger pink for major grid lines
        strokeWidth = 1.5f // Slightly thinner than before
        style = Paint.Style.STROKE
    }

    private val ecgPaint = Paint().apply {
        color = Color.parseColor("#32CD32") // ECG green
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val abnormalEcgPaint = Paint().apply {
        color = Color.parseColor("#FF4500") // Orange-red for abnormal sections
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val statsPaint = Paint().apply {
        color = Color.BLACK
        textSize = 40f
        isAntiAlias = true
    }

    private val leadLabelPaint = Paint().apply {
        color = Color.BLACK
        textSize = 32f
        isAntiAlias = true
    }

    private val abnormalityLabelPaint = Paint().apply {
        color = Color.parseColor("#FF4500")
        textSize = 30f
        isAntiAlias = true
    }

    // ECG data
    private val dataPoints = ArrayList<Float>(MAX_DATA_POINTS)
    private var ecgPath = Path()
    private var displayLeadIndex = DEFAULT_LEAD_TO_DISPLAY
    private var heartRate = DEFAULT_BPM
    private var pixelsPerDataPoint = 2f // Will be calculated based on view size
    private var verticalScale = 1f // Will be calculated based on view size
    private var abnormalities = mapOf<String, Float>()
    private var abnormalRanges = mutableListOf<IntRange>() // Ranges of data points to highlight

    // Job for data collection
    private var dataCollectionJob: Job? = null

    // WebSocket connection status
    private var isConnected = false

    init {
        // Read custom attributes if any
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.EcgVisualizationView)
            displayLeadIndex = typedArray.getInt(R.styleable.EcgVisualizationView_displayLeadIndex, DEFAULT_LEAD_TO_DISPLAY)
            typedArray.recycle()
        }
    }

    /**
     * Connect to the WebSocket to start receiving ECG data
     */
    fun connectToEcgService(scope: CoroutineScope, webSocketService: WebSocketService) {
        // Disconnect any existing connection
        disconnectFromEcgService()

        dataCollectionJob = scope.launch(Dispatchers.Main) {
            webSocketService.ecgDataFlow.collectLatest { jsonString ->
                processEcgData(jsonString)
                invalidate() // Trigger redraw with new data
            }
        }

        isConnected = true
    }

    /**
     * Disconnect from the ECG service
     */
    fun disconnectFromEcgService() {
        dataCollectionJob?.cancel()
        dataCollectionJob = null
        isConnected = false
    }

    /**
     * Process incoming ECG JSON data from WebSocket
     */
    private fun processEcgData(jsonString: String) {
        try {
            val jsonObject = JSONObject(jsonString)

            // Extract heart rate
            heartRate = jsonObject.optInt("heartRate", DEFAULT_BPM)

            // Extract lead data from the multi-lead structure
            val leadsArray = jsonObject.getJSONArray("leads")
            for (i in 0 until leadsArray.length()) {
                val leadObj = leadsArray.getJSONObject(i)
                val leadIndex = leadObj.getInt("lead") - 1 // Convert from 1-based to 0-based

                // Only process the lead we're displaying
                if (leadIndex == displayLeadIndex) {
                    val leadDataArray = leadObj.getJSONArray("data")

                    // Process and add new data points
                    for (j in 0 until leadDataArray.length()) {
                        val value = leadDataArray.getDouble(j).toFloat()
                        addDataPoint(value)
                    }

                    // Once we have data for our lead, we can stop processing
                    break
                }
            }

            // Extract abnormalities
            val abnormalitiesObj = jsonObject.getJSONObject("abnormalities")
            val newAbnormalities = mutableMapOf<String, Float>()

            abnormalitiesObj.keys().forEach { key ->
                val probability = abnormalitiesObj.getDouble(key).toFloat()
                newAbnormalities[key] = probability
            }

            // Update abnormalities and mark regions for highlighting
            abnormalities = newAbnormalities
            updateAbnormalRanges()

        } catch (e: Exception) {
            // Log error but don't crash
            e.printStackTrace()
        }
    }

    /**
     * Add a new data point, maintaining maximum size
     */
    private fun addDataPoint(value: Float) {
        if (dataPoints.size >= MAX_DATA_POINTS) {
            dataPoints.removeAt(0)
        }
        dataPoints.add(value)
        updateEcgPath()
    }

    /**
     * Update abnormal ranges based on current abnormalities
     */
    private fun updateAbnormalRanges() {
        abnormalRanges.clear()

        // If any abnormality exceeds threshold, mark the last 25% of data as abnormal
        if (abnormalities.any { it.value >= ABNORMALITY_THRESHOLD }) {
            val startIndex = (dataPoints.size * 0.75).toInt()
            if (startIndex < dataPoints.size) {
                abnormalRanges.add(startIndex until dataPoints.size)
            }
        }
    }

    /**
     * Update the path used to draw the ECG line
     */
    private fun updateEcgPath() {
        if (dataPoints.isEmpty()) return

        ecgPath.reset()

        // Calculate center line (0mV reference)
        val centerY = height / 2f

        // Draw the ECG line
        var startX = width - (dataPoints.size * pixelsPerDataPoint)

        ecgPath.moveTo(startX, centerY - (dataPoints[0] * verticalScale))

        for (i in 1 until dataPoints.size) {
            val x = startX + (i * pixelsPerDataPoint)
            val y = centerY - (dataPoints[i] * verticalScale)
            ecgPath.lineTo(x, y)
        }
    }

    /**
     * Set which lead to display (0-based index)
     */
    fun setLeadToDisplay(leadIndex: Int) {
        if (leadIndex != displayLeadIndex) {
            displayLeadIndex = leadIndex
            // Clear existing data as we're switching leads
            dataPoints.clear()
            abnormalRanges.clear()
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Calculate horizontal scale - fit all data points in view width
        pixelsPerDataPoint = w.toFloat() / MAX_DATA_POINTS

        // Calculate vertical scale - 1mV should be 10mm (2 grid squares) on screen
        // This maintains the standard ECG scaling of 10mm/mV
        verticalScale = (PIXELS_PER_GRID * 2) / MILLIVOLTS_PER_GRID

        updateEcgPath()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Draw grid
        drawGrid(canvas)

        // Draw normal ECG segments
        canvas.drawPath(ecgPath, ecgPaint)

        // Draw abnormal segments with different color
        drawAbnormalSegments(canvas)

        // Draw stats and labels
        drawStatsAndLabels(canvas)
    }

    /**
     * Draw the ECG grid background
     */
    // Update the drawGrid method for better grid rendering
    private fun drawGrid(canvas: Canvas) {
        // Draw minor grid lines (1mm spacing)
        val minorGridSize = PIXELS_PER_GRID / 5 // 1mm

        // Use alpha to make minor grid lines even more subtle
        gridPaint.alpha = 100

        // Vertical minor grid lines
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += minorGridSize
        }

        // Horizontal minor grid lines
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += minorGridSize
        }

        // Draw major grid lines (5mm spacing) with full opacity
        majorGridPaint.alpha = 255

        // Vertical major grid lines
        x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), majorGridPaint)
            x += PIXELS_PER_GRID
        }

        // Horizontal major grid lines
        y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, majorGridPaint)
            y += PIXELS_PER_GRID
        }
    }

    /**
     * Draw abnormal segments with a different color
     */
    private fun drawAbnormalSegments(canvas: Canvas) {
        if (abnormalRanges.isEmpty() || dataPoints.isEmpty()) return

        val centerY = height / 2f

        for (range in abnormalRanges) {
            val abnormalPath = Path()
            val startX = width - (dataPoints.size * pixelsPerDataPoint)

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
     * Draw heart rate and lead information
     */
    private fun drawStatsAndLabels(canvas: Canvas) {
        // Draw heart rate
        val bpmText = "♥ $heartRate BPM"
        canvas.drawText(bpmText, 20f, 60f, statsPaint)

        // Draw lead label
        val leadName = "Lead ${displayLeadIndex + 1}"
        canvas.drawText(leadName, 20f, 120f, leadLabelPaint)

        // Draw abnormality alerts if any exceed threshold
        var y = 180f
        abnormalities.forEach { (type, probability) ->
            if (probability >= ABNORMALITY_THRESHOLD) {
                val abnormalityText = "$type: ${(probability * 100).toInt()}%"
                canvas.drawText(abnormalityText, 20f, y, abnormalityLabelPaint)
                y += 40f
            }
        }

        // Draw connection status
        val connectionText = if (isConnected) "●" else "○"
        val connectionPaint = if (isConnected) {
            Paint().apply {
                color = Color.GREEN
                textSize = 40f
                isAntiAlias = true
            }
        } else {
            Paint().apply {
                color = Color.RED
                textSize = 40f
                isAntiAlias = true
            }
        }

        val connectionWidth = connectionPaint.measureText(connectionText)
        canvas.drawText(connectionText, width - connectionWidth - 20f, 60f, connectionPaint)
    }

    /**
     * Simulate ECG data for preview mode
     */
    fun simulateEcgData() {
        // Sample ECG data points representing one heartbeat
        val sampleEcgPattern = listOf(
            0.0f, 0.0f, 0.1f, 0.2f, 0.0f, -0.1f, -0.1f, 0.0f, 0.5f, 1.5f, 1.0f,
            -0.5f, -1.0f, -0.3f, 0.0f, 0.2f, 0.4f, 0.3f, 0.0f, -0.1f, 0.0f
        )

        // Clear existing data
        dataPoints.clear()

        // Fill with sample pattern repeated
        while (dataPoints.size < MAX_DATA_POINTS) {
            for (value in sampleEcgPattern) {
                if (dataPoints.size < MAX_DATA_POINTS) {
                    addDataPoint(value)
                } else {
                    break
                }
            }
        }

        // Simulate abnormal section for preview
        abnormalRanges.add((MAX_DATA_POINTS - 100) until MAX_DATA_POINTS)

        // Set heart rate
        heartRate = 75

        // Set sample abnormalities
        abnormalities = mapOf(
            "RBBB" to 0.85f,
            "AF" to 0.3f
        )

        updateEcgPath()
        invalidate()
    }
}