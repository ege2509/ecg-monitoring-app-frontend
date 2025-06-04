package com.example.ens492frontend

import android.app.ProgressDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import androidx.core.view.GestureDetectorCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedList
import kotlin.math.max
import kotlin.math.min

/**
 * Custom view for displaying real-time ECG data with multi-lead support, abnormality highlighting,
 * and scrolling/zooming capabilities to review past recordings
 */
class EcgVisualizationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Constants
    companion object {
        private const val TAG = "EcgVisualizationView"
        const val MAX_VISIBLE_DATA_POINTS = 1000       // Number of points visible at once
        const val MAX_TOTAL_DATA_POINTS = 10000       // Total history to maintain
        const val GRID_SIZE_MM = 5
        const val MM_TO_PIXEL_RATIO = 6.0f
        const val PIXELS_PER_GRID = GRID_SIZE_MM * MM_TO_PIXEL_RATIO
        const val MILLIVOLTS_PER_GRID = 0.5f
        const val DEFAULT_BPM = 80
        const val DEFAULT_LEAD_INDEX = 0 // Starting with lead I (0-based index)
        const val NUM_LEADS = 12
        const val ABNORMALITY_THRESHOLD = 0.7f
        const val MIN_SCROLL_OFFSET = 0f              // Minimum scroll position
        const val REPLAY_FRAME_DELAY = 20L            // Milliseconds between replay frames
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

    // Data model - map of lead index to data points
    private val allLeadsDataPoints = mutableMapOf<Int, LinkedList<Float>>()

    private val heartRateData = mutableListOf<Int>() // To store heart rate values


    // Recording state
    private var isRecording = true             // Whether actively recording data
    private var currentLeadIndex = DEFAULT_LEAD_INDEX
    private var isConnected = false

    // Replay system state
    private var isReplaying = false            // Whether in replay mode
    private var replayPosition = 0             // Current position in replay
    private var replaySpeed = 1.0f             // Replay speed multiplier
    private var replayJob: Job? = null         // Coroutine job for replay animation

    // Recording session management
    private var hasRecordedData = false        // Whether we have recorded any data in the session
    private var recordingSessionId = ""        // ID for the current recording session (for saving)

    // Scrolling and zoom state
    private var scrollOffset = 0f              // Current horizontal scroll position
    private var maxScrollOffset = 0f           // Maximum scroll offset based on data size
    private var zoomLevel = 1.0f               // Zoom factor (1.0 = default view)
    private var visibleDataPoints = MAX_VISIBLE_DATA_POINTS

    // Gesture detection
    private val gestureDetector: GestureDetectorCompat
    private val scaleGestureDetector: ScaleGestureDetector

    private var heartRate = 0

    // Abnormality detection (now per lead)
    private data class Abnormality(val type: String, val probability: Float)
    private val abnormalitiesByLead = mutableMapOf<Int, MutableList<Abnormality>>()
    private val abnormalRangesByLead = mutableMapOf<Int, MutableList<IntRange>>()

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
        color = Color.BLACK
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = false
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

    private val controlsPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 30f
        isAntiAlias = true
    }

    private val buttonPaint = Paint().apply {
        color = Color.parseColor("#303030")
        style = Paint.Style.FILL
    }

    private val buttonTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // UI controls
    private val clearButton = RectF()

    init {

        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.EcgVisualizationView)
            currentLeadIndex = typedArray.getInt(R.styleable.EcgVisualizationView_displayLeadIndex, DEFAULT_LEAD_INDEX)
            typedArray.recycle()
        }

        // Initialize data storage for all leads
        for (i in 0 until NUM_LEADS) {
            allLeadsDataPoints[i] = LinkedList()
            abnormalitiesByLead[i] = mutableListOf()
            abnormalRangesByLead[i] = mutableListOf()
        }

        // Initialize gesture detectors
        gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                // Only allow scrolling when not recording or when we have enough data
                if ((!isRecording || hasRecordedData) && !isReplaying) {
                    // Update scroll offset (negative distanceX to make it feel natural)
                    scrollOffset = (scrollOffset - distanceX).coerceIn(MIN_SCROLL_OFFSET, maxScrollOffset)
                    updateEcgPath()
                    invalidate()
                    return true
                }
                return false
            }



            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Reset zoom and scroll position on double tap
                zoomLevel = 1.0f
                scrollOffset = 0f
                visibleDataPoints = MAX_VISIBLE_DATA_POINTS
                updateEcgPath()
                invalidate()
                return true
            }

        })

        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // Only allow zooming when not recording or when we have enough data
                if ((!isRecording || hasRecordedData) && !isReplaying) {
                    // Update zoom level
                    zoomLevel *= detector.scaleFactor
                    // Constrain zoom level to reasonable bounds
                    zoomLevel = zoomLevel.coerceIn(0.5f, 5.0f)

                    // Adjust visible data points based on zoom
                    visibleDataPoints = (MAX_VISIBLE_DATA_POINTS / zoomLevel).toInt().coerceIn(100, MAX_VISIBLE_DATA_POINTS * 2)

                    // Recalculate max scroll offset based on new zoom level
                    updateMaxScrollOffset()

                    // Ensure scroll offset is still valid
                    scrollOffset = scrollOffset.coerceIn(MIN_SCROLL_OFFSET, maxScrollOffset)

                    updateEcgPath()
                    invalidate()
                    return true
                }
                return false
            }
        })

        // Initialize a unique recording session ID
        recordingSessionId = "recording_" + System.currentTimeMillis()
    }

    private fun updateMaxScrollOffset() {
        // Calculate max scrollable distance based on data size and zoom level
        val currentData = getCurrentLeadData()
        maxScrollOffset = max(0f, (currentData.size - visibleDataPoints) * pixelsPerDataPoint)
    }

    private fun getCurrentLeadData(): List<Float> {
        return allLeadsDataPoints[currentLeadIndex]?.toList() ?: emptyList()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // If we're in replay mode, stop replay on any touch
        if (isReplaying && event.action == MotionEvent.ACTION_DOWN) {
            stopReplay()
            return true
        }

        // If we're still recording with minimal data, don't handle touch events
        if (isRecording && !hasRecordedData) {
            return false
        }

        var handled = scaleGestureDetector.onTouchEvent(event)
        if (!scaleGestureDetector.isInProgress) {
            handled = gestureDetector.onTouchEvent(event) || handled
        }
        return handled || super.onTouchEvent(event)
    }

    /**
     * Connect to the WebSocket service to receive ECG data
     */
    fun connectToEcgService(scope: CoroutineScope, webSocketService: WebSocketService) {
        isConnected = true
        isRecording = true
        hasRecordedData = false
        dataCollectionJob?.cancel()

        dataCollectionJob = scope.launch {
            try {
                Log.d(TAG, "Starting to collect ECG data")

                // Reset the recording session
                recordingSessionId = "recording_" + System.currentTimeMillis()

                // Clear previous data if starting a new recording session
                for (i in 0 until NUM_LEADS) {
                    allLeadsDataPoints[i]?.clear()
                    abnormalitiesByLead[i]?.clear()
                    abnormalRangesByLead[i]?.clear()
                }

                scrollOffset = 0f

                // Launch ECG data collection in separate coroutine
                launch {
                    webSocketService.ecgDataFlow.collectLatest { leadDataMap ->
                        // Process the data for ALL leads
                        processAllEcgData(leadDataMap)

                        // Mark that we have recorded data in this session
                        if (leadDataMap.isNotEmpty()) {
                            hasRecordedData = true
                        }

                        // Update UI on main thread
                        withContext(Dispatchers.Main) {
                            updateMaxScrollOffset()
                            updateEcgPath()
                            invalidate()
                        }
                    }
                }

                // Launch heart rate collection in separate coroutine
                launch {
                    Log.d(TAG, "Starting heart rate collection...")
                    webSocketService.heartRateFlow.collectLatest { heartRate ->
                        Log.d(TAG, "Received heart rate from WebSocket: $heartRate")
                        withContext(Dispatchers.Main) {
                            Log.d(TAG, "Updating heart rate on main thread: $heartRate")
                            updateHeartRate(heartRate)
                        }
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
        isRecording = false // No longer recording, allow scrolling
        invalidate()
    }

    /**
     * Process the incoming ECG data for all leads
     */
    private fun processAllEcgData(leadDataMap: Map<Int, FloatArray>) {

        // Process all leads in the data map
        leadDataMap.forEach { (leadIndex, dataPoints) ->
            if (leadIndex in 0 until NUM_LEADS) {
                val leadName = EcgLeads.leadNames[leadIndex] ?: "Unknown Lead"
                Log.d(TAG, "Received ${dataPoints.size} data points for $leadName (index: $leadIndex)")

                // Add data points for this lead
                synchronized(allLeadsDataPoints) {
                    val leadData = allLeadsDataPoints[leadIndex] ?: LinkedList<Float>().also {
                        allLeadsDataPoints[leadIndex] = it
                    }

                    for (value in dataPoints) {
                        // Keep history within limits
                        if (leadData.size >= MAX_TOTAL_DATA_POINTS) {
                            leadData.removeFirst()
                        }
                        leadData.add(value)
                    }
                }
            }
        }

        // If recording the current lead, auto-scroll to see newest data
        if (isRecording) {
            scrollOffset = maxScrollOffset
        }
    }

    /**
     * Toggle recording mode
     */
    fun toggleRecording() {
        isRecording = !isRecording

        // If resuming recording, scroll to latest data
        if (isRecording) {
            scrollOffset = maxScrollOffset
        }

        invalidate()
    }

    /**
     * Update the path used for drawing the ECG line
     */
    private fun updateEcgPath() {
        val currentData = getCurrentLeadData()

        if (currentData.isEmpty()) {
            Log.d(TAG, "No data points to draw for lead $currentLeadIndex")
            return
        }

        if (width <= 0 || height <= 0) {
            Log.d(TAG, "View dimensions not ready: width=$width, height=$height")
            return
        }

        Log.d(TAG, "Updating ECG path with ${currentData.size} data points for lead $currentLeadIndex")

        ecgPath.reset()

        // Calculate center line (0mV reference)
        val centerY = height / 2f

        // Calculate horizontal scaling based on zoom level
        pixelsPerDataPoint = width.toFloat() / visibleDataPoints

        // Calculate vertical scaling - standard is 10mm/mV
        verticalScale = (PIXELS_PER_GRID * 2) / MILLIVOLTS_PER_GRID

        // Determine which portion of data to display based on scroll offset
        val startIndex = if ((isRecording || isReplaying) && currentData.size <= visibleDataPoints) {
            // When recording with not enough data to fill the view
            0
        } else {
            // Convert scroll position to data index
            min((scrollOffset / pixelsPerDataPoint).toInt(), max(0, currentData.size - visibleDataPoints))
        }

        // Calculate end index ensuring we don't go out of bounds
        val endIndex = min(startIndex + visibleDataPoints, currentData.size)

        // Create the path if we have data to display
        if (startIndex < endIndex) {
            val dataList = currentData.subList(startIndex, endIndex)

            ecgPath.moveTo(0f, centerY - (dataList[0] * verticalScale))

            for (i in 1 until dataList.size) {
                val x = i * pixelsPerDataPoint
                val y = centerY - (dataList[i] * verticalScale)
                ecgPath.lineTo(x, y)
            }

            Log.d(TAG, "ECG path created successfully showing points $startIndex to $endIndex")
        }
    }

    /**
     * Set which lead to display (0-based index)
     */
    fun setLeadToDisplay(leadIndex: Int) {
        if (leadIndex in 0 until NUM_LEADS && leadIndex != currentLeadIndex) {
            currentLeadIndex = leadIndex

            // Keep existing data but update the path for the new lead
            updateMaxScrollOffset()
            updateEcgPath()

            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Recalculate scaling when size changes
        pixelsPerDataPoint = w.toFloat() / visibleDataPoints
        verticalScale = (PIXELS_PER_GRID * 2) / MILLIVOLTS_PER_GRID

        // Update max scroll offset based on new dimensions
        updateMaxScrollOffset()

        // Initialize button positions
        val buttonWidth = 180f
        val buttonHeight = 60f
        val buttonPadding = 10f



        clearButton.set(
            w - buttonWidth * 2 - buttonPadding * 2,
            h - buttonHeight - buttonPadding,
            w - buttonWidth - buttonPadding * 2,
            h - buttonPadding
        )

        updateEcgPath()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background grid
        drawGrid(canvas)

        // Draw ECG data if available
        val currentData = getCurrentLeadData()
        if (currentData.isNotEmpty()) {
            // Draw normal ECG path
            canvas.drawPath(ecgPath, ecgPaint)

            // Draw abnormal segments with different color
            drawAbnormalSegments(canvas)
        }

        // Draw stats and labels
        drawStatsAndLabels(canvas)

        // Draw mode indicator (recording/replay/review)
        drawModeIndicator(canvas)

        // Draw control buttons
        drawControlButtons(canvas)
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

        // Calculate grid offset for scrolling effect
        val gridOffsetX = (scrollOffset % majorGridSpacing)

        // Draw minor grid lines first
        // Minor vertical lines
        var minorX = -gridOffsetX
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
        var x = -gridOffsetX
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
        val abnormalRanges = abnormalRangesByLead[currentLeadIndex] ?: return
        val currentData = getCurrentLeadData()

        if (abnormalRanges.isEmpty() || currentData.isEmpty()) return

        val centerY = height / 2f

        // Determine which portion of data to display based on scroll offset
        val startIndex = min((scrollOffset / pixelsPerDataPoint).toInt(),
            max(0, currentData.size - visibleDataPoints))
        val endIndex = min(startIndex + visibleDataPoints, currentData.size)

        for (range in abnormalRanges) {
            // Skip ranges that aren't in the visible section
            if (range.last < startIndex || range.first >= endIndex) continue

            // Adjust range to visible portion
            val visibleStart = max(range.first, startIndex) - startIndex
            val visibleEnd = min(range.last, endIndex - 1) - startIndex

            val abnormalPath = Path()

            // Create path for the abnormal segment
            val dataList = currentData.subList(startIndex, endIndex)

            abnormalPath.moveTo(
                visibleStart * pixelsPerDataPoint,
                centerY - (dataList[visibleStart] * verticalScale)
            )

            for (i in visibleStart + 1..visibleEnd) {
                val x = i * pixelsPerDataPoint
                val y = centerY - (dataList[i] * verticalScale)
                abnormalPath.lineTo(x, y)
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

        // Draw lead label
        val leadName = EcgLeads.leadNames[currentLeadIndex] ?: "Unknown Lead"
        canvas.drawText(leadName, 20f, 120f, textPaint)

        // Draw abnormality alerts for the current lead
        val abnormalities = abnormalitiesByLead[currentLeadIndex] ?: emptyList()
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
     * Draw the recording/replay/review mode indicator
     */
    private fun drawModeIndicator(canvas: Canvas) {
        val modeText = when {
            isReplaying -> "REPLAY MODE"
            isRecording -> "RECORDING"
            else -> "REVIEW MODE"
        }
        val modeColor = when {
            isReplaying -> Color.BLUE
            isRecording -> Color.RED
            else -> Color.GREEN
        }

        val modePaint = Paint(textPaint).apply {
            color = modeColor
            textSize = 30f
        }

        // Draw at the bottom of the screen
        canvas.drawText(modeText, 20f, height - 90f, modePaint)

        // If in review mode and we have enough data, show scroll position
        if (!isRecording && !isReplaying && getCurrentLeadData().size > visibleDataPoints) {
            val percentScrolled = (scrollOffset / maxScrollOffset * 100).toInt().coerceIn(0, 100)
            val scrollText = "Position: $percentScrolled%"
            canvas.drawText(scrollText, 20f, height - 30f, controlsPaint)
        }

        // If replaying, show replay progress
        if (isReplaying) {
            val currentData = getCurrentLeadData()
            val progress = if (currentData.isNotEmpty()) {
                (replayPosition.toFloat() / currentData.size * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
            val replayText = "Replay: $progress% (${replaySpeed}x)"
            canvas.drawText(replayText, 20f, height - 30f, controlsPaint)
        }
    }

    /**
     * Draw control buttons (Save, Clear, Replay)
     */
    private fun drawControlButtons(canvas: Canvas) {
        // Don't show buttons during active recording
        if (isRecording && !hasRecordedData) return

        // Clear button
        canvas.drawRoundRect(clearButton, 10f, 10f, buttonPaint)
        canvas.drawText(
            "CLEAR",
            clearButton.centerX(),
            clearButton.centerY() + buttonTextPaint.textSize / 3,
            buttonTextPaint
        )

    }

    /**
     * Jump to the beginning of the recording
     */
    fun scrollToStart() {
        scrollOffset = 0f
        updateEcgPath()
        invalidate()
    }

    /**
     * Jump to the end of the recording
     */
    fun scrollToEnd() {
        scrollOffset = maxScrollOffset
        updateEcgPath()
        invalidate()
    }

    /**
     * Toggle replay mode - will animate through the recorded data
     */
    fun toggleReplay() {
        if (isReplaying) {
            stopReplay()
        } else {
            startReplay()
        }
    }

    /**
     * Start replaying the recorded data
     */
    private fun startReplay() {
        val currentData = getCurrentLeadData()
        if (currentData.isEmpty() || isRecording) {
            Log.d(TAG, "Cannot start replay: No data or still recording")
            return
        }

        isReplaying = true
        replayPosition = 0

        // Cancel any existing replay
        replayJob?.cancel()

        // Start replay animation
        replayJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                while (isReplaying && replayPosition < currentData.size) {
                    // Calculate visibleDataPoints to show
                    val endPos = min(replayPosition + visibleDataPoints, currentData.size)
                    val visibleCount = endPos - replayPosition

                    // Update the view on the main thread
                    withContext(Dispatchers.Main) {
                        // Only show data up to the current replay position
                        scrollOffset = 0f // Always start at beginning during replay
                        updateEcgPath()
                        invalidate()
                    }

                    // Advance the replay position
                    replayPosition += (replaySpeed * 5).toInt().coerceAtLeast(1)

                    // Add delay based on replay speed
                    delay((REPLAY_FRAME_DELAY / replaySpeed).toLong().coerceAtLeast(1))
                }

                // When replay reaches the end, stop
                withContext(Dispatchers.Main) {
                    isReplaying = false
                    invalidate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during replay", e)
                withContext(Dispatchers.Main) {
                    isReplaying = false
                    invalidate()
                }
            }
        }
    }

    /**
     * Stop the ongoing replay
     */
    private fun stopReplay() {
        isReplaying = false
        replayJob?.cancel()
        replayJob = null
        invalidate()
    }

    /**
     * Change replay speed
     */
    fun setReplaySpeed(speed: Float) {
        replaySpeed = speed.coerceIn(0.25f, 4.0f)
        invalidate()

    }

    /**
     * Clear the current recording data
     */
    private fun clearRecording() {

        if (!hasAnyData()) {
            Log.d(TAG, "No data to clear")
            return
        }

        // Stop replay if running
        if (isReplaying) {
            stopReplay()
        }

        // Clear all data for all leads
        for (i in 0 until NUM_LEADS) {
            allLeadsDataPoints[i]?.clear()
            abnormalitiesByLead[i]?.clear()
            abnormalRangesByLead[i]?.clear()
        }

        // Reset state
        hasRecordedData = false
        scrollOffset = 0f
        updateMaxScrollOffset()
        updateEcgPath()

        heartRateData.clear()

        // Generate a new recording session ID
        recordingSessionId = "recording_" + System.currentTimeMillis()

        // Provide visual feedback
        //showToast("Recording cleared")

        invalidate()
    }

    // Method to handle heart rate updates
    fun updateHeartRate(newHeartRate: Int) {
        this.heartRate = newHeartRate
        // Invalidate to refresh the display
        invalidate()
    }
    /**
     * Check if we have any data in any leads
     */
    fun hasAnyData(): Boolean {
        return allLeadsDataPoints.any { (_, data) -> data.isNotEmpty() }
    }

    /**
     * Simulate ECG data for preview/testing mode
     */
    fun simulateEcgData() {
        // Clear any existing data first
        clearRecording()

        // Generate simulated data for all leads with slightly different patterns
        for (leadIndex in 0 until NUM_LEADS) {
            // Base ECG pattern (one heartbeat)
            val basePattern = listOf(
                0.0f, 0.0f, 0.1f, 0.2f, 0.0f, -0.1f, -0.1f, 0.0f, 0.5f, 2.0f, 1.5f,
                -0.8f, -1.5f, -0.5f, 0.0f, 0.4f, 0.8f, 0.5f, 0.0f, -0.1f, 0.0f
            )

            // Modify the pattern slightly for each lead to simulate different views
            val leadPattern = basePattern.map { value ->
                // Apply a lead-specific modification
                val leadFactor = when (leadIndex) {
                    EcgLeads.LEAD_I -> 1.0f
                    EcgLeads.LEAD_II -> 1.2f
                    EcgLeads.LEAD_III -> 0.9f
                    EcgLeads.LEAD_AVR -> -0.5f  // Inverted
                    EcgLeads.LEAD_AVL -> 0.7f
                    EcgLeads.LEAD_AVF -> 0.8f
                    EcgLeads.LEAD_V1 -> 1.3f
                    EcgLeads.LEAD_V2 -> 1.5f
                    EcgLeads.LEAD_V3 -> 1.4f
                    EcgLeads.LEAD_V4 -> 1.2f
                    EcgLeads.LEAD_V5 -> 1.0f
                    EcgLeads.LEAD_V6 -> 0.8f
                    else -> 1.0f
                }

                value * leadFactor
            }

            // Generate data points by repeating the pattern
            val leadData = allLeadsDataPoints[leadIndex] ?: LinkedList<Float>().also {
                allLeadsDataPoints[leadIndex] = it
            }

            // Clear existing data for this lead
            leadData.clear()

            // Fill with simulated data (2-5 seconds worth)
            val repeats = MAX_TOTAL_DATA_POINTS / leadPattern.size + 1
            for (i in 0 until repeats) {
                for (value in leadPattern) {
                    if (leadData.size < MAX_TOTAL_DATA_POINTS) {
                        leadData.add(value)
                    } else {
                        break
                    }
                }
            }

            // Add some simulated abnormalities
            if (leadIndex == EcgLeads.LEAD_II || leadIndex == EcgLeads.LEAD_V1) {
                val abnormalRange = IntRange(
                    (MAX_TOTAL_DATA_POINTS / 2),
                    (MAX_TOTAL_DATA_POINTS / 2 + 200)
                )
                abnormalRangesByLead[leadIndex]?.add(abnormalRange)

                val abnormalityType = if (leadIndex == EcgLeads.LEAD_II) "RBBB" else "ST Elevation"
                abnormalitiesByLead[leadIndex]?.add(Abnormality(abnormalityType, 0.85f))
            }
        }

        // Mark that we have data
        hasRecordedData = true

        // Set heart rate for the simulation
        heartRate = 75

        // Set to review mode
        isRecording = false

        // Update the view for the current lead
        updateMaxScrollOffset()
        updateEcgPath()
        invalidate()
    }

}

/**
 * OPTIONAL: For real implementations, methods to save/load recordings from database
 * These would be implemented with your database solution (Room, etc.)
 */

/**
 * Load a specific recording from the database
 */
//fun loadRecording(recordingId: String, scope: CoroutineScope) {
// This would load a saved recording by ID from your database
// For implementation, you'd need:
// - A DAO method to query the recording
// - A model class to represent saved data
// - Logic to convert from saved format to our LinkedList structure

// Example pseudocode:
/*
scope.launch {
    // Clear existing data
    clearRecording()

    // Load recording from database
    val recording = ecgDao.getRecordingById(recordingId)

    // Set the recording session ID
    recordingSessionId = recordingId

    // Load data for each lead
    for (leadData in recording.leadData) {
        val leadIndex = leadData.leadIndex
        val dataPoints = leadData.dataPoints

        // Add to our data structure
        allLeadsDataPoints[leadIndex]?.addAll(dataPoints)

        // Load abnormalities
        abnormalitiesByLead[leadIndex]?.addAll(leadData.abnormalities)
        abnormalRangesByLead[leadIndex]?.addAll(leadData.abnormalRanges)
    }

    // Update heart rate
    heartRate = recording.heartRate

    // Mark as having data and not recording
    hasRecordedData = true
    isRecording = false

    // Update the view
    withContext(Dispatchers.Main) {
        updateMaxScrollOffset()
        updateEcgPath()
        invalidate()
    }
}

}

/**
* Get list of available leads that have data
*/
fun getAvailableLeads(): List<Int> {
return allLeadsDataPoints.filter { (_, data) -> data.isNotEmpty() }.keys.toList()
}
}*/